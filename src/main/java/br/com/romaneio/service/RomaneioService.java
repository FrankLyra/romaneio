package br.com.romaneio.service;

import br.com.romaneio.model.AnaliseRomaneioResponseDTO;
import br.com.romaneio.model.EncomendaDTO;
import br.com.romaneio.model.ImportacaoResponseDTO;
import br.com.romaneio.model.RotaResponseDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class RomaneioService {

    private final PlanilhaParserService planilhaParserService;
    private final ValidacaoService validacaoService;
    private final GeocodificacaoService geocodificacaoService;
    private final RoteirizacaoService roteirizacaoService;
    private final AgrupamentoService agrupamentoService;

    public RomaneioService(PlanilhaParserService planilhaParserService,
                           ValidacaoService validacaoService,
                           GeocodificacaoService geocodificacaoService,
                           RoteirizacaoService roteirizacaoService,
                           AgrupamentoService agrupamentoService) {
        this.planilhaParserService = planilhaParserService;
        this.validacaoService = validacaoService;
        this.geocodificacaoService = geocodificacaoService;
        this.roteirizacaoService = roteirizacaoService;
        this.agrupamentoService = agrupamentoService;
    }

    public ImportacaoResponseDTO processarPlanilha(MultipartFile file) {
        // 1. Extração / Leitura e Parser da Planilha
        List<EncomendaDTO> encomendasBrutas = planilhaParserService.parse(file);
        int totalLinhas = encomendasBrutas.size();

        // 2. Validação de dados e identificação de duplicidades
        ValidacaoService.ResultadoValidacao resultadoValidacao = validacaoService.validarEncomendas(encomendasBrutas);
        List<EncomendaDTO> encomendasValidas = resultadoValidacao.getEncomendasValidas();

        // 3. Geocodificação das encomendas
        geocodificacaoService.geocodificarEncomendas(encomendasValidas);

        // 4. Otimização de Rota (5 melhores rotas sem retorno à base de Itaipuaçu)
        List<RotaResponseDTO> rotas = roteirizacaoService.calcularMelhoresRotas(encomendasValidas);

        // 5. Montagem da resposta
        ImportacaoResponseDTO response = new ImportacaoResponseDTO(true, "Planilha processada com sucesso.");
        response.setTotalLinhasArquivo(totalLinhas);
        response.setTotalEncomendasValidas(encomendasValidas.size());
        if (!rotas.isEmpty()) {
            response.setTotalParadasUnicas(rotas.get(0).getQuantidadeParadas() - 1); // Descontando a base de origem
        }
        response.setAvisos(resultadoValidacao.getAvisos());
        response.setRotas(rotas);

        return response;
    }

    /**
     * Processa a planilha e retorna a análise estruturada de agrupamento
     * separando por rotas (letras), consolidando ruas e contando paradas/endereços únicos.
     */
    public AnaliseRomaneioResponseDTO analisarPlanilhaAgrupamentos(MultipartFile file) {
        List<EncomendaDTO> encomendasBrutas = planilhaParserService.parse(file);
        ValidacaoService.ResultadoValidacao validacao = validacaoService.validarEncomendas(encomendasBrutas);
        List<EncomendaDTO> validas = validacao.getEncomendasValidas();

        // Realiza a geocodificação para preencher coordenadas das paradas
        geocodificacaoService.geocodificarEncomendas(validas);

        AnaliseRomaneioResponseDTO resultado = agrupamentoService.analisarAgrupamentos(validas);
        resultado.setTotalLinhasLidas(encomendasBrutas.size());
        resultado.setAvisos(validacao.getAvisos());
        return resultado;
    }

    public List<RotaResponseDTO> calcularRotasDiretas(List<EncomendaDTO> encomendas) {
        ValidacaoService.ResultadoValidacao resultadoValidacao = validacaoService.validarEncomendas(encomendas);
        List<EncomendaDTO> validas = resultadoValidacao.getEncomendasValidas();
        geocodificacaoService.geocodificarEncomendas(validas);
        return roteirizacaoService.calcularMelhoresRotas(validas);
    }
}
