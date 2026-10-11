package br.com.romaneio;

import br.com.romaneio.model.EncomendaDTO;
import br.com.romaneio.model.RotaResponseDTO;
import br.com.romaneio.service.GeocodificacaoService;
import br.com.romaneio.service.PlanilhaParserService;
import br.com.romaneio.service.RoteirizacaoService;
import br.com.romaneio.service.ValidacaoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ExcelInspectorTest {

    @Autowired
    private PlanilhaParserService planilhaParserService;

    @Autowired
    private ValidacaoService validacaoService;

    @Autowired
    private GeocodificacaoService geocodificacaoService;

    @Autowired
    private RoteirizacaoService roteirizacaoService;

    @Test
    void testProcessamentoArquivoRealExcel() throws Exception {
        File file = new File("data/ROMANEIO MARICA 11_10_2026 AM .xlsx");
        if (!file.exists()) return;

        try (FileInputStream fis = new FileInputStream(file)) {
            List<EncomendaDTO> listaBruta = planilhaParserService.parseExcel(fis);
            assertFalse(listaBruta.isEmpty());
            System.out.println("Linhas lidas do Excel real: " + listaBruta.size());

            // Testa com as primeiras 50 encomendas da planilha para teste de desempenho e roteamento
            List<EncomendaDTO> subLista = listaBruta.subList(0, Math.min(50, listaBruta.size()));

            ValidacaoService.ResultadoValidacao validacao = validacaoService.validarEncomendas(subLista);
            List<EncomendaDTO> validas = validacao.getEncomendasValidas();
            assertFalse(validas.isEmpty());

            geocodificacaoService.geocodificarEncomendas(validas);

            List<RotaResponseDTO> rotas = roteirizacaoService.calcularMelhoresRotas(validas);
            assertEquals(5, rotas.size());
            assertTrue(rotas.get(0).isPrincipal());
            assertTrue(rotas.get(0).getDistanciaTotalKm() > 0);

            System.out.println("Distâncias das 5 melhores rotas geradas:");
            for (RotaResponseDTO rota : rotas) {
                System.out.printf(" - %s: %s (Tempo: %s, Paradas: %d, Encomendas: %d)%n",
                        rota.getNome(), rota.getDistanciaTotalFormatada(),
                        rota.getTempoEstimadoFormatado(), rota.getQuantidadeParadas(),
                        rota.getQuantidadeEncomendas());
            }
        }
    }
}
