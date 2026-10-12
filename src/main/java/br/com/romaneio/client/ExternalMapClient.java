package br.com.romaneio.client;

import br.com.romaneio.model.CoordenadaDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ExternalMapClient {
    private static final int MAX_DELIVERIES_PER_REQUEST = 25;
    private static final String GEOCODE_URL = "https://maps.googleapis.com/maps/api/geocode/json";
    private static final String ROUTES_URL = "https://routes.googleapis.com/directions/v2:computeRoutes";

    private volatile HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public ExternalMapClient(ObjectMapper objectMapper,
                             @Value("${romaneio.google.maps.api-key:}") String apiKey) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public boolean estaConfigurado() { return !apiKey.isBlank(); }

    public CoordenadaDTO geocodificarEndereco(String endereco) {
        exigirConfiguracao();
        if (endereco == null || endereco.isBlank()) return null;
        try {
            String url = GEOCODE_URL + "?address=" + URLEncoder.encode(endereco, StandardCharsets.UTF_8)
                    + "&components=country:BR&region=br&key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(enviar(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15)).GET().build()));
            String status = root.path("status").asText();
            if ("ZERO_RESULTS".equals(status)) return null;
            if (!"OK".equals(status)) throw new IllegalStateException("Google Geocoding retornou status " + status + ". Confira a chave, API habilitada e faturamento.");
            if (root.path("results").isEmpty()) return null;
            JsonNode result = root.path("results").get(0);
            String locationType = result.path("geometry").path("location_type").asText();
            if (result.path("partial_match").asBoolean(false) || !("ROOFTOP".equals(locationType) || "RANGE_INTERPOLATED".equals(locationType))) return null;
            JsonNode location = result.path("geometry").path("location");
            if (!location.hasNonNull("lat") || !location.hasNonNull("lng")) return null;
            CoordenadaDTO coordenada = new CoordenadaDTO(location.path("lat").asDouble(), location.path("lng").asDouble());
            return coordenada.isValid() ? coordenada : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Consulta de geocodificação interrompida.", e);
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("Não foi possível consultar a geocodificação no Google Maps.", e);
        }
    }

    /**
     * Otimiza trechos de até 25 entregas. Cada chamada parte do ponto anterior e termina
     * na última entrega do bloco, sem retorno à base. O provedor retorna distância e duração viárias.
     */
    public Percurso otimizarPercurso(CoordenadaDTO base, List<CoordenadaDTO> entregas) {
        exigirConfiguracao();
        if (base == null || !base.isValid() || entregas == null || entregas.isEmpty()) {
            throw new IllegalArgumentException("A base e os pontos da rota precisam ter coordenadas válidas.");
        }
        List<Integer> ordemGlobal = new ArrayList<>();
        List<Double> distanciasPorTrecho = new ArrayList<>();
        List<Double> duracoesPorTrecho = new ArrayList<>();
        CoordenadaDTO origem = base;
        for (int inicio = 0; inicio < entregas.size(); inicio += MAX_DELIVERIES_PER_REQUEST) {
            int fim = Math.min(entregas.size(), inicio + MAX_DELIVERIES_PER_REQUEST);
            List<Integer> ordemTrecho = otimizarTrecho(origem, entregas, inicio, fim);
            List<Double> distanciasTrecho = new ArrayList<>();
            List<Double> duracoesTrecho = new ArrayList<>();
            CoordenadaDTO destino = entregas.get(fim - 1);
            try {
                JsonNode route = consultarTrecho(origem, entregas, inicio, fim);
                JsonNode legs = route.path("legs");
                if (!legs.isArray() || legs.size() != ordemTrecho.size()) {
                    throw new IllegalStateException("O Google Maps retornou trechos incompletos para a rota.");
                }
                for (JsonNode leg : legs) {
                    double metros = leg.path("distanceMeters").asDouble(Double.NaN);
                    double segundos = parseSeconds(leg.path("duration").asText());
                    if (!Double.isFinite(metros) || !Double.isFinite(segundos)) {
                        throw new IllegalStateException("O Google Maps não retornou distância viária para todos os trechos.");
                    }
                    distanciasTrecho.add(metros);
                    duracoesTrecho.add(segundos);
                }
                JsonNode indexes = route.path("optimizedIntermediateWaypointIndex");
                if (indexes.isArray() && indexes.size() == fim - inicio - 1) {
                    ordemTrecho.clear();
                    for (JsonNode index : indexes) ordemTrecho.add(inicio + index.asInt());
                    ordemTrecho.add(fim - 1); // O destino final do bloco permanece fixo.
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Consulta de percurso interrompida.", e);
            } catch (Exception e) {
                if (e instanceof IllegalStateException state) throw state;
                throw new IllegalStateException("Não foi possível calcular o percurso viário no Google Maps.", e);
            }
            ordemGlobal.addAll(ordemTrecho);
            distanciasPorTrecho.addAll(distanciasTrecho);
            duracoesPorTrecho.addAll(duracoesTrecho);
            origem = destino;
        }
        return new Percurso(ordemGlobal, distanciasPorTrecho, duracoesPorTrecho);
    }

    private List<Integer> otimizarTrecho(CoordenadaDTO origem, List<CoordenadaDTO> entregas, int inicio, int fim) {
        List<Integer> ordem = new ArrayList<>();
        for (int i = inicio; i < fim; i++) ordem.add(i);
        return ordem;
    }

    private JsonNode consultarTrecho(CoordenadaDTO origem, List<CoordenadaDTO> entregas, int inicio, int fim) throws Exception {
        int destinoIndex = fim - 1;
        List<Object> intermediarios = new ArrayList<>();
        for (int i = inicio; i < destinoIndex; i++) intermediarios.add(waypoint(entregas.get(i)));
        Map<String, Object> bodyMap = new java.util.LinkedHashMap<>();
        bodyMap.put("origin", waypoint(origem));
        bodyMap.put("destination", waypoint(entregas.get(destinoIndex)));
        bodyMap.put("travelMode", "DRIVE");
        bodyMap.put("regionCode", "BR");
        bodyMap.put("languageCode", "pt-BR");
        bodyMap.put("units", "METRIC");
        if (!intermediarios.isEmpty()) {
            bodyMap.put("intermediates", intermediarios);
            bodyMap.put("optimizeWaypointOrder", true);
        }
        String body = objectMapper.writeValueAsString(bodyMap);
        HttpRequest request = HttpRequest.newBuilder(URI.create(ROUTES_URL))
                .timeout(Duration.ofSeconds(45)).header("Content-Type", "application/json")
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", "routes.distanceMeters,routes.duration,routes.legs.distanceMeters,routes.legs.duration,routes.optimizedIntermediateWaypointIndex")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        JsonNode root = objectMapper.readTree(enviar(request));
        JsonNode routes = root.path("routes");
        if (!routes.isArray() || routes.isEmpty()) throw new IllegalStateException("O Google Maps não encontrou um percurso viário para a rota.");
        return routes.get(0);
    }

    private Object waypoint(CoordenadaDTO c) {
        return Map.of("location", Map.of("latLng", Map.of("latitude", c.getLatitude(), "longitude", c.getLongitude())));
    }

    private HttpClient obterHttpClient() {
        HttpClient atual = httpClient;
        if (atual != null) return atual;
        synchronized (this) {
            if (httpClient == null) httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
            return httpClient;
        }
    }

    private String enviar(HttpRequest request) throws Exception {
        HttpResponse<String> response = obterHttpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Google Maps respondeu HTTP " + response.statusCode() + ". Verifique APIs habilitadas, chave e faturamento.");
        }
        return response.body();
    }

    private double parseSeconds(String value) {
        if (value == null || !value.endsWith("s")) return Double.NaN;
        try { return Double.parseDouble(value.substring(0, value.length() - 1)); }
        catch (NumberFormatException e) { return Double.NaN; }
    }

    private void exigirConfiguracao() {
        if (!estaConfigurado()) throw new IllegalStateException("Configure ROMANEIO_GOOGLE_MAPS_API_KEY para habilitar geocodificação e roteirização viária.");
    }

    public record Percurso(List<Integer> ordem, List<Double> distanciasPorTrecho, List<Double> duracoesPorTrecho) {}
}

