package br.com.romaneio.service;

import br.com.romaneio.model.EncomendaDTO;
import br.com.romaneio.model.RotaResponseDTO;
import br.com.romaneio.util.BaseFixaConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoteirizacaoServiceTest {

    private RoteirizacaoService roteirizacaoService;

    @BeforeEach
    void setUp() {
        roteirizacaoService = new RoteirizacaoService();
    }

    @Test
    void deveGerarCincoMelhoresRotasOrdenadasPorMenorDistancia() {
        List<EncomendaDTO> lista = new ArrayList<>();
        // 4 pontos em Maricá/Itaipuaçu com coordenadas distintas
        for (int i = 1; i <= 6; i++) {
            EncomendaDTO enc = new EncomendaDTO("E" + i, "24933-" + (100 + i), "Rua " + i, String.valueOf(i * 10), "", "Itaipuaçu", "Maricá", "RJ");
            enc.setLatitude(-22.9500 - (i * 0.005));
            enc.setLongitude(-42.9800 - (i * 0.004));
            lista.add(enc);
        }

        List<RotaResponseDTO> rotas = roteirizacaoService.calcularMelhoresRotas(lista);

        assertNotNull(rotas);
        assertEquals(5, rotas.size());

        // A primeira rota deve ser a principal recomendada
        assertTrue(rotas.get(0).isPrincipal());
        assertEquals(1, rotas.get(0).getId());
        assertEquals("A", rotas.get(0).getLetra());
        assertTrue(rotas.get(0).getNome().contains("Rota A"));

        // As rotas devem ser identificadas pelas letras A, B, C, D, E
        String[] letrasEsperadas = {"A", "B", "C", "D", "E"};
        for (int i = 0; i < rotas.size(); i++) {
            assertEquals(letrasEsperadas[i], rotas.get(i).getLetra());
            assertTrue(rotas.get(i).getNome().contains("Rota " + letrasEsperadas[i]));
        }

        // As rotas devem estar ordenadas de forma ascendente pela distância total
        for (int i = 0; i < rotas.size() - 1; i++) {
            assertTrue(rotas.get(i).getDistanciaTotalKm() <= rotas.get(i + 1).getDistanciaTotalKm());
        }

        // Todas as rotas devem partir da base fixa de Itaipuaçu
        for (RotaResponseDTO rota : rotas) {
            assertEquals("ORIGEM", rota.getParadas().get(0).getTipo());
            assertEquals(BaseFixaConstants.LATITUDE, rota.getParadas().get(0).getLatitude(), 0.0001);
            assertEquals(BaseFixaConstants.LONGITUDE, rota.getParadas().get(0).getLongitude(), 0.0001);
            assertEquals(0.0, rota.getParadas().get(0).getDistanciaAcumuladaKm());
        }
    }

    @Test
    void deveAgruparEncomendasNoMesmoEndereco() {
        List<EncomendaDTO> lista = new ArrayList<>();
        EncomendaDTO enc1 = new EncomendaDTO("E1", "24933-175", "Rua 115", "23", "Casa 1", "Itaipuaçu", "Maricá", "RJ");
        enc1.setLatitude(-22.9510);
        enc1.setLongitude(-42.9810);

        EncomendaDTO enc2 = new EncomendaDTO("E2", "24933-175", "Rua 115", "23", "Casa 2", "Itaipuaçu", "Maricá", "RJ");
        enc2.setLatitude(-22.9510);
        enc2.setLongitude(-42.9810);

        lista.add(enc1);
        lista.add(enc2);

        List<RotaResponseDTO> rotas = roteirizacaoService.calcularMelhoresRotas(lista);
        RotaResponseDTO rota1 = rotas.get(0);

        // Deve ter 2 paradas no total: Origem + 1 Parada de Entrega agrupada
        assertEquals(2, rota1.getParadas().size());
        assertEquals(2, rota1.getQuantidadeEncomendas());
        assertEquals(2, rota1.getParadas().get(1).getQuantidadeEncomendas());
    }

    @Test
    void deveAgruparEncomendasNoMesmoEnderecoMesmoComFormatacaoCepDiferente() {
        List<EncomendaDTO> lista = new ArrayList<>();
        EncomendaDTO enc1 = new EncomendaDTO("E1", "24933-175", "Rua 115", "23", "Casa 1", "Itaipuaçu", "Maricá", "RJ");
        enc1.setLatitude(-22.9510);
        enc1.setLongitude(-42.9810);

        EncomendaDTO enc2 = new EncomendaDTO("E2", "24933175", "Rua 115", "23", "Casa 2", "Itaipuaçu", "Maricá", "RJ");
        enc2.setLatitude(-22.9510);
        enc2.setLongitude(-42.9810);

        lista.add(enc1);
        lista.add(enc2);

        List<RotaResponseDTO> rotas = roteirizacaoService.calcularMelhoresRotas(lista);
        RotaResponseDTO rota1 = rotas.get(0);

        // Deve ter 2 paradas no total: Origem + 1 Parada de Entrega agrupada
        assertEquals(2, rota1.getParadas().size());
        assertEquals(2, rota1.getQuantidadeEncomendas());
        assertEquals(2, rota1.getParadas().get(1).getQuantidadeEncomendas());
    }

    @Test
    void naoDeveContabilizarRetornoABaseNoCalculoFinal() {
        List<EncomendaDTO> lista = new ArrayList<>();
        EncomendaDTO enc1 = new EncomendaDTO("E1", "24933-175", "Rua Ponto 1", "10", "", "Itaipuaçu", "Maricá", "RJ");
        enc1.setLatitude(-22.9600);
        enc1.setLongitude(-42.9900);
        lista.add(enc1);

        List<RotaResponseDTO> rotas = roteirizacaoService.calcularMelhoresRotas(lista);
        RotaResponseDTO rota = rotas.get(0);

        double distOrigemAtePonto1 = rota.getParadas().get(1).getDistanciaDoPontoAnteriorKm();
        // A distância total deve ser apenas Origem -> Ponto 1, sem somar a volta
        assertEquals(distOrigemAtePonto1, rota.getDistanciaTotalKm(), 0.001);
    }
}
