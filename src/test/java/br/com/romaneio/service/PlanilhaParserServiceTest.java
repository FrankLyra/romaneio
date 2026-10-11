package br.com.romaneio.service;

import br.com.romaneio.exception.PlanilhaInvalidaException;
import br.com.romaneio.model.EncomendaDTO;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlanilhaParserServiceTest {

    private PlanilhaParserService parserService;

    @BeforeEach
    void setUp() {
        parserService = new PlanilhaParserService();
    }

    @Test
    void deveParsearCsvComVirgula() {
        String csv = "Sequence,Zipcode,Destination Address,City,Neighborhood\n" +
                "1,24933-175,\"Rua 115, 23, Casa 1\",Maricá,Jardim Atlântico\n" +
                "2,24933-075,\"Rua Brizola, 10\",Maricá,Itaipuaçu\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "teste.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        List<EncomendaDTO> lista = parserService.parse(file);
        assertEquals(2, lista.size());
        assertEquals("1", lista.get(0).getCodigo());
        assertEquals("24933-175", lista.get(0).getCep());
        assertEquals("Rua 115", lista.get(0).getLogradouro());
        assertEquals("23", lista.get(0).getNumero());
        assertEquals("Casa 1", lista.get(0).getComplemento());
        assertEquals("Maricá", lista.get(0).getCidade());
    }

    @Test
    void deveParsearCsvComPontoEVirgula() {
        String csv = "Codigo;CEP;Logradouro;Numero;Bairro;Cidade;UF\n" +
                "ENC001;24933-175;Rua das Flores;100;Itaipuaçu;Maricá;RJ\n" +
                "ENC002;24933-180;Avenida Central;S/N;Centro;Maricá;RJ\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "romaneio.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        List<EncomendaDTO> lista = parserService.parse(file);
        assertEquals(2, lista.size());
        assertEquals("ENC001", lista.get(0).getCodigo());
        assertEquals("Rua das Flores", lista.get(0).getLogradouro());
        assertEquals("100", lista.get(0).getNumero());
    }

    @Test
    void deveParsearExcelXlsx() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Entregas");
            Row h = sheet.createRow(0);
            h.createCell(0).setCellValue("Sequence");
            h.createCell(1).setCellValue("Zipcode");
            h.createCell(2).setCellValue("Destination Address");
            h.createCell(3).setCellValue("City");
            h.createCell(4).setCellValue("Neighborhood");
            h.createCell(5).setCellValue("Latitude");

            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("101");
            r1.createCell(1).setCellValue("24933-175");
            r1.createCell(2).setCellValue("Rua Dois, 349");
            r1.createCell(3).setCellValue("Maricá");
            r1.createCell(4).setCellValue("Itaipuaçu");
            r1.createCell(5).setCellValue("-229.674.416");

            wb.write(baos);
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "teste.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", baos.toByteArray());

        List<EncomendaDTO> lista = parserService.parse(file);
        assertEquals(1, lista.size());
        assertEquals("101", lista.get(0).getCodigo());
        assertEquals("Rua Dois", lista.get(0).getLogradouro());
        assertEquals("349", lista.get(0).getNumero());
        assertNotNull(lista.get(0).getLatitude());
        assertEquals(-22.9674416, lista.get(0).getLatitude(), 0.00001);
    }

    @Test
    void deveLancarExcecaoParaArquivoVazioOuInvalido() {
        MockMultipartFile fileVazio = new MockMultipartFile("file", "teste.csv", "text/csv", new byte[0]);
        assertThrows(PlanilhaInvalidaException.class, () -> parserService.parse(fileVazio));

        MockMultipartFile fileExtensaoInvalida = new MockMultipartFile("file", "teste.pdf", "application/pdf", "dummy".getBytes());
        assertThrows(PlanilhaInvalidaException.class, () -> parserService.parse(fileExtensaoInvalida));
    }
}
