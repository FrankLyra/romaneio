package br.com.romaneio.service;

import br.com.romaneio.model.AnaliseRomaneioResponseDTO;
import br.com.romaneio.model.EncomendaDTO;
import br.com.romaneio.model.ImportacaoResponseDTO;
import br.com.romaneio.model.RotaResponseDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class RomaneioService {
    private final PlanilhaParserService planilhaParserService;
    private final ValidacaoService validacaoService;
    private final RoteirizacaoService roteirizacaoService;
    private final AgrupamentoService agrupamentoService;

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
        int totalPontos = 0;
        for (Map.Entry<String, List<EncomendaDTO>> entry : porRota.entrySet()) {
            if ("SEM-IDENTIFICADOR".equals(entry.getKey())) {
                avisos.add("Há encomendas sem identificador no campo Corridor Cage; não foram classificadas.");
                continue;
            }
            RotaResponseDTO resumo = roteirizacaoService.resumirRota(entry.getKey(), entry.getValue());
            totalPontos += resumo.getQuantidadeParadas();
            resumo.setBairros(listarBairrosDaRota(entry.getValue()));
            int pontosPrioritarios = contarPontosBairrosPrioritarios(entry.getValue());
            if (pontosPrioritarios == 0) continue;
            resumo.setPontosBairrosPrioritarios(pontosPrioritarios);
            rotas.add(resumo);
        }

        rotas.sort(java.util.Comparator.comparingInt(RotaResponseDTO::getQuantidadeParadas)
                .thenComparing(java.util.Comparator.comparingInt(RotaResponseDTO::getPontosBairrosPrioritarios).reversed())
                .thenComparing(RotaResponseDTO::getIdentificador, String.CASE_INSENSITIVE_ORDER));
        List<RotaResponseDTO> melhores = rotas.stream().limit(10).toList();
        for (int i = 0; i < melhores.size(); i++) {
            melhores.get(i).setId(i + 1);
            melhores.get(i).setPrincipal(i == 0);
        }
        if (melhores.isEmpty()) avisos.add("Nenhuma rota possui pontos de entrega nos bairros prioritários.");

        ImportacaoResponseDTO response = new ImportacaoResponseDTO(true,
                "Planilha lida. As dez rotas elegíveis foram ordenadas por pontos de entrega, do menor para o maior; empates favorecem mais pontos nas áreas prioritárias.");
        response.setTotalLinhasArquivo(brutas.size());
        response.setTotalEncomendasValidas(validas.size());
        response.setTotalParadasUnicas(totalPontos);
        response.setAvisos(avisos);
        response.setRotas(melhores);
        return response;
    }

    private int contarPontosBairrosPrioritarios(List<EncomendaDTO> encomendas) {
        List<EncomendaDTO> entregasPrioritarias = encomendas.stream()
                .filter(encomenda -> bairroAlvo(encomenda.getBairro())).toList();
        return agrupamentoService.contarEnderecosUnicos(entregasPrioritarias);
    }

    private List<String> listarBairrosDaRota(List<EncomendaDTO> encomendas) {
        Map<String, String> bairrosUnicos = new LinkedHashMap<>();
        for (EncomendaDTO encomenda : encomendas) {
            String bairro = encomenda.getBairro();
            if (bairro == null || bairro.isBlank()) continue;
            String nomeExibicao = nomeBairroCanonico(bairro);
            String chave = normalizarBairro(nomeExibicao)
                    .replaceAll("\\b(jd|jdm|jard)\\b", "jardim")
                    .replaceAll("\\b(atl|atlant)\\b", "atlantico");
            bairrosUnicos.putIfAbsent(chave, nomeExibicao);
        }
        List<String> bairros = bairrosUnicos.values().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        boolean possuiSubdivisaoJardimAtlantico = bairros.stream()
                .anyMatch(nome -> nome.startsWith("Jardim Atlântico "));
        if (possuiSubdivisaoJardimAtlantico) {
            bairros = bairros.stream().filter(nome -> !nome.equals("Jardim Atlântico")).toList();
        }
        return bairros;
    }

    private String nomeBairroCanonico(String bairro) {
        String normalizado = normalizarBairro(bairro);
        if (normalizado.matches(".*\\bbarroco\\b.*")) return "Barroco";
        boolean praiaDeItaipuacu = normalizado.matches(".*\\b(praia|pr)\\b.*")
                && normalizado.matches(".*\\bitaip(?:u|i)acu\\b.*");
        if (praiaDeItaipuacu) return "Praia de Itaipuaçu";
        boolean abreviacaoJardimAtlantico = normalizado.matches(
                ".*\\b(ja|j|jd|jdm|jard|jardm|jrdm|jardim)\\s*(atlantico|atlant|atl)[a-z]*\\b.*")
                || normalizado.matches(".*\\bja\\s+(oeste|oest|leste|lest|central|centr|cen|c)\\b.*")
                || normalizado.matches(".*\\bjardim\\s+a\\s+(oeste|oest|leste|lest|central|centr|cen)\\b.*");
        boolean contextoJardimAtlantico = normalizado.contains("atlantico")
                && (normalizado.contains("itaipuacu") || normalizado.contains("itaipiacu")
                    || normalizado.matches(".*\\b(oeste|oest|leste|lest|central|centr|cen)\\b.*")
                    || normalizado.matches(".*atlantico(central|centr|leste|lest|oeste|oest).*"));
        if (abreviacaoJardimAtlantico || contextoJardimAtlantico) {
            String regiao = normalizado.matches(".*oest[a-z]*.*") ? "Oeste"
                    : normalizado.matches(".*lest[a-z]*.*") || normalizado.matches(".*atlantico\\s+l(?:\\s|$).*") ? "Leste"
                    : normalizado.matches(".*(central|centr|cen)[a-z]*.*")
                        || normalizado.matches(".*atlantico\\s+c(?:\\s|$).*")
                        || normalizado.matches(".*atlantico(c|cent).*" ) ? "Central" : "";
            return regiao.isBlank() ? "Jardim Atlântico" : "Jardim Atlântico " + regiao;
        }
        if (normalizado.matches(".*\\bitaip(?:u|i)acu\\b.*")) return "Itaipuaçu";
        return bairro.trim().replaceAll("\\s+", " ");
    }

    private String normalizarBairro(String bairro) {
        return Normalizer.normalize(bairro.toLowerCase(java.util.Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ").trim()
                .replaceAll("\\bitaiupacu\\b", "itaipuacu")
                .replaceAll("\\bitapuacu\\b", "itaipuacu");
    }

    private boolean bairroAlvo(String bairro) {
        if (bairro == null || bairro.isBlank()) return false;
        String normalizado = normalizarBairro(bairro);
        boolean jardimAtlantico = normalizado.matches(
                ".*\\b(jardim|jard|jdm|jd|jrdm|j)\\s*(atlantico|atlant|atl)[a-z]*\\b.*")
                || normalizado.matches(".*\\bja\\s+(oeste|oest|leste|lest|central|centr|cen|c)\\b.*")
                || normalizado.contains("atlantico")
                    && (normalizado.matches(".*\\b(oeste|oest|leste|lest|central|centr|cen)\\b.*")
                        || normalizado.matches(".*atlantico(central|centr|leste|lest|oeste|oest).*"));
        boolean barroco = normalizado.matches(".*\\bbarroco\\b.*");
        boolean praiaDeItaipuacu = normalizado.matches(".*\\b(praia|pr)\\b.*")
                && normalizado.matches(".*\\bitaip(?:u|i)acu\\b.*");
        return jardimAtlantico || barroco || praiaDeItaipuacu;
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
