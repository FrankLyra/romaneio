package br.com.romaneio.service;

import br.com.romaneio.model.*;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AgrupamentoService {

    /**
     * Realiza a análise completa de agrupamento das encomendas:
     * 1. Separa por Letra de Rota
     * 2. Agrupa por Rua (com contagem de entregas por rua)
     * 3. Agrupa por Endereço Único / Ponto de Parada (com contagem de pacotes para o mesmo local)
     */
    public AnaliseRomaneioResponseDTO analisarAgrupamentos(List<EncomendaDTO> encomendas) {
        if (encomendas == null || encomendas.isEmpty()) {
            AnaliseRomaneioResponseDTO vazio = new AnaliseRomaneioResponseDTO(true, "Nenhuma encomenda fornecida para análise.");
            vazio.setTotalLinhasLidas(0);
            vazio.setTotalEncomendasValidas(0);
            return vazio;
        }

        // 1. Agrupar por Letra da Rota
        Map<String, List<EncomendaDTO>> porLetra = encomendas.stream()
                .collect(Collectors.groupingBy(
                        enc -> extrairLetraRota(enc.getCorredor()),
                        TreeMap::new,
                        Collectors.toList()
                ));

        List<ResumoRotaLetraDTO> rotasDTO = new ArrayList<>();
        int totalParadasUnicasGeral = 0;
        Set<String> chavesRuasGerais = new HashSet<>();

        for (Map.Entry<String, List<EncomendaDTO>> entry : porLetra.entrySet()) {
            String letra = entry.getKey();
            List<EncomendaDTO> encomendasDaRota = entry.getValue();

            ResumoRotaLetraDTO resumoRota = agruparRotaPorRuasEEnderecos(letra, encomendasDaRota);
            rotasDTO.add(resumoRota);

            totalParadasUnicasGeral += resumoRota.getTotalParadasUnicas();
            for (ResumoRuaDTO rua : resumoRota.getRuas()) {
                chavesRuasGerais.add(gerarChaveRua(rua.getNomeRua(), rua.getBairro(), rua.getCep()));
            }
        }

        AnaliseRomaneioResponseDTO response = new AnaliseRomaneioResponseDTO(true, "Análise de agrupamento concluída com sucesso.");
        response.setTotalLinhasLidas(encomendas.size());
        response.setTotalEncomendasValidas(encomendas.size());
        response.setTotalRotasIdentificadas(rotasDTO.size());
        response.setTotalRuasDistintas(chavesRuasGerais.size());
        response.setTotalParadasUnicas(totalParadasUnicasGeral);
        response.setRotasPorLetra(rotasDTO);

        return response;
    }

    /**
     * Para uma dada rota/letra, agrupa as encomendas em ruas e cada rua em endereços únicos.
     */
    public ResumoRotaLetraDTO agruparRotaPorRuasEEnderecos(String letraRota, List<EncomendaDTO> encomendas) {
        // Agrupar encomendas da rota por rua (chave: logradouro normalizado + bairro normalizado + prefixo CEP)
        Map<String, List<EncomendaDTO>> porRua = encomendas.stream()
                .collect(Collectors.groupingBy(
                        enc -> gerarChaveRua(enc.getLogradouro(), enc.getBairro(), enc.getCep()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<ResumoRuaDTO> listaRuas = new ArrayList<>();
        int totalParadasRota = 0;

        for (List<EncomendaDTO> encsDaRua : porRua.values()) {
            if (encsDaRua.isEmpty()) continue;

            // Extrai dados representativos da rua
            EncomendaDTO primeira = encsDaRua.get(0);
            String nomeRua = formatarNomeRua(primeira.getLogradouro());
            String bairro = primeira.getBairro() != null && !primeira.getBairro().isBlank() ? primeira.getBairro().trim() : "Bairro não informado";
            String cep = primeira.getCep() != null ? primeira.getCep() : "";

            // Agrupar dentro da rua por endereço físico único
            Map<String, List<EncomendaDTO>> porEndereco = encsDaRua.stream()
                    .collect(Collectors.groupingBy(
                            this::gerarChaveEnderecoUnico,
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));

            List<ResumoEnderecoDTO> listaEnderecos = new ArrayList<>();

            for (List<EncomendaDTO> encsDoEndereco : porEndereco.values()) {
                if (encsDoEndereco.isEmpty()) continue;

                EncomendaDTO ref = encsDoEndereco.get(0);
                List<String> codigos = encsDoEndereco.stream()
                        .map(EncomendaDTO::getCodigo)
                        .filter(Objects::nonNull)
                        .filter(c -> !c.isBlank())
                        .collect(Collectors.toList());

                // Se houver coordenada em alguma das encomendas do endereço, utiliza
                Double lat = encsDoEndereco.stream()
                        .map(EncomendaDTO::getLatitude)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);

                Double lon = encsDoEndereco.stream()
                        .map(EncomendaDTO::getLongitude)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);

                ResumoEnderecoDTO endDTO = new ResumoEnderecoDTO(
                        ref.getEnderecoCompleto(),
                        ref.getLogradouro(),
                        ref.getNumero(),
                        ref.getComplemento(),
                        ref.getBairro(),
                        ref.getCep(),
                        lat,
                        lon,
                        encsDoEndereco.size(),
                        codigos
                );

                listaEnderecos.add(endDTO);
            }

            // Ordenar endereços por número (se numérico) para facilitar visualização
            listaEnderecos.sort((a, b) -> compararNumerosEndereco(a.getNumero(), b.getNumero()));

            ResumoRuaDTO resumoRua = new ResumoRuaDTO(
                    nomeRua,
                    bairro,
                    cep,
                    encsDaRua.size(),       // Total de encomendas/volumes na rua
                    listaEnderecos.size(),   // Total de paradas/endereços distintos na rua
                    listaEnderecos
            );

            listaRuas.add(resumoRua);
            totalParadasRota += listaEnderecos.size();
        }

        // Ordenar ruas pelo volume de entregas (decrescente)
        listaRuas.sort((r1, r2) -> Integer.compare(r2.getTotalEntregas(), r1.getTotalEntregas()));

        return new ResumoRotaLetraDTO(
                letraRota,
                encomendas.size(),
                listaRuas.size(),
                totalParadasRota,
                listaRuas
        );
    }

    /**
     * Extrai a letra da rota do campo corredor/rota (ex: 'A-01' -> 'A', 'ROTA B' -> 'B', 'C' -> 'C').
     */
    public String extrairLetraRota(String corredor) {
        if (corredor == null || corredor.isBlank()) {
            return "A"; // Padrão se não informado
        }

        String limpo = corredor.trim().toUpperCase();

        // Se contiver palavra 'ROTA X' ou 'ROUTE X'
        if (limpo.startsWith("ROTA") || limpo.startsWith("ROUTE")) {
            String resto = limpo.replace("ROTA", "").replace("ROUTE", "").trim();
            if (!resto.isEmpty() && Character.isLetter(resto.charAt(0))) {
                return String.valueOf(resto.charAt(0));
            }
        }

        // Procura a primeira letra relevante
        for (int i = 0; i < limpo.length(); i++) {
            char c = limpo.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                return String.valueOf(c);
            }
        }

        return limpo;
    }

    private String gerarChaveRua(String logradouro, String bairro, String cep) {
        String logNorm = normalizarTexto(logradouro);
        String baiNorm = normalizarTexto(bairro);
        String cepNorm = cep != null ? cep.replaceAll("[^0-9]", "") : "";
        String cepPrefixo = cepNorm.length() >= 5 ? cepNorm.substring(0, 5) : cepNorm;

        return logNorm + "|" + baiNorm + "|" + cepPrefixo;
    }

    private String gerarChaveEnderecoUnico(EncomendaDTO enc) {
        String cepDigitos = enc.getCep() != null ? enc.getCep().replaceAll("[^0-9]", "") : "";
        String logNorm = normalizarTexto(enc.getLogradouro());
        String numNorm = normalizarTexto(enc.getNumero());
        String compNorm = normalizarTexto(enc.getComplemento());
        String baiNorm = normalizarTexto(enc.getBairro());

        return cepDigitos + "|" + logNorm + "|" + numNorm + "|" + compNorm + "|" + baiNorm;
    }

    private String formatarNomeRua(String logradouro) {
        if (logradouro == null || logradouro.isBlank()) {
            return "Logradouro não informado";
        }
        return logradouro.trim();
    }

    private String normalizarTexto(String str) {
        if (str == null) return "";
        String nfd = Normalizer.normalize(str.toLowerCase().trim(), Normalizer.Form.NFD);
        return nfd.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private int compararNumerosEndereco(String num1, String num2) {
        if (num1 == null) return -1;
        if (num2 == null) return 1;

        try {
            int n1 = Integer.parseInt(num1.replaceAll("[^0-9]", ""));
            int n2 = Integer.parseInt(num2.replaceAll("[^0-9]", ""));
            return Integer.compare(n1, n2);
        } catch (Exception e) {
            return num1.compareToIgnoreCase(num2);
        }
    }
}
