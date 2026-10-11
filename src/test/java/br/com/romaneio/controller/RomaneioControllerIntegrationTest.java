package br.com.romaneio.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RomaneioControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveRetornarDadosDaBaseFixa() throws Exception {
        mockMvc.perform(get("/api/romaneios/base-fixa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome", containsString("Itaipuaçu")))
                .andExpect(jsonPath("$.cidade", is("Maricá")))
                .andExpect(jsonPath("$.uf", is("RJ")))
                .andExpect(jsonPath("$.cep", is("24942-395")))
                .andExpect(jsonPath("$.latitude", notNullValue()))
                .andExpect(jsonPath("$.longitude", notNullValue()));
    }

    @Test
    void deveImportarPlanilhaCsvERetornarCincoRotas() throws Exception {
        String csvContent = "Sequence,Zipcode,Destination Address,City,Neighborhood\n" +
                "1,24933-175,\"Rua José G Pedroso, 23\",Maricá,Jardim Atlântico Leste\n" +
                "2,24933-075,\"Rua Leonel Brizola, S/N\",Maricá,Itaipuaçu\n" +
                "3,24933-180,\"Rua Carlos Alberto, 15\",Maricá,Itaipuaçu\n" +
                "4,24933-085,\"Av Antonio Marques, 800\",Maricá,Itaipuaçu\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "romaneio.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/romaneios/importar").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].principal", is(true)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].distanciaTotalKm", greaterThan(0.0)))
                .andExpect(jsonPath("$[0].paradas", hasSize(5))) // 1 Origem + 4 Entregas
                .andExpect(jsonPath("$[0].paradas[0].tipo", is("ORIGEM")))
                .andExpect(jsonPath("$[0].paradas[1].tipo", is("ENTREGA")));
    }

    @Test
    void deveProcessarPlanilhaCompletaERetornarMetadadosEAvisos() throws Exception {
        String csvContent = "Codigo;CEP;Logradouro;Numero;Bairro;Cidade;UF\n" +
                "ENC01;24933-175;Rua 115;23;Itaipuaçu;Maricá;RJ\n" +
                "ENC01;24933-175;Rua 115;23;Itaipuaçu;Maricá;RJ\n" + // Duplicata proposital
                "ENC02;24933-180;Rua 116;50;Itaipuaçu;Maricá;RJ\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "romaneio.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/romaneios/processar-completo").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sucesso", is(true)))
                .andExpect(jsonPath("$.totalLinhasArquivo", is(3)))
                .andExpect(jsonPath("$.totalEncomendasValidas", is(3)))
                .andExpect(jsonPath("$.totalParadasUnicas", is(2)))
                .andExpect(jsonPath("$.avisos", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.rotas", hasSize(5)));
    }

    @Test
    void deveRetornarErro400AoEnviarArquivoInvalido() throws Exception {
        MockMultipartFile fileInvalido = new MockMultipartFile(
                "file", "documento.pdf", "application/pdf", "conteudo fake".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/romaneios/importar").file(fileInvalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.erro", is("Planilha Inválida")));
    }

    @Test
    void deveImportarArquivoRealExcelComSucessoSePresente() throws Exception {
        File fileReal = new File("data/ROMANEIO MARICA 11_10_2026 AM .xlsx");
        if (!fileReal.exists()) return;

        byte[] bytes;
        try (FileInputStream fis = new FileInputStream(fileReal)) {
            bytes = fis.readAllBytes();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "ROMANEIO MARICA.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes);

        mockMvc.perform(multipart("/api/romaneios/importar").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].principal", is(true)))
                .andExpect(jsonPath("$[0].distanciaTotalKm", greaterThan(0.0)));
    }
}
