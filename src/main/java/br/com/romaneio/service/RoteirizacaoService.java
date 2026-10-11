package br.com.romaneio.service;

import br.com.romaneio.model.EncomendaDTO;
import br.com.romaneio.model.ParadaDTO;
import br.com.romaneio.model.RotaResponseDTO;
import br.com.romaneio.util.BaseFixaConstants;
import br.com.romaneio.util.GeoUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RoteirizacaoService {

    public List<RotaResponseDTO> calcularMelhoresRotas(List<EncomendaDTO> encomendas) {
        if (encomendas == null || encomendas.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. Agrupar encomendas em paradas únicas de entrega
        List<ParadaDTO> paradasEntrega = agruparEncomendasEmParadas(encomendas);
        int totalEncomendas = encomendas.size();

        // 2. Criar a parada de origem fixa
        ParadaDTO origem = criarParadaOrigem();

        if (paradasEntrega.isEmpty()) {
            RotaResponseDTO rotaVazia = criarRota(1, "A", "Rota A1 (Origem)", "Nenhuma parada de entrega", true, List.of(origem), totalEncomendas);
            return List.of(rotaVazia);
        }

        if (paradasEntrega.size() == 1) {
            List<RotaResponseDTO> rotasUnicas = new ArrayList<>();
            char[] letras = new char[]{'A', 'B', 'C', 'D', 'E'};
            for (int i = 0; i < 5; i++) {
                List<ParadaDTO> lista = List.of(clonarParada(origem), clonarParada(paradasEntrega.get(0)));
                boolean principal = (i == 0);
                String letra = String.valueOf(letras[i]);
                int rank = i + 1;
                String nome = principal ? "Rota " + letra + rank + " (Menor Distância)" : "Rota " + letra + rank;
                rotasUnicas.add(criarRota(rank, letra, nome, "Rota direta com parada única", principal, lista, totalEncomendas));
            }
            return rotasUnicas;
        }

        // 3. Gerar estratégias candidatas de rotas (Open TSP a partir da Origem)
        List<List<ParadaDTO>> candidatos = new ArrayList<>();
        int n = paradasEntrega.size();

        // Estratégia 1: Vizinho Mais Próximo da Origem + Otimização 2-Opt
        candidatos.add(otimizar2Opt(gerarVizinhoMaisProximo(origem, paradasEntrega, 0), origem));

        // Estratégia 2: Varredura Polar Horária a partir da Origem + 2-Opt
        candidatos.add(otimizar2Opt(gerarVarreduraAngular(origem, paradasEntrega, true), origem));

        // Estratégia 3: Varredura Polar Anti-Horária a partir da Origem + 2-Opt
        candidatos.add(otimizar2Opt(gerarVarreduraAngular(origem, paradasEntrega, false), origem));

        // Estratégia 4: Agrupamento Regional (CEP / Bairro) + Proximidade + 2-Opt
        candidatos.add(otimizar2Opt(gerarPorAgrupamentoRegional(origem, paradasEntrega), origem));

        // Estratégia 5: Ordenação Linear Geográfica / Projeção + 2-Opt
        candidatos.add(otimizar2Opt(gerarPorOrdenacaoLinear(origem, paradasEntrega), origem));

        // Estratégias complementares
        if (n <= 200) {
            candidatos.add(otimizar2Opt(gerarVizinhoMaisProximo(origem, paradasEntrega, 1), origem));
            candidatos.add(otimizar2Opt(gerarInsercaoMaisDistante(origem, paradasEntrega), origem));
        }

        // 4. Montar e avaliar as rotas calculadas
        List<RotaResponseDTO> rotasAvaliadas = new ArrayList<>();
        Set<String> assinaturasVistas = new HashSet<>();

        for (List<ParadaDTO> sequenciaEntrega : candidatos) {
            String assinatura = sequenciaEntrega.stream()
                    .map(p -> p.getLatitude() + "," + p.getLongitude())
                    .collect(Collectors.joining(";"));

            if (assinaturasVistas.contains(assinatura)) {
                continue;
            }
            assinaturasVistas.add(assinatura);

            List<ParadaDTO> rotaCompleta = new ArrayList<>();
            rotaCompleta.add(clonarParada(origem));
            for (ParadaDTO p : sequenciaEntrega) {
                rotaCompleta.add(clonarParada(p));
            }

            RotaResponseDTO rotaDTO = criarRota(0, "", "", "", false, rotaCompleta, totalEncomendas);
            rotasAvaliadas.add(rotaDTO);
        }

        // Ordenar por menor distância total
        rotasAvaliadas.sort(Comparator.comparingDouble(RotaResponseDTO::getDistanciaTotalKm));

        // Garantir exatamente 5 rotas distintas ou preencher com variações caso haja poucas paradas
        while (rotasAvaliadas.size() < 5) {
            RotaResponseDTO copia = clonarRotaDTO(rotasAvaliadas.get(rotasAvaliadas.size() - 1));
            rotasAvaliadas.add(copia);
        }

        List<RotaResponseDTO> cincoMelhores = rotasAvaliadas.subList(0, 5);

        // 5. Configurar IDs, letras, nomes e flag principal
        String[] letras = new String[]{"A", "B", "C", "D", "E", "F", "G", "H", "I", "J"};
        for (int i = 0; i < cincoMelhores.size(); i++) {
            RotaResponseDTO rota = cincoMelhores.get(i);
            int rank = i + 1;
            String letra = (i < letras.length) ? letras[i] : String.valueOf((char) ('A' + i));
            rota.setId(rank);
            rota.setLetra(letra);
            if (rank == 1) {
                rota.setPrincipal(true);
                rota.setNome("Rota " + letra + rank + " (Menor Distância)");
                rota.setDescricao("Melhor alternativa com a menor distância total calculada e sequência otimizada de paradas.");
            } else {
                rota.setPrincipal(false);
                rota.setNome("Rota " + letra + rank);
                rota.setDescricao("Alternativa viável de roteamento com menor sobreposição de trechos.");
            }
        }

        return cincoMelhores;
    }

    private List<ParadaDTO> agruparEncomendasEmParadas(List<EncomendaDTO> encomendas) {
        Map<String, ParadaDTO> mapaParadas = new LinkedHashMap<>();

        for (EncomendaDTO enc : encomendas) {
            String chave = enc.getChaveLocalizacao();
            ParadaDTO parada = mapaParadas.computeIfAbsent(chave, k -> {
                ParadaDTO p = new ParadaDTO();
                p.setTipo("ENTREGA");
                p.setEndereco(enc.getEnderecoCompleto());
                p.setLogradouro(enc.getLogradouro());
                p.setNumero(enc.getNumero());
                p.setBairro(enc.getBairro());
                p.setCidade(enc.getCidade());
                p.setUf(enc.getUf());
                p.setCep(enc.getCep());
                p.setLatitude(enc.getLatitude());
                p.setLongitude(enc.getLongitude());
                return p;
            });
            parada.adicionarEncomenda(enc);
        }

        return new ArrayList<>(mapaParadas.values());
    }

    private ParadaDTO criarParadaOrigem() {
        ParadaDTO origem = new ParadaDTO(1, "ORIGEM", BaseFixaConstants.ENDERECO_COMPLETO,
                BaseFixaConstants.LATITUDE, BaseFixaConstants.LONGITUDE);
        origem.setLogradouro(BaseFixaConstants.LOGRADOURO);
        origem.setNumero(BaseFixaConstants.NUMERO);
        origem.setBairro(BaseFixaConstants.BAIRRO);
        origem.setCidade(BaseFixaConstants.CIDADE);
        origem.setUf(BaseFixaConstants.UF);
        origem.setCep(BaseFixaConstants.CEP);
        return origem;
    }

    // Heurística do Vizinho Mais Próximo
    private List<ParadaDTO> gerarVizinhoMaisProximo(ParadaDTO origem, List<ParadaDTO> paradas, int offsetInicio) {
        if (paradas.isEmpty()) return new ArrayList<>();

        if (paradas.size() > 300) {
            List<ParadaDTO> ordenada = new ArrayList<>(paradas);
            ordenada.sort(Comparator.comparingDouble((ParadaDTO p) -> calcularDistancia(origem, p))
                    .thenComparingDouble(ParadaDTO::getLongitude));
            return ordenada;
        }

        List<ParadaDTO> naoVisitadas = new ArrayList<>(paradas);
        List<ParadaDTO> resultado = new ArrayList<>();

        // Ordena por distância da origem para escolher ponto de partida
        naoVisitadas.sort(Comparator.comparingDouble(p -> calcularDistancia(origem, p)));

        int indiceInicial = Math.min(offsetInicio, naoVisitadas.size() - 1);
        ParadaDTO atual = naoVisitadas.remove(indiceInicial);
        resultado.add(atual);

        while (!naoVisitadas.isEmpty()) {
            final ParadaDTO pontoAtual = atual;
            ParadaDTO maisProximo = null;
            double menorDist = Double.MAX_VALUE;
            int indiceMaisProximo = 0;

            for (int i = 0; i < naoVisitadas.size(); i++) {
                ParadaDTO p = naoVisitadas.get(i);
                double d = calcularDistancia(pontoAtual, p);
                if (d < menorDist) {
                    menorDist = d;
                    maisProximo = p;
                    indiceMaisProximo = i;
                }
            }

            atual = naoVisitadas.remove(indiceMaisProximo);
            resultado.add(atual);
        }

        return resultado;
    }

    // Heurística de Varredura Angular (Sweep Polar)
    private List<ParadaDTO> gerarVarreduraAngular(ParadaDTO origem, List<ParadaDTO> paradas, boolean horario) {
        List<ParadaDTO> ordenadas = new ArrayList<>(paradas);
        ordenadas.sort((p1, p2) -> {
            double angulo1 = Math.atan2(p1.getLatitude() - origem.getLatitude(), p1.getLongitude() - origem.getLongitude());
            double angulo2 = Math.atan2(p2.getLatitude() - origem.getLatitude(), p2.getLongitude() - origem.getLongitude());
            int cmp = Double.compare(angulo1, angulo2);
            return horario ? cmp : -cmp;
        });
        return ordenadas;
    }

    // Heurística de Agrupamento Regional (CEP / Bairro)
    private List<ParadaDTO> gerarPorAgrupamentoRegional(ParadaDTO origem, List<ParadaDTO> paradas) {
        Map<String, List<ParadaDTO>> clusters = new LinkedHashMap<>();
        for (ParadaDTO p : paradas) {
            String cepDigitos = p.getCep() != null ? p.getCep().replaceAll("[^0-9]", "") : "";
            String regiao = (cepDigitos.length() >= 5) ?
                    cepDigitos.substring(0, 5) : (p.getBairro() != null ? p.getBairro().trim().toLowerCase() : "Geral");
            clusters.computeIfAbsent(regiao, k -> new ArrayList<>()).add(p);
        }

        // Ordena cada cluster pela distância média até a origem
        List<Map.Entry<String, List<ParadaDTO>>> listaClusters = new ArrayList<>(clusters.entrySet());
        listaClusters.sort(Comparator.comparingDouble(entry -> {
            List<ParadaDTO> lista = entry.getValue();
            return lista.stream().mapToDouble(p -> calcularDistancia(origem, p)).average().orElse(0.0);
        }));

        List<ParadaDTO> resultado = new ArrayList<>();
        for (Map.Entry<String, List<ParadaDTO>> entry : listaClusters) {
            List<ParadaDTO> subLista = entry.getValue();
            subLista.sort(Comparator.comparingDouble(p -> calcularDistancia(origem, p)));
            resultado.addAll(subLista);
        }
        return resultado;
    }

    // Heurística de Inserção Mais Distante
    private List<ParadaDTO> gerarInsercaoMaisDistante(ParadaDTO origem, List<ParadaDTO> paradas) {
        List<ParadaDTO> naoVisitadas = new ArrayList<>(paradas);
        List<ParadaDTO> rota = new ArrayList<>();

        if (naoVisitadas.isEmpty()) return rota;

        // Escolhe o ponto mais distante da origem
        naoVisitadas.sort((p1, p2) -> Double.compare(calcularDistancia(origem, p2), calcularDistancia(origem, p1)));
        rota.add(naoVisitadas.remove(0));

        while (!naoVisitadas.isEmpty()) {
            // Acha o ponto mais distante de todos os pontos na rota
            ParadaDTO maisDistante = null;
            double maxDist = -1.0;
            int idxMaisDistante = 0;

            for (int i = 0; i < naoVisitadas.size(); i++) {
                ParadaDTO cand = naoVisitadas.get(i);
                double minDistNaRota = rota.stream().mapToDouble(p -> calcularDistancia(p, cand)).min().orElse(0.0);
                if (minDistNaRota > maxDist) {
                    maxDist = minDistNaRota;
                    maisDistante = cand;
                    idxMaisDistante = i;
                }
            }

            naoVisitadas.remove(idxMaisDistante);

            // Acha a melhor posição de inserção na rota aberta
            int melhorPos = 0;
            double menorCusto = Double.MAX_VALUE;

            for (int pos = 0; pos <= rota.size(); pos++) {
                double custo = calcularCustoInsercao(origem, rota, maisDistante, pos);
                if (custo < menorCusto) {
                    menorCusto = custo;
                    melhorPos = pos;
                }
            }

            rota.add(melhorPos, maisDistante);
        }

        return rota;
    }

    private double calcularCustoInsercao(ParadaDTO origem, List<ParadaDTO> rota, ParadaDTO novo, int pos) {
        List<ParadaDTO> temp = new ArrayList<>(rota);
        temp.add(pos, novo);
        return calcularDistanciaTotalAberta(origem, temp);
    }

    // Ordenação linear por Projeção Geográfica
    private List<ParadaDTO> gerarPorOrdenacaoLinear(ParadaDTO origem, List<ParadaDTO> paradas) {
        List<ParadaDTO> lista = new ArrayList<>(paradas);
        lista.sort(Comparator.comparingDouble(ParadaDTO::getLongitude)
                .thenComparingDouble(ParadaDTO::getLatitude));
        return lista;
    }

    // Otimização 2-Opt para Open TSP (caminho aberto com origem fixa)
    private List<ParadaDTO> otimizar2Opt(List<ParadaDTO> paradas, ParadaDTO origem) {
        if (paradas == null || paradas.size() <= 2) {
            return paradas;
        }

        List<ParadaDTO> rota = new ArrayList<>(paradas);
        int n = rota.size();
        boolean melhorou = true;
        int maxIteracoes = n > 500 ? 5 : (n > 100 ? 20 : 100);
        int maxJanela = n > 500 ? 30 : n;
        int iter = 0;

        while (melhorou && iter < maxIteracoes) {
            melhorou = false;
            iter++;

            for (int i = 0; i < n - 1; i++) {
                int limitJ = Math.min(n, i + maxJanela);
                for (int j = i + 1; j < limitJ; j++) {
                    double delta = calcularDelta2Opt(origem, rota, i, j);
                    if (delta < -1e-6) {
                        inverterSublista(rota, i, j);
                        melhorou = true;
                    }
                }
            }
        }

        return rota;
    }

    private double calcularDelta2Opt(ParadaDTO origem, List<ParadaDTO> rota, int i, int j) {
        int n = rota.size();
        ParadaDTO prev = (i == 0) ? origem : rota.get(i - 1);
        ParadaDTO pi = rota.get(i);
        ParadaDTO pj = rota.get(j);
        ParadaDTO next = (j < n - 1) ? rota.get(j + 1) : null;

        double distAtual = calcularDistancia(prev, pi);
        if (next != null) {
            distAtual += calcularDistancia(pj, next);
        }

        double distNova = calcularDistancia(prev, pj);
        if (next != null) {
            distNova += calcularDistancia(pi, next);
        }

        return distNova - distAtual;
    }

    private void inverterSublista(List<ParadaDTO> lista, int i, int j) {
        while (i < j) {
            ParadaDTO temp = lista.get(i);
            lista.set(i, lista.get(j));
            lista.set(j, temp);
            i++;
            j--;
        }
    }

    private double calcularDistanciaTotalAberta(ParadaDTO origem, List<ParadaDTO> paradas) {
        if (paradas.isEmpty()) return 0.0;
        double total = calcularDistancia(origem, paradas.get(0));
        for (int i = 0; i < paradas.size() - 1; i++) {
            total += calcularDistancia(paradas.get(i), paradas.get(i + 1));
        }
        return total;
    }

    private double calcularDistancia(ParadaDTO p1, ParadaDTO p2) {
        if (p1 == null || p2 == null) return 0.0;
        return GeoUtils.calcularDistanciaKm(
                p1.getLatitude(), p1.getLongitude(),
                p2.getLatitude(), p2.getLongitude()
        );
    }

    private RotaResponseDTO criarRota(int id, String letra, String nome, String descricao, boolean principal,
                                      List<ParadaDTO> paradas, int totalEncomendas) {
        RotaResponseDTO dto = new RotaResponseDTO(id, letra, nome, descricao, principal);

        double distAcumulada = 0.0;
        for (int i = 0; i < paradas.size(); i++) {
            ParadaDTO p = paradas.get(i);
            p.setOrdem(i + 1);

            if (i == 0) {
                p.setDistanciaDoPontoAnteriorKm(0.0);
                p.setDistanciaAcumuladaKm(0.0);
            } else {
                ParadaDTO anterior = paradas.get(i - 1);
                double d = calcularDistancia(anterior, p);
                distAcumulada += d;
                p.setDistanciaDoPontoAnteriorKm(Math.round(d * 100.0) / 100.0);
                p.setDistanciaAcumuladaKm(Math.round(distAcumulada * 100.0) / 100.0);
            }
        }

        double totalKm = Math.round(distAcumulada * 100.0) / 100.0;
        dto.setDistanciaTotalKm(totalKm);
        dto.setDistanciaTotalFormatada(GeoUtils.formatarDistancia(totalKm));

        int totalParadas = paradas.size();
        int tempoMin = GeoUtils.estimarTempoMinutos(totalKm, totalParadas);
        dto.setTempoEstimadoMinutos(tempoMin);
        dto.setTempoEstimadoFormatado(GeoUtils.formatarTempo(tempoMin));
        dto.setQuantidadeParadas(totalParadas);
        dto.setQuantidadeEncomendas(totalEncomendas);
        dto.setParadas(paradas);

        return dto;
    }

    private ParadaDTO clonarParada(ParadaDTO original) {
        ParadaDTO p = new ParadaDTO();
        p.setOrdem(original.getOrdem());
        p.setTipo(original.getTipo());
        p.setEndereco(original.getEndereco());
        p.setLogradouro(original.getLogradouro());
        p.setNumero(original.getNumero());
        p.setBairro(original.getBairro());
        p.setCidade(original.getCidade());
        p.setUf(original.getUf());
        p.setCep(original.getCep());
        p.setLatitude(original.getLatitude());
        p.setLongitude(original.getLongitude());
        p.setDistanciaDoPontoAnteriorKm(original.getDistanciaDoPontoAnteriorKm());
        p.setDistanciaAcumuladaKm(original.getDistanciaAcumuladaKm());
        p.setEncomendas(new ArrayList<>(original.getEncomendas()));
        p.setQuantidadeEncomendas(original.getQuantidadeEncomendas());
        return p;
    }

    private RotaResponseDTO clonarRotaDTO(RotaResponseDTO original) {
        List<ParadaDTO> paradasClonadas = original.getParadas().stream()
                .map(this::clonarParada)
                .collect(Collectors.toList());

        RotaResponseDTO rota = new RotaResponseDTO();
        rota.setId(original.getId());
        rota.setLetra(original.getLetra());
        rota.setNome(original.getNome());
        rota.setDescricao(original.getDescricao());
        rota.setPrincipal(original.isPrincipal());
        rota.setDistanciaTotalKm(original.getDistanciaTotalKm());
        rota.setDistanciaTotalFormatada(original.getDistanciaTotalFormatada());
        rota.setTempoEstimadoMinutos(original.getTempoEstimadoMinutos());
        rota.setTempoEstimadoFormatado(original.getTempoEstimadoFormatado());
        rota.setQuantidadeParadas(original.getQuantidadeParadas());
        rota.setQuantidadeEncomendas(original.getQuantidadeEncomendas());
        rota.setParadas(paradasClonadas);
        return rota;
    }
}
