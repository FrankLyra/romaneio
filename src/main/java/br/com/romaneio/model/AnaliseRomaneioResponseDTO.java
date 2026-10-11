package br.com.romaneio.model;

import java.util.ArrayList;
import java.util.List;

public class AnaliseRomaneioResponseDTO {
    private boolean sucesso;
    private String mensagem;
    private int totalLinhasLidas;
    private int totalEncomendasValidas;
    private int totalRotasIdentificadas;
    private int totalRuasDistintas;
    private int totalParadasUnicas;
    private List<String> avisos = new ArrayList<>();
    private List<ResumoRotaLetraDTO> rotasPorLetra = new ArrayList<>();

    public AnaliseRomaneioResponseDTO() {}

    public AnaliseRomaneioResponseDTO(boolean sucesso, String mensagem) {
        this.sucesso = sucesso;
        this.mensagem = mensagem;
    }

    public boolean isSucesso() {
        return sucesso;
    }

    public void setSucesso(boolean sucesso) {
        this.sucesso = sucesso;
    }

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }

    public int getTotalLinhasLidas() {
        return totalLinhasLidas;
    }

    public void setTotalLinhasLidas(int totalLinhasLidas) {
        this.totalLinhasLidas = totalLinhasLidas;
    }

    public int getTotalEncomendasValidas() {
        return totalEncomendasValidas;
    }

    public void setTotalEncomendasValidas(int totalEncomendasValidas) {
        this.totalEncomendasValidas = totalEncomendasValidas;
    }

    public int getTotalRotasIdentificadas() {
        return totalRotasIdentificadas;
    }

    public void setTotalRotasIdentificadas(int totalRotasIdentificadas) {
        this.totalRotasIdentificadas = totalRotasIdentificadas;
    }

    public int getTotalRuasDistintas() {
        return totalRuasDistintas;
    }

    public void setTotalRuasDistintas(int totalRuasDistintas) {
        this.totalRuasDistintas = totalRuasDistintas;
    }

    public int getTotalParadasUnicas() {
        return totalParadasUnicas;
    }

    public void setTotalParadasUnicas(int totalParadasUnicas) {
        this.totalParadasUnicas = totalParadasUnicas;
    }

    public List<String> getAvisos() {
        return avisos;
    }

    public void setAvisos(List<String> avisos) {
        this.avisos = avisos;
    }

    public List<ResumoRotaLetraDTO> getRotasPorLetra() {
        return rotasPorLetra;
    }

    public void setRotasPorLetra(List<ResumoRotaLetraDTO> rotasPorLetra) {
        this.rotasPorLetra = rotasPorLetra;
    }
}
