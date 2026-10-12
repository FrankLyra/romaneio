package br.com.romaneio.service;

import br.com.romaneio.client.ExternalMapClient;
import br.com.romaneio.model.CoordenadaDTO;
import br.com.romaneio.model.EncomendaDTO;
import br.com.romaneio.model.ParadaDTO;
import br.com.romaneio.model.RotaResponseDTO;
import br.com.romaneio.exception.RomaneioException;
import br.com.romaneio.util.BaseFixaConstants;
import br.com.romaneio.util.GeoUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RoteirizacaoService {
    private static final int MAX_PONTOS_POR_ROTA = 200;
    private final ExternalMapClient mapClient;
    private final GeocodificacaoService geocodificacaoService;

    /** Mantido para compatibilidade com instanciações manuais antigas. */
    public RoteirizacaoService() { this.mapClient = null; this.geocodificacaoService = null; }

    @Autowired
    public RoteirizacaoService(ExternalMapClient mapClient, GeocodificacaoService geocodificacaoService) {
        this.mapClient = mapClient;
        this.geocodificacaoService = geocodificacaoService;
    }

    public List<RotaResponseDTO> calcularMelhoresRotas(List<EncomendaDTO> encomendas) {
        return calcularComAvisos(encomendas).rotas();
    }

    /** Resumo sem geocodificação para classificar rotas pelo número de pontos únicos. */
    public RotaResponseDTO resumirRota(String identificador, List<EncomendaDTO> encomendas) {
        List<ParadaAgrupada> paradas = consolidarParadas(encomendas);
        RotaResponseDTO rota = new RotaResponseDTO();
        rota.setIdentificador(identificador);
        rota.setLetra(identificador.contains("-") ? identificador.substring(0, identificador.indexOf('-')) : identificador);
        rota.setNome("Rota " + identificador);
        rota.setDescricao("Classificada pela quantidade de pontos de entrega únicos.");
        rota.setQuantidadeParadas(paradas.size());
        rota.setQuantidadeEncomendas(encomendas.size());
        for (int i = 0; i < paradas.size(); i++) {
            List<EncomendaDTO> objetos = paradas.get(i).encomendas;
            EncomendaDTO ref = objetos.get(0);
            ParadaDTO parada = new ParadaDTO();
            parada.setOrdem(i + 1);
            parada.setTipo("ENTREGA");
            parada.setEndereco(ref.getEnderecoCompleto());
            parada.setLogradouro(ref.getLogradouro());
            parada.setNumero(ref.getNumero());
            parada.setComplemento(ref.getComplemento());
            parada.setBairro(ref.getBairro());
            parada.setCidade(ref.getCidade());
            parada.setUf(ref.getUf());
            parada.setCep(ref.getCep());
            parada.setEncomendas(objetos);
            rota.getParadas().add(parada);
        }
        return rota;
    }

    /** Geocodifica e calcula somente a rota explicitamente escolhida pelo usuário. */
    public RotaResponseDTO calcularRotaSelecionada(String identificador, List<EncomendaDTO> encomendas) {
        if (mapClient == null || !mapClient.estaConfigurado()) {
            throw new RomaneioException(getAvisoIndisponibilidade());
        }
        if (encomendas == null || encomendas.isEmpty()) {
            throw new RomaneioException("A rota selecionada não contém encomendas válidas.");
        }

        geocodificacaoService.geocodificarEncomendas(encomendas);
        CoordenadaDTO base = geocodificacaoService.geocodificarEndereco(BaseFixaConstants.ENDERECO_COMPLETO);
        if (base == null || !base.isValid()) {
            throw new RomaneioException("Não foi possível localizar a base fixa com precisão suficiente.");
        }

        List<ParadaAgrupada> paradas = consolidarParadas(encomendas);
        if (paradas.size() > MAX_PONTOS_POR_ROTA) {
            throw new RomaneioException("A rota excede o limite de " + MAX_PONTOS_POR_ROTA + " pontos por cálculo.");
        }
        if (paradas.stream().anyMatch(p -> p.latitude == null || p.longitude == null)) {
            throw new RomaneioException("Há endereços sem coordenadas precisas nesta rota. Revise os endereços antes de calcular.");
        }
        List<CoordenadaDTO> pontos = paradas.stream()
                .map(p -> new CoordenadaDTO(p.latitude, p.longitude)).toList();
        try {
            ExternalMapClient.Percurso percurso = mapClient.otimizarPercurso(base, pontos);
            return montarResposta(identificador, base, paradas, percurso);
        } catch (RuntimeException e) {
            throw new RomaneioException("Não foi possível calcular a rota selecionada. " + mensagemCurta(e), e);
        }
    }

    public ResultadoRoteirizacao calcularComAvisos(List<EncomendaDTO> encomendas) {
        List<String> avisos = new ArrayList<>();
        if (mapClient == null || !mapClient.estaConfigurado()) {
            avisos.add(getAvisoIndisponibilidade());
            return new ResultadoRoteirizacao(List.of(), avisos);
        }
        if (encomendas == null || encomendas.isEmpty()) return new ResultadoRoteirizacao(List.of(), List.of("Nenhuma encomenda válida para roteirizar."));

        CoordenadaDTO base;
        try {
            base = geocodificacaoService.geocodificarEndereco(BaseFixaConstants.ENDERECO_COMPLETO);
        } catch (RuntimeException e) {
            avisos.add("Não foi possível localizar a base fixa; nenhuma rota foi classificada.");
            return new ResultadoRoteirizacao(List.of(), avisos);
        }
        if (base == null || !base.isValid()) {
            avisos.add("A base fixa não foi localizada com precisão suficiente pelo Google Maps; confira o endereço cadastrado.");
            return new ResultadoRoteirizacao(List.of(), avisos);
        }

        Map<String, List<EncomendaDTO>> porIdentificador = encomendas.stream().collect(Collectors.groupingBy(
                e -> normalizarIdentificador(e.getCorredor()), TreeMap::new, Collectors.toList()));
        List<RotaResponseDTO> calculadas = new ArrayList<>();
        for (Map.Entry<String, List<EncomendaDTO>> entry : porIdentificador.entrySet()) {
            String id = entry.getKey();
            if (id.equals("SEM-IDENTIFICADOR")) {
                avisos.add("Há encomendas sem identificador de rota; elas não foram usadas na classificação.");
                continue;
            }
            try {
                List<ParadaAgrupada> paradas = consolidarParadas(entry.getValue());
                if (paradas.isEmpty()) {
                    avisos.add("Rota " + id + ": não há pontos de entrega válidos.");
                    continue;
                }
                if (paradas.size() > MAX_PONTOS_POR_ROTA) {
                    avisos.add("Rota " + id + ": possui " + paradas.size() + " pontos, acima do limite operacional de " + MAX_PONTOS_POR_ROTA + "; não foi classificada.");
                    continue;
                }
                if (paradas.stream().anyMatch(p -> p.latitude == null || p.longitude == null)) {
                    avisos.add("Rota " + id + ": há endereços sem geocodificação precisa; valide-os antes do cálculo.");
                    continue;
                }
                List<CoordenadaDTO> pontos = paradas.stream()
                        .map(p -> new CoordenadaDTO(p.latitude, p.longitude)).toList();
                ExternalMapClient.Percurso percurso = mapClient.otimizarPercurso(base, pontos);
                calculadas.add(montarResposta(id, base, paradas, percurso));
            } catch (RuntimeException e) {
                avisos.add("Rota " + id + ": percurso indisponível. " + mensagemCurta(e));
            }
        }
        calculadas.sort(Comparator.comparing(RotaResponseDTO::getDistanciaTotalKm));
        List<RotaResponseDTO> melhores = calculadas.stream().limit(10).toList();
        for (int i = 0; i < melhores.size(); i++) { melhores.get(i).setId(i + 1); melhores.get(i).setPrincipal(i == 0); }
        if (melhores.isEmpty() && avisos.isEmpty()) avisos.add("Nenhuma rota pôde ser calculada com os dados disponíveis.");
        return new ResultadoRoteirizacao(melhores, avisos);
    }

    public String getAvisoIndisponibilidade() {
        return "Para calcular a distância e o tempo da rota selecionada, configure ROMANEIO_GOOGLE_MAPS_API_KEY e habilite Geocoding API e Routes API.";
    }

    private List<ParadaAgrupada> consolidarParadas(List<EncomendaDTO> encomendas) {
        List<ParadaAgrupada> paradas = new ArrayList<>();
        for (EncomendaDTO encomenda : encomendas) {
            ParadaAgrupada existente = paradas.stream().filter(p -> mesmoEndereco(p.encomendas.get(0), encomenda)).findFirst().orElse(null);
            if (existente == null) paradas.add(new ParadaAgrupada(encomenda));
            else existente.encomendas.add(encomenda);
        }
        return paradas;
    }

    private boolean mesmoEndereco(EncomendaDTO a, EncomendaDTO b) {
        String numeroA = normalizar(a.getNumero()), numeroB = normalizar(b.getNumero());
        if (numeroA.isBlank() || !numeroA.equals(numeroB)) return false;
        String cepA = digitos(a.getCep()), cepB = digitos(b.getCep());
        if (cepA.isBlank() || !cepA.equals(cepB)) return false;
        return nomesDeRuaParecidos(chaveRua(a.getLogradouro()), chaveRua(b.getLogradouro()));
    }

    private String chaveRua(String valor) {
        String rua = removerTipoVia(normalizar(valor));
        return rua.replaceAll("\\bjose\\s+g\\s+", "jose goncalves ");
    }

    private boolean nomesDeRuaParecidos(String a, String b) {
        if (a.isBlank() || b.isBlank()) return false;
        String[] ta = a.split(" "), tb = b.split(" ");
        if (Math.abs(ta.length - tb.length) > 1) return false;
        int i = 0, j = 0, divergencias = 0;
        while (i < ta.length && j < tb.length) {
            if (ta[i].equals(tb[j]) || ta[i].startsWith(tb[j]) || tb[j].startsWith(ta[i])) { i++; j++; continue; }
            if (++divergencias > 1) return false;
            if (ta.length > tb.length) i++; else if (tb.length > ta.length) j++; else { i++; j++; }
        }
        divergencias += (ta.length - i) + (tb.length - j);
        return divergencias <= 1;
    }
    private RotaResponseDTO montarResposta(String id, CoordenadaDTO base, List<ParadaAgrupada> paradas,
                                            ExternalMapClient.Percurso percurso) {
        RotaResponseDTO rota = new RotaResponseDTO();
        rota.setId(1);
        rota.setIdentificador(id);
        rota.setLetra(id.split("-")[0]);
        rota.setNome("Rota " + id);
        rota.setDescricao("Percurso aberto por vias, com distância estimada e sem retorno à base.");
        ParadaDTO origem = new ParadaDTO(0, "ORIGEM", BaseFixaConstants.ENDERECO_COMPLETO, base.getLatitude(), base.getLongitude());
        origem.setDistanciaDoPontoAnteriorKm(0.0);
        origem.setDistanciaAcumuladaKm(0.0);
        origem.setLogradouro(BaseFixaConstants.LOGRADOURO);
        origem.setNumero(BaseFixaConstants.NUMERO);
        origem.setBairro(BaseFixaConstants.BAIRRO);
        origem.setCidade(BaseFixaConstants.CIDADE);
        origem.setUf(BaseFixaConstants.UF);
        origem.setCep(BaseFixaConstants.CEP);
        rota.getParadas().add(origem);

        double totalMetros = 0;
        double totalSegundos = 0;
        List<Integer> ordem = percurso.ordem();
        for (int pos = 0; pos < ordem.size(); pos++) {
            int indice = ordem.get(pos);
            ParadaAgrupada agrupada = paradas.get(indice);
            double metros = percurso.distanciasPorTrecho().get(pos);
            double segundos = percurso.duracoesPorTrecho().get(pos);
            totalMetros += metros;
            totalSegundos += segundos;
            EncomendaDTO ref = agrupada.encomendas.get(0);
            ParadaDTO parada = new ParadaDTO(pos + 1, "ENTREGA", ref.getEnderecoCompleto(), agrupada.latitude, agrupada.longitude);
            parada.setLogradouro(ref.getLogradouro()); parada.setNumero(ref.getNumero()); parada.setComplemento(ref.getComplemento()); parada.setBairro(ref.getBairro());
            parada.setCidade(ref.getCidade()); parada.setUf(ref.getUf()); parada.setCep(ref.getCep());
            parada.setDistanciaDoPontoAnteriorKm(metros / 1000.0);
            parada.setDistanciaAcumuladaKm(totalMetros / 1000.0);
            parada.setEncomendas(agrupada.encomendas);
            rota.getParadas().add(parada);
        }
        double km = totalMetros / 1000.0;
        int minutos = (int) Math.round(totalSegundos / 60.0 + ordem.size() * 3.0);
        rota.setDistanciaTotalKm(km); rota.setDistanciaTotalFormatada(GeoUtils.formatarDistancia(km));
        rota.setTempoEstimadoMinutos(minutos); rota.setTempoEstimadoFormatado(GeoUtils.formatarTempo(minutos));
        rota.setQuantidadeParadas(ordem.size() + 1);
        rota.setQuantidadeEncomendas(paradas.stream().mapToInt(p -> p.encomendas.size()).sum());
        return rota;
    }

    private String normalizarIdentificador(String valor) {
        if (valor == null || valor.isBlank()) return "SEM-IDENTIFICADOR";
        String id = valor.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if (id.startsWith("ROTA")) id = id.substring(4);
        return id.isBlank() ? "SEM-IDENTIFICADOR" : id;
    }

    private String removerTipoVia(String rua) { return rua.replaceFirst("^(rua|r|avenida|av|travessa|tv|estrada|estr|rodovia|rod)\\s+", ""); }
    private String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
    }
    private String digitos(String valor) { return valor == null ? "" : valor.replaceAll("[^0-9]", ""); }
    private String mensagemCurta(RuntimeException e) {
        String mensagem = e.getMessage();
        return mensagem == null || mensagem.isBlank() ? "falha ao validar endereços ou consultar o provedor." : mensagem;
    }

    private static final class ParadaAgrupada {
        private final List<EncomendaDTO> encomendas = new ArrayList<>();
        private final Double latitude;
        private final Double longitude;
        private ParadaAgrupada(EncomendaDTO encomenda) {
            encomendas.add(encomenda); latitude = encomenda.getLatitude(); longitude = encomenda.getLongitude();
        }
    }

    public record ResultadoRoteirizacao(List<RotaResponseDTO> rotas, List<String> avisos) {}
}
