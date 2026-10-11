package br.com.romaneio.client;

import br.com.romaneio.model.CoordenadaDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class ExternalMapClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ExternalMapClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public CoordenadaDTO geocodificarEndereco(String endereco) {
        if (endereco == null || endereco.isBlank()) {
            return null;
        }

        try {
            String query = URLEncoder.encode(endereco, StandardCharsets.UTF_8);
            String url = "https://nominatim.openstreetmap.org/search?q=" + query + "&format=json&limit=1&addressdetails=1";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "RomaneioOptimizer/1.0 (logistica@romaneio.com.br)")
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null && !response.body().isBlank()) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.isArray() && root.size() > 0) {
                    JsonNode primeiro = root.get(0);
                    double lat = Double.parseDouble(primeiro.get("lat").asText());
                    double lon = Double.parseDouble(primeiro.get("lon").asText());
                    return new CoordenadaDTO(lat, lon);
                }
            }
        } catch (Exception ignored) {
            // Em caso de falha de conexão, timeout ou rate limit, fallback tratado pelo GeocodificacaoService
        }

        return null;
    }
}
