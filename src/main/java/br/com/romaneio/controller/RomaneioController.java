package br.com.romaneio.controller;

import br.com.romaneio.model.AnaliseRomaneioResponseDTO;
import br.com.romaneio.model.ImportacaoResponseDTO;
import br.com.romaneio.model.RotaResponseDTO;
import br.com.romaneio.service.RomaneioService;
import br.com.romaneio.util.BaseFixaConstants;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/romaneios")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class RomaneioController {

    private final RomaneioService romaneioService;

    public RomaneioController(RomaneioService romaneioService) {
        this.romaneioService = romaneioService;
    }

    /**
     * Endpoint principal de importação de planilha de encomendas (.xlsx ou .csv).
     * Endpoint legado que retorna o resumo das rotas ordenadas por quantidade de pontos.
     */
    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<RotaResponseDTO>> importarPlanilha(
            @RequestParam("file") MultipartFile file) {
        ImportacaoResponseDTO resultado = romaneioService.processarPlanilha(file);
        return ResponseEntity.ok(resultado.getRotas());
    }

    /**
     * Lê a planilha e classifica até dez rotas por pontos únicos nas áreas prioritárias, sem geolocalizar.
     */
    @PostMapping(value = "/processar-completo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportacaoResponseDTO> processarPlanilhaCompleta(
            @RequestParam("file") MultipartFile file) {
        ImportacaoResponseDTO resultado = romaneioService.prepararPlanilha(file);
        return ResponseEntity.ok(resultado);
    }

    /**
     * Endpoint analítico que separa as rotas por letras, consolida as entregas por rua
     * e identifica a quantidade de pacotes para o mesmo endereço.
     */
    @PostMapping(value = "/analise-agrupamento", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnaliseRomaneioResponseDTO> analisarAgrupamento(
            @RequestParam("file") MultipartFile file) {
        AnaliseRomaneioResponseDTO resultado = romaneioService.analisarPlanilhaAgrupamentos(file);
        return ResponseEntity.ok(resultado);
    }

    /**
     * Retorna os dados da Origem Fixa (Base de Retirada Itaipuaçu).
     */
    @GetMapping("/base-fixa")
    public ResponseEntity<Map<String, Object>> obterBaseFixa() {
        Map<String, Object> base = Map.of(
                "nome", BaseFixaConstants.NOME,
                "enderecoCompleto", BaseFixaConstants.ENDERECO_COMPLETO,
                "logradouro", BaseFixaConstants.LOGRADOURO,
                "numero", BaseFixaConstants.NUMERO,
                "bairro", BaseFixaConstants.BAIRRO,
                "cidade", BaseFixaConstants.CIDADE,
                "uf", BaseFixaConstants.UF,
                "cep", BaseFixaConstants.CEP
        );
        return ResponseEntity.ok(base);
    }
}
