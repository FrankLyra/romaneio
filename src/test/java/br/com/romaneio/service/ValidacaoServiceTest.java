package br.com.romaneio.service;

import br.com.romaneio.exception.PlanilhaInvalidaException;
import br.com.romaneio.model.EncomendaDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValidacaoServiceTest {

    private ValidacaoService validacaoService;

    @BeforeEach
    void setUp() {
        validacaoService = new ValidacaoService();
    }

    @Test
    void deveValidarEncomendasComSucesso() {
        List<EncomendaDTO> lista = new ArrayList<>();
        lista.add(new EncomendaDTO("ENC01", "24933-175", "Rua 115", "23", "", "Itaipuaçu", "Maricá", "RJ"));
        lista.add(new EncomendaDTO("ENC02", "24933-180", "Rua 116", "", "", "Itaipuaçu", "Maricá", "RJ"));

        ValidacaoService.ResultadoValidacao res = validacaoService.validarEncomendas(lista);
        assertEquals(2, res.getEncomendasValidas().size());
        assertEquals("S/N", res.getEncomendasValidas().get(1).getNumero());
    }

    @Test
    void deveIdentificarDuplicidadesEAvisar() {
        List<EncomendaDTO> lista = new ArrayList<>();
        lista.add(new EncomendaDTO("ENC01", "24933-175", "Rua 115", "23", "", "Itaipuaçu", "Maricá", "RJ"));
        lista.add(new EncomendaDTO("ENC01", "24933-175", "Rua 115", "23", "Apto 2", "Itaipuaçu", "Maricá", "RJ"));

        ValidacaoService.ResultadoValidacao res = validacaoService.validarEncomendas(lista);
        assertEquals(2, res.getEncomendasValidas().size());
        assertEquals(1, res.getTotalDuplicados());
        assertFalse(res.getAvisos().isEmpty());
    }

    @Test
    void deveIgnorarLinhasSemCodigoOuLogradouro() {
        List<EncomendaDTO> lista = new ArrayList<>();
        lista.add(new EncomendaDTO("", "24933-175", "Rua 115", "23", "", "Itaipuaçu", "Maricá", "RJ"));
        lista.add(new EncomendaDTO("ENC02", "24933-180", "", "10", "", "Itaipuaçu", "Maricá", "RJ"));
        lista.add(new EncomendaDTO("ENC03", "24933-180", "Rua Válida", "10", "", "Itaipuaçu", "Maricá", "RJ"));

        ValidacaoService.ResultadoValidacao res = validacaoService.validarEncomendas(lista);
        assertEquals(1, res.getEncomendasValidas().size());
        assertEquals(2, res.getTotalInvalidos());
        assertEquals("ENC03", res.getEncomendasValidas().get(0).getCodigo());
    }

    @Test
    void deveNormalizarCepEAvisarQuandoForaDoPadrao() {
        List<EncomendaDTO> lista = new ArrayList<>();
        lista.add(new EncomendaDTO("ENC01", "24933175", "Rua 115", "23", "", "Itaipuaçu", "Maricá", "RJ"));
        lista.add(new EncomendaDTO("ENC02", "24933", "Rua 116", "10", "", "Itaipuaçu", "Maricá", "RJ"));

        ValidacaoService.ResultadoValidacao res = validacaoService.validarEncomendas(lista);
        assertEquals(2, res.getEncomendasValidas().size());
        assertEquals("24933-175", res.getEncomendasValidas().get(0).getCep());
        assertFalse(res.getAvisos().isEmpty());
    }

    @Test
    void deveLancarExcecaoSeListaVaziaOuSemNenhumaValida() {
        assertThrows(PlanilhaInvalidaException.class, () -> validacaoService.validarEncomendas(Collections.emptyList()));

        List<EncomendaDTO> invalidas = List.of(new EncomendaDTO("", "", "", "", "", "", "", ""));
        assertThrows(PlanilhaInvalidaException.class, () -> validacaoService.validarEncomendas(invalidas));
    }
}
