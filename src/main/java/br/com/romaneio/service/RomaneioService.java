package br.com.romaneio.service;

import br.com.romaneio.model.AnaliseRomaneioResponseDTO;
import br.com.romaneio.model.EncomendaDTO;
import br.com.romaneio.model.ImportacaoResponseDTO;
import br.com.romaneio.model.RotaResponseDTO;
import br.com.romaneio.exception.RomaneioException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class RomaneioService {
    private final PlanilhaParserService planilhaParserService;
    private final ValidacaoService validacaoService;
    private final RoteirizacaoService roteirizacaoService;
    private final AgrupamentoService agrupamentoService;
    private final LinkedHashMap<String, Map<String, List<EncomendaDTO>>> importacoesRecentes = new LinkedHashMap<>();

    public RomaneioService(PlanilhaParserService planilhaParserService, ValidacaoService validacaoService,
                           RoteirizacaoService roteirizacaoService, AgrupamentoService agrupamentoService) {
        this.planilhaParserService = planilhaParserService;
        this.validacaoService = validacaoService;
        this.roteirizacaoService = roteirizacaoService;
        this.agrupamentoService = agrupamentoService;
    }

    public ImportacaoResponseDTO processarPlanilha(MultipartFile file) {
        return prepararPlanilha(file);
    }

    /** Lê e classifica por quantidade de pontos; não chama geocodificação nem Routes API. */
    public ImportacaoResponseDTO prepararPlanilha(MultipartFile file) {
        List<EncomendaDTO> brutas = planilhaParserService.parse(file);
        ValidacaoService.ResultadoValidacao validacao = validacaoService.validarEncomendas(brutas);
        List<EncomendaDTO> validas = validacao.getEncomendasValidas();
        Map<String, List<EncomendaDTO>> porRota = validas.stream().collect(Collectors.groupingBy(
                e -> agrupamentoService.extrairLetraRota(e.getCorredor()), TreeMap::new, Collectors.toList()));

        List<String> avisos = new ArrayList<>(validacao.getAvisos());
        List<RotaResponseDTO> rotas = new ArrayList<>();
        Map<String, List<EncomendaDTO>> rotasImportadas = new TreeMap<>();
        int totalPontos = 0;
        for (Map.Entry<String, List<EncomendaDTO>> entry : porRota.entrySet()) {
            if ("SEM-IDENTIFICADOR".equals(entry.getKey())) {
                avisos.add("Há encomendas sem identificador no campo Corridor Cage; não foram classificadas.");
                continue;
            }
            RotaResponseDTO resumo = roteirizacaoService.resumirRota(entry.getKey(), entry.getValue());
            rotas.add(resumo);
            rotasImportadas.put(entry.getKey(), entry.getValue());
            totalPontos += resumo.getQuantidadeParadas();
        }

        rotas.sort(java.util.Comparator.comparingInt(RotaResponseDTO::getQuantidadeParadas)
                .thenComparing(RotaResponseDTO::getIdentificador, String.CASE_INSENSITIVE_ORDER));
        List<RotaResponseDTO> melhores = rotas.stream().limit(5).toList();
        for (int i = 0; i < melhores.size(); i++) {
            melhores.get(i).setId(i + 1);
            melhores.get(i).setPrincipal(i == 0);
        }

        String idImportacao = UUID.randomUUID().toString();
        guardarImportacao(idImportacao, rotasImportadas);
        ImportacaoResponseDTO response = new ImportacaoResponseDTO(true,
                "Planilha lida. As cinco rotas com menos pontos de entrega foram classificadas.");
        response.setIdImportacao(idImportacao);
        response.setTotalLinhasArquivo(brutas.size());
        response.setTotalEncomendasValidas(validas.size());
        response.setTotalParadasUnicas(totalPontos);
        response.setAvisos(avisos);
        response.setRotas(melhores);
        return response;
    }

    public synchronized RotaResponseDTO calcularRotaSelecionada(String idImportacao, String identificador) {
        Map<String, List<EncomendaDTO>> rotas = importacoesRecentes.get(idImportacao);
        if (rotas == null) {
            throw new RomaneioException("Esta importação expirou. Importe novamente a planilha para calcular a rota.");
        }
        List<EncomendaDTO> encomendas = rotas.get(agrupamentoService.extrairLetraRota(identificador));
        if (encomendas == null) {
            throw new RomaneioException("A rota selecionada não pertence a esta importação.");
        }
        return roteirizacaoService.calcularRotaSelecionada(identificador, encomendas);
    }

    private synchronized void guardarImportacao(String id, Map<String, List<EncomendaDTO>> rotas) {
        importacoesRecentes.put(id, rotas);
        while (importacoesRecentes.size() > 5) {
            String maisAntiga = importacoesRecentes.keySet().iterator().next();
            importacoesRecentes.remove(maisAntiga);
        }
    }

    /** Agrupamento cadastral não necessita de geocodificação nem consultas externas. */
    public AnaliseRomaneioResponseDTO analisarPlanilhaAgrupamentos(MultipartFile file) {
        List<EncomendaDTO> brutas = planilhaParserService.parse(file);
        ValidacaoService.ResultadoValidacao validacao = validacaoService.validarEncomendas(brutas);
        AnaliseRomaneioResponseDTO resultado = agrupamentoService.analisarAgrupamentos(validacao.getEncomendasValidas());
        resultado.setTotalLinhasLidas(brutas.size());
        resultado.setAvisos(validacao.getAvisos());
        return resultado;
    }

}
