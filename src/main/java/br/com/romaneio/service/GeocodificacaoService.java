package br.com.romaneio.service;

import br.com.romaneio.client.ExternalMapClient;
import br.com.romaneio.model.CoordenadaDTO;
import br.com.romaneio.model.EncomendaDTO;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class GeocodificacaoService {
    private final ExternalMapClient mapClient;
    private final Map<String, CoordenadaDTO> cache = new HashMap<>();

    public GeocodificacaoService(ExternalMapClient mapClient) { this.mapClient = mapClient; }

    public synchronized void geocodificarEncomendas(Iterable<EncomendaDTO> encomendas) {
        for (EncomendaDTO encomenda : encomendas) geocodificar(encomenda);
    }

    public synchronized void geocodificar(EncomendaDTO encomenda) {
        if (encomenda == null) return;
        CoordenadaDTO atual = new CoordenadaDTO(encomenda.getLatitude(), encomenda.getLongitude());
        if (atual.isValid()) return;
        encomenda.setLatitude(null);
        encomenda.setLongitude(null);
        if (!mapClient.estaConfigurado()) return;
        CoordenadaDTO coordenada;
        try {
            coordenada = consultarComCache(encomenda.getEnderecoCompleto());
        } catch (RuntimeException indisponivel) {
            return; // a rota será sinalizada como inelegível pelo serviço de roteirização
        }
        if (coordenada != null && coordenada.isValid()) {
            encomenda.setLatitude(coordenada.getLatitude());
            encomenda.setLongitude(coordenada.getLongitude());
        }
    }

    public synchronized CoordenadaDTO geocodificarEndereco(String endereco) {
        if (!mapClient.estaConfigurado()) return null;
        return consultarComCache(endereco);
    }

    public boolean estaConfigurado() { return mapClient.estaConfigurado(); }

    private CoordenadaDTO consultarComCache(String endereco) {
        if (endereco == null || endereco.isBlank()) return null;
        String chave = Normalizer.normalize(endereco, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return cache.computeIfAbsent(chave, ignored -> mapClient.geocodificarEndereco(endereco));
    }
}
