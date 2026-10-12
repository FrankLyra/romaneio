package br.com.romaneio.service;

import br.com.romaneio.model.*;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AgrupamentoService {
    public AnaliseRomaneioResponseDTO analisarAgrupamentos(List<EncomendaDTO> encomendas) {
        if (encomendas == null || encomendas.isEmpty()) {
            AnaliseRomaneioResponseDTO vazio = new AnaliseRomaneioResponseDTO(true, "Nenhuma encomenda fornecida para análise.");
            vazio.setTotalLinhasLidas(0);
            vazio.setTotalEncomendasValidas(0);
            return vazio;
        }

        Map<String, List<EncomendaDTO>> porRota = encomendas.stream().collect(Collectors.groupingBy(
                e -> normalizarIdentificador(e.getCorredor()), TreeMap::new, Collectors.toList()));
        List<ResumoRotaLetraDTO> rotas = new ArrayList<>();
        Set<String> ruasUnicas = new HashSet<>();
        int paradasUnicas = 0;
        for (var entry : porRota.entrySet()) {
            ResumoRotaLetraDTO resumo = agruparRotaPorRuasEEnderecos(entry.getKey(), entry.getValue());
            rotas.add(resumo);
            paradasUnicas += resumo.getTotalParadasUnicas();
            for (ResumoRuaDTO rua : resumo.getRuas()) {
                ruasUnicas.add(entry.getKey() + "|" + chaveRua(rua.getNomeRua()) + "|" + normalizar(rua.getBairro()));
            }
        }
        AnaliseRomaneioResponseDTO response = new AnaliseRomaneioResponseDTO(true, "Análise de agrupamento concluída com sucesso.");
        response.setTotalLinhasLidas(encomendas.size());
        response.setTotalEncomendasValidas(encomendas.size());
        response.setTotalRotasIdentificadas(rotas.size());
        response.setTotalRuasDistintas(ruasUnicas.size());
        response.setTotalParadasUnicas(paradasUnicas);
        response.setRotasPorLetra(rotas);
        return response;
    }

    public ResumoRotaLetraDTO agruparRotaPorRuasEEnderecos(String identificadorRota, List<EncomendaDTO> encomendas) {
        List<List<EncomendaDTO>> gruposRua = agrupar(encomendas, this::mesmaRua);
        List<ResumoRuaDTO> ruas = new ArrayList<>();
        int totalParadas = 0;
        for (List<EncomendaDTO> grupo : gruposRua) {
            EncomendaDTO refRua = grupo.get(0);
            String nomeRua = refRua.getLogradouro() == null ? "Logradouro não informado" : refRua.getLogradouro().trim();
            List<List<EncomendaDTO>> gruposEndereco = agrupar(grupo, this::mesmoEndereco);
            List<ResumoEnderecoDTO> enderecos = new ArrayList<>();
            for (List<EncomendaDTO> entregas : gruposEndereco) {
                EncomendaDTO ref = entregas.get(0);
                Double lat = entregas.stream().map(EncomendaDTO::getLatitude).filter(Objects::nonNull).findFirst().orElse(null);
                Double lon = entregas.stream().map(EncomendaDTO::getLongitude).filter(Objects::nonNull).findFirst().orElse(null);
                List<String> codigos = entregas.stream().map(EncomendaDTO::getCodigo).filter(Objects::nonNull).distinct().toList();
                enderecos.add(new ResumoEnderecoDTO(ref.getEnderecoCompleto(), ref.getLogradouro(), ref.getNumero(),
                        ref.getComplemento(), ref.getBairro(), ref.getCep(), lat, lon, entregas.size(), codigos));
            }
            enderecos.sort((a, b) -> compararNumeros(a.getNumero(), b.getNumero()));
            String cep = refRua.getCep();
            ruas.add(new ResumoRuaDTO(nomeRua, refRua.getBairro(), cep, grupo.size(), enderecos.size(), enderecos));
            totalParadas += enderecos.size();
        }
        ruas.sort(Comparator.comparingInt(ResumoRuaDTO::getTotalEntregas).reversed());
        return new ResumoRotaLetraDTO(identificadorRota, encomendas == null ? 0 : encomendas.size(), ruas.size(), totalParadas, ruas);
    }

    public String extrairLetraRota(String corredor) { return normalizarIdentificador(corredor); }

    /** Conta pontos únicos, consolidando encomendas no mesmo logradouro, número e CEP. */
    public int contarEnderecosUnicos(List<EncomendaDTO> encomendas) {
        return agrupar(encomendas, this::mesmoEndereco).size();
    }

    private List<List<EncomendaDTO>> agrupar(List<EncomendaDTO> itens, Comparador comparador) {
        List<List<EncomendaDTO>> grupos = new ArrayList<>();
        if (itens == null) return grupos;
        for (EncomendaDTO item : itens) {
            List<EncomendaDTO> existente = grupos.stream().filter(g -> comparador.iguais(g.get(0), item)).findFirst().orElse(null);
            if (existente == null) { existente = new ArrayList<>(); grupos.add(existente); }
            existente.add(item);
        }
        return grupos;
    }

    private boolean mesmaRua(EncomendaDTO a, EncomendaDTO b) {
        if (!compatibilidadeLocal(a, b)) return false;
        String ruaA = chaveRua(a.getLogradouro()), ruaB = chaveRua(b.getLogradouro());
        return ruaA.equals(ruaB) || nomesParecidos(ruaA, ruaB);
    }

    private boolean mesmoEndereco(EncomendaDTO a, EncomendaDTO b) {
        String numeroA = normalizar(a.getNumero()), numeroB = normalizar(b.getNumero());
        String cepA = digitos(a.getCep()), cepB = digitos(b.getCep());
        // Sem número ou CEP não há dados suficientes para afirmar que é o mesmo endereço.
        return !numeroA.isBlank() && numeroA.equals(numeroB)
                && !cepA.isBlank() && cepA.equals(cepB)
                && nomesDeRuaCompativeis(a.getLogradouro(), b.getLogradouro());
    }

    private boolean nomesDeRuaCompativeis(String logradouroA, String logradouroB) {
        String ruaA = chaveRua(logradouroA), ruaB = chaveRua(logradouroB);
        return !ruaA.isBlank() && (ruaA.equals(ruaB) || nomesParecidos(ruaA, ruaB));
    }

    private boolean compatibilidadeLocal(EncomendaDTO a, EncomendaDTO b) {
        String bairroA = normalizar(a.getBairro()), bairroB = normalizar(b.getBairro());
        if (!bairroA.isBlank() && !bairroB.isBlank() && !bairroA.equals(bairroB)) return false;
        String cepA = digitos(a.getCep()), cepB = digitos(b.getCep());
        return cepA.isBlank() || cepB.isBlank() || cepA.equals(cepB);
    }

    private boolean nomesParecidos(String a, String b) {
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

    private String chaveRua(String valor) {
        String rua = normalizar(valor).replaceFirst("^(rua|r|avenida|av|travessa|tv|estrada|estr|rodovia|rod|alameda|al)\\s+", "");
        return rua.replaceAll("\\b(jose)\\s+g\\s+", "$1 goncalves ");
    }
    private String normalizarIdentificador(String valor) {
        if (valor == null || valor.isBlank()) return "SEM-IDENTIFICADOR";
        String id = valor.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if (id.startsWith("ROTA")) id = id.substring(4);
        return id.isBlank() ? "SEM-IDENTIFICADOR" : id;
    }
    private String normalizar(String valor) {
        if (valor == null) return "";
        return Normalizer.normalize(valor.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
    }
    private String digitos(String valor) { return valor == null ? "" : valor.replaceAll("[^0-9]", ""); }
    private int compararNumeros(String a, String b) {
        try { return Integer.compare(Integer.parseInt(digitos(a)), Integer.parseInt(digitos(b))); }
        catch (NumberFormatException e) { return Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER).compare(a, b); }
    }
    @FunctionalInterface private interface Comparador { boolean iguais(EncomendaDTO a, EncomendaDTO b); }
}
