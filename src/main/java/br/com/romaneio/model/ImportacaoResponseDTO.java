package br.com.romaneio.model;

import java.util.ArrayList;
import java.util.List;

public class ImportacaoResponseDTO {
    private boolean sucesso;
    private String mensagem;
    private int totalLinhasArquivo;
    private int totalEncomendasValidas;
    private int totalParadasUnicas;
    private String idImportacao;
    private List<String> avisos = new ArrayList<>();
    private List<RotaResponseDTO> rotas = new ArrayList<>();

    public ImportacaoResponseDTO() {}

    public ImportacaoResponseDTO(boolean sucesso, String mensagem) {
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

    public int getTotalLinhasArquivo() {
        return totalLinhasArquivo;
    }

    public void setTotalLinhasArquivo(int totalLinhasArquivo) {
        this.totalLinhasArquivo = totalLinhasArquivo;
    }

    public int getTotalEncomendasValidas() {
        return totalEncomendasValidas;
    }

    public void setTotalEncomendasValidas(int totalEncomendasValidas) {
        this.totalEncomendasValidas = totalEncomendasValidas;
    }

    public int getTotalParadasUnicas() {
        return totalParadasUnicas;
    }

    public void setTotalParadasUnicas(int totalParadasUnicas) {
        this.totalParadasUnicas = totalParadasUnicas;
    }

    public String getIdImportacao() { return idImportacao; }

    public void setIdImportacao(String idImportacao) { this.idImportacao = idImportacao; }

    public List<String> getAvisos() {
        return avisos;
    }

    public void setAvisos(List<String> avisos) {
        this.avisos = avisos;
    }

    public List<RotaResponseDTO> getRotas() {
        return rotas;
    }

    public void setRotas(List<RotaResponseDTO> rotas) {
        this.rotas = rotas;
    }
}
