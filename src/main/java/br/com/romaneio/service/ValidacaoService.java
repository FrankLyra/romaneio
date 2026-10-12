package br.com.romaneio.service;

import br.com.romaneio.exception.PlanilhaInvalidaException;
import br.com.romaneio.model.EncomendaDTO;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ValidacaoService {

    public static class ResultadoValidacao {
        private final List<EncomendaDTO> encomendasValidas = new ArrayList<>();
        private final List<String> avisos = new ArrayList<>();
        private int totalDuplicados = 0;
        private int totalInvalidos = 0;

        public List<EncomendaDTO> getEncomendasValidas() {
            return encomendasValidas;
        }

        public List<String> getAvisos() {
            return avisos;
        }

        public int getTotalDuplicados() {
            return totalDuplicados;
        }

        public int getTotalInvalidos() {
            return totalInvalidos;
        }
    }

    public ResultadoValidacao validarEncomendas(List<EncomendaDTO> listaBruta) {
        if (listaBruta == null || listaBruta.isEmpty()) {
            throw new PlanilhaInvalidaException("A planilha não contém dados válidos de encomendas para processamento.");
        }

        ResultadoValidacao resultado = new ResultadoValidacao();
        Set<String> codigosProcessados = new HashSet<>();
        int ocorrenciasDuplicadas = 0;
        int linhaNum = 1;

        for (EncomendaDTO enc : listaBruta) {
            linhaNum++;
            // O romaneio pode não conter um identificador único. Sequence é uma ordem da planilha,
            // não um código de encomenda; linhas sem código continuam sendo entregas válidas.
            String codNormalizado = enc.getCodigo() == null ? "" : enc.getCodigo().trim();
            if (!codNormalizado.isBlank() && !codigosProcessados.add(codNormalizado)) {
                resultado.totalDuplicados++;
                ocorrenciasDuplicadas++;
            }
            // Validação de Logradouro obrigatório
            if (enc.getLogradouro() == null || enc.getLogradouro().isBlank()) {
                resultado.totalInvalidos++;
                resultado.avisos.add("Linha " + linhaNum + ": Encomenda ignorada por falta de logradouro obrigatório.");
                continue;
            }

            // Normalização de número (permite s/n, S/N, 0, 000 ou ausente assumindo S/N)
            if (enc.getNumero() == null || enc.getNumero().isBlank()) {
                enc.setNumero("S/N");
            }

            // Normalização e validação auxiliar de CEP
            if (enc.getCep() != null && !enc.getCep().isBlank()) {
                String cepDigitos = enc.getCep().replaceAll("[^0-9]", "");
                if (cepDigitos.length() == 8) {
                    enc.setCep(cepDigitos.substring(0, 5) + "-" + cepDigitos.substring(5));
                } else if (!cepDigitos.isEmpty()) {
                    resultado.avisos.add("Linha " + linhaNum + " (Código " + codNormalizado + "): CEP '" + enc.getCep() + "' possui formato não padrão.");
                }
            }

            // Normalização de Cidade / UF caso vazio
            if (enc.getCidade() == null || enc.getCidade().isBlank()) {
                enc.setCidade("Maricá");
            }
            if (enc.getUf() == null || enc.getUf().isBlank()) {
                enc.setUf("RJ");
            }

            resultado.encomendasValidas.add(enc);
        }

        if (ocorrenciasDuplicadas > 0) {
            resultado.avisos.add(ocorrenciasDuplicadas + " linhas repetem código de encomenda; as linhas foram mantidas no processamento.");
        }

        if (resultado.encomendasValidas.isEmpty()) {
            throw new PlanilhaInvalidaException("Nenhuma encomenda válida foi identificada após a validação das linhas da planilha.");
        }

        return resultado;
    }
}
