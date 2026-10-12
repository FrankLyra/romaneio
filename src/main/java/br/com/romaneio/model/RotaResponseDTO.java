package br.com.romaneio.model;

import java.util.ArrayList;
import java.util.List;

public class RotaResponseDTO {
    private int id;
    private String letra;
    private String identificador;
    private String nome;
    private String descricao;
    private boolean principal;
    private Double distanciaTotalKm;
    private String distanciaTotalFormatada;
    private int tempoEstimadoMinutos;
    private String tempoEstimadoFormatado;
    private int quantidadeParadas;
    private int quantidadeEncomendas;
    private List<ParadaDTO> paradas = new ArrayList<>();

    public RotaResponseDTO() {}

    public RotaResponseDTO(int id, String nome, String descricao, boolean principal) {
        this.id = id;
        this.nome = nome;
        this.descricao = descricao;
        this.principal = principal;
    }

    public RotaResponseDTO(int id, String letra, String nome, String descricao, boolean principal) {
        this.id = id;
        this.letra = letra;
        this.nome = nome;
        this.descricao = descricao;
        this.principal = principal;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getLetra() {
        return letra;
    }

    public void setLetra(String letra) {
        this.letra = letra;
    }

    public String getIdentificador() { return identificador; }

    public void setIdentificador(String identificador) { this.identificador = identificador; }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public boolean isPrincipal() {
        return principal;
    }

    public void setPrincipal(boolean principal) {
        this.principal = principal;
    }

    public Double getDistanciaTotalKm() {
        return distanciaTotalKm;
    }

    public void setDistanciaTotalKm(Double distanciaTotalKm) {
        this.distanciaTotalKm = distanciaTotalKm;
    }

    public String getDistanciaTotalFormatada() {
        return distanciaTotalFormatada;
    }

    public void setDistanciaTotalFormatada(String distanciaTotalFormatada) {
        this.distanciaTotalFormatada = distanciaTotalFormatada;
    }

    public int getTempoEstimadoMinutos() {
        return tempoEstimadoMinutos;
    }

    public void setTempoEstimadoMinutos(int tempoEstimadoMinutos) {
        this.tempoEstimadoMinutos = tempoEstimadoMinutos;
    }

    public String getTempoEstimadoFormatado() {
        return tempoEstimadoFormatado;
    }

    public void setTempoEstimadoFormatado(String tempoEstimadoFormatado) {
        this.tempoEstimadoFormatado = tempoEstimadoFormatado;
    }

    public int getQuantidadeParadas() {
        return quantidadeParadas;
    }

    public void setQuantidadeParadas(int quantidadeParadas) {
        this.quantidadeParadas = quantidadeParadas;
    }

    public int getQuantidadeEncomendas() {
        return quantidadeEncomendas;
    }

    public void setQuantidadeEncomendas(int quantidadeEncomendas) {
        this.quantidadeEncomendas = quantidadeEncomendas;
    }

    public List<ParadaDTO> getParadas() {
        return paradas;
    }

    public void setParadas(List<ParadaDTO> paradas) {
        this.paradas = paradas;
    }
}
