package br.com.romaneio.service;

import br.com.romaneio.exception.PlanilhaInvalidaException;
import br.com.romaneio.model.EncomendaDTO;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

@Service
public class PlanilhaParserService {

    public List<EncomendaDTO> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new PlanilhaInvalidaException("O arquivo enviado está vazio.");
        }

        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new PlanilhaInvalidaException("Nome do arquivo não fornecido.");
        }

        String lower = filename.toLowerCase();
        try {
            if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
                return parseExcel(file.getInputStream());
            } else if (lower.endsWith(".csv") || lower.endsWith(".txt")) {
                return parseCsv(file.getInputStream());
            } else {
                throw new PlanilhaInvalidaException("Formato de arquivo não suportado. Utilize arquivos .xlsx, .xls ou .csv");
            }
        } catch (PlanilhaInvalidaException e) {
            throw e;
        } catch (Exception e) {
            throw new PlanilhaInvalidaException("Erro ao processar a planilha: " + e.getMessage(), e);
        }
    }

    public List<EncomendaDTO> parseExcel(InputStream inputStream) {
        List<EncomendaDTO> encomendas = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new PlanilhaInvalidaException("A planilha Excel não possui abas/planilhas.");
            }

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() < 2) {
                throw new PlanilhaInvalidaException("A planilha deve conter pelo menos o cabeçalho e uma linha de dados.");
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new PlanilhaInvalidaException("Cabeçalho da planilha não encontrado na primeira linha.");
            }

            Map<String, Integer> colunas = mapearColunasCabecalho(headerRow, formatter);

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                EncomendaDTO dto = extrairLinha(colunas, (colIdx) -> {
                    if (colIdx < 0 || colIdx >= row.getLastCellNum()) return "";
                    Cell cell = row.getCell(colIdx);
                    return cell != null ? formatter.formatCellValue(cell).trim() : "";
                });

                if (dto != null) {
                    encomendas.add(dto);
                }
            }
        } catch (PlanilhaInvalidaException e) {
            throw e;
        } catch (Exception e) {
            throw new PlanilhaInvalidaException("Falha na leitura do arquivo Excel: " + e.getMessage(), e);
        }

        return encomendas;
    }

    public List<EncomendaDTO> parseCsv(InputStream inputStream) {
        List<EncomendaDTO> encomendas = new ArrayList<>();
        try {
            byte[] bytes = inputStream.readAllBytes();
            char separador = detectarSeparadorCsv(bytes);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8));
                 CSVReader csvReader = new CSVReaderBuilder(reader)
                         .withCSVParser(new CSVParserBuilder().withSeparator(separador).build())
                         .build()) {

                List<String[]> todasLinhas = csvReader.readAll();
                if (todasLinhas.isEmpty() || todasLinhas.size() < 2) {
                    throw new PlanilhaInvalidaException("O arquivo CSV deve conter pelo menos o cabeçalho e uma linha de dados.");
                }

                String[] cabecalhos = todasLinhas.get(0);
                Map<String, Integer> colunas = mapearColunasCabecalho(cabecalhos);

                for (int i = 1; i < todasLinhas.size(); i++) {
                    String[] linha = todasLinhas.get(i);
                    EncomendaDTO dto = extrairLinha(colunas, (colIdx) -> {
                        if (colIdx < 0 || colIdx >= linha.length) return "";
                        return linha[colIdx] != null ? linha[colIdx].trim() : "";
                    });

                    if (dto != null) {
                        encomendas.add(dto);
                    }
                }
            }
        } catch (PlanilhaInvalidaException e) {
            throw e;
        } catch (Exception e) {
            throw new PlanilhaInvalidaException("Falha na leitura do arquivo CSV: " + e.getMessage(), e);
        }

        return encomendas;
    }

    private char detectarSeparadorCsv(byte[] bytes) {
        String amostra = new String(bytes, 0, Math.min(bytes.length, 2048), StandardCharsets.UTF_8);
        int virgulas = contarOcorrencias(amostra, ',');
        int pontoEVirgulas = contarOcorrencias(amostra, ';');
        int tabs = contarOcorrencias(amostra, '\t');

        if (pontoEVirgulas > virgulas && pontoEVirgulas > tabs) {
            return ';';
        } else if (tabs > virgulas && tabs > pontoEVirgulas) {
            return '\t';
        }
        return ',';
    }

    private int contarOcorrencias(String str, char c) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == c) count++;
        }
        return count;
    }

    @FunctionalInterface
    private interface ValorCelulaProvider {
        String get(int colIdx);
    }

    private EncomendaDTO extrairLinha(Map<String, Integer> colunas, ValorCelulaProvider provider) {
        String codigo = colunas.containsKey("codigo") ? provider.get(colunas.get("codigo")) : "";
        String sequence = colunas.containsKey("sequence") ? provider.get(colunas.get("sequence")) : "";
        String cep = colunas.containsKey("cep") ? provider.get(colunas.get("cep")) : "";
        String enderecoCompleto = colunas.containsKey("endereco") ? provider.get(colunas.get("endereco")) : "";
        String logradouro = colunas.containsKey("logradouro") ? provider.get(colunas.get("logradouro")) : "";
        String numero = colunas.containsKey("numero") ? provider.get(colunas.get("numero")) : "";
        String complemento = colunas.containsKey("complemento") ? provider.get(colunas.get("complemento")) : "";
        String bairro = colunas.containsKey("bairro") ? provider.get(colunas.get("bairro")) : "";
        String cidade = colunas.containsKey("cidade") ? provider.get(colunas.get("cidade")) : "";
        String uf = colunas.containsKey("uf") ? provider.get(colunas.get("uf")) : "";
        String corredor = colunas.containsKey("corredor") ? provider.get(colunas.get("corredor")) : "";
        String latStr = colunas.containsKey("latitude") ? provider.get(colunas.get("latitude")) : "";
        String lonStr = colunas.containsKey("longitude") ? provider.get(colunas.get("longitude")) : "";

        // Se linha estiver totalmente em branco, ignorar
        if (codigo.isEmpty() && cep.isEmpty() && enderecoCompleto.isEmpty() && logradouro.isEmpty()) {
            return null;
        }

        // Se o logradouro não veio separado, mas veio a coluna de endereço de destino composto
        if (logradouro.isEmpty() && !enderecoCompleto.isEmpty()) {
            String[] partes = enderecoCompleto.split(",", 3);
            logradouro = partes[0].trim();
            if (partes.length > 1 && numero.isEmpty()) {
                numero = partes[1].trim();
            }
            if (partes.length > 2 && complemento.isEmpty()) {
                complemento = partes[2].trim();
            }
        }

        EncomendaDTO dto = new EncomendaDTO();
        dto.setCodigo(codigo);
        dto.setSequence(sequence);
        dto.setCep(cep);
        dto.setLogradouro(logradouro);
        dto.setNumero(numero.isEmpty() ? "S/N" : numero);
        dto.setComplemento(complemento);
        dto.setBairro(bairro);
        dto.setCidade(cidade.isEmpty() ? "Maricá" : cidade);
        dto.setUf(uf.isEmpty() ? "RJ" : uf);
        dto.setCorredor(corredor);

        Double lat = parseCoordenada(latStr, true);
        Double lon = parseCoordenada(lonStr, false);
        dto.setLatitude(lat);
        dto.setLongitude(lon);
        dto.setEnderecoCompleto(dto.montarEnderecoCompleto());

        return dto;
    }

    public static Double parseCoordenada(String val, boolean isLatitude) {
        if (val == null || val.isBlank()) return null;
        String limpo = val.trim().replace(" ", "");

        try {
            // Caso 1: Formato padrão decimal -22.9674416
            if (limpo.matches("-?\\d+\\.\\d+")) {
                double parsed = Double.parseDouble(limpo);
                if (validarFaixa(parsed, isLatitude)) return parsed;
            }

            // Caso 2: Formato com pontos extras como "-229.674.416" -> -22.9674416
            String apenasDigitos = limpo.replace(".", "").replace(",", "");
            if (apenasDigitos.startsWith("-")) {
                String semSinal = apenasDigitos.substring(1);
                if (semSinal.length() >= 3) {
                    // Coloca ponto após os dois primeiros dígitos (ex: 22.9674416 ou 42.98361)
                    String formatado = "-" + semSinal.substring(0, 2) + "." + semSinal.substring(2);
                    double parsed = Double.parseDouble(formatado);
                    if (validarFaixa(parsed, isLatitude)) return parsed;
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    private static boolean validarFaixa(double val, boolean isLatitude) {
        if (isLatitude) {
            return val >= -90.0 && val <= 90.0;
        } else {
            return val >= -180.0 && val <= 180.0;
        }
    }

    private Map<String, Integer> mapearColunasCabecalho(Row row, DataFormatter formatter) {
        String[] cabecalhos = new String[row.getLastCellNum()];
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            cabecalhos[c] = cell != null ? formatter.formatCellValue(cell) : "";
        }
        return mapearColunasCabecalho(cabecalhos);
    }

    private Map<String, Integer> mapearColunasCabecalho(String[] cabecalhos) {
        Map<String, Integer> map = new HashMap<>();

        for (int i = 0; i < cabecalhos.length; i++) {
            String col = normalizarTexto(cabecalhos[i]);
            if (col.isEmpty()) continue;

            if (col.equals("sequence") || col.equals("sequencia")) {
                map.putIfAbsent("sequence", i);
            } else if (col.contains("codigo") || col.contains("tracking") || col.contains("rastreio") || col.contains("waybill") || col.contains("barcode") || col.matches(".*\\b(id|identifier)\\b.*")) {
                map.putIfAbsent("codigo", i);
            } else if (col.contains("zipcode") || col.contains("cep") || col.contains("postal")) {
                map.putIfAbsent("cep", i);
            } else if (col.contains("destination address") || col.contains("endereco de destino") ||
                    col.contains("endereco completo") || col.contains("endereco")) {
                map.putIfAbsent("endereco", i);
            } else if (col.contains("logradouro") || col.contains("rua") || col.contains("avenida")) {
                map.putIfAbsent("logradouro", i);
            } else if (col.equals("numero") || col.equals("num") || col.contains("numero")) {
                map.putIfAbsent("numero", i);
            } else if (col.contains("complemento") || col.contains("comp")) {
                map.putIfAbsent("complemento", i);
            } else if (col.contains("neighbo") || col.contains("bairro")) {
                map.putIfAbsent("bairro", i);
            } else if (col.contains("city") || col.contains("cidade") || col.contains("municipio")) {
                map.putIfAbsent("cidade", i);
            } else if (col.contains("state") || col.contains("uf") || col.contains("estado")) {
                map.putIfAbsent("uf", i);
            } else if (col.contains("corridor") || col.contains("corredor") || col.contains("cage") || col.contains("gaiola")) {
                map.putIfAbsent("corredor", i);
            } else if (col.contains("latitude") || col.equals("lat")) {
                map.putIfAbsent("latitude", i);
            } else if (col.contains("longitude") || col.equals("long") || col.equals("lng") || col.equals("lon")) {
                map.putIfAbsent("longitude", i);
            }
        }

        return map;
    }

    private String normalizarTexto(String str) {
        if (str == null) return "";
        String nfd = Normalizer.normalize(str.toLowerCase().trim(), Normalizer.Form.NFD);
        return nfd.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
    }
}
