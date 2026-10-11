package br.com.romaneio.model;

import java.util.ArrayList;
import java.util.List;

public class ResumoEnderecoDTO {
    private String enderecoCompleto;
    private String logradouro;
    private String numero;
    private String complemento;
    private String bairro;
    private String cep;
    private Double latitude;
    private Double longitude;
    private int quantidadeEntregas;
    private List<String> codigosRastreio = new ArrayList<>();

    public ResumoEnderecoDTO() {}

    public ResumoEnderecoDTO(String enderecoCompleto, String logradouro, String numero,
                             String complemento, String bairro, String cep,
                             Double latitude, Double longitude, int quantidadeEntregas,
                             List<String> codigosRastreio) {
        this.enderecoCompleto = enderecoCompleto;
        this.logradouro = logradouro;
        this.numero = numero;
        this.complemento = complemento;
        this.bairro = bairro;
        this.cep = cep;
        this.latitude = latitude;
        this.longitude = longitude;
        this.quantidadeEntregas = quantidadeEntregas;
        this.codigosRastreio = codigosRastreio != null ? codigosRastreio : new ArrayList<>();
    }

    public String getEnderecoCompleto() {
        return enderecoCompleto;
    }

    public void setEnderecoCompleto(String enderecoCompleto) {
        this.enderecoCompleto = enderecoCompleto;
    }

    public String getLogradouro() {
        return logradouro;
    }

    public void setLogradouro(String logradouro) {
        this.logradouro = logradouro;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public String getComplemento() {
        return complemento;
    }

    public void setComplemento(String complemento) {
        this.complemento = complemento;
    }

    public String getBairro() {
        return bairro;
    }

    public void setBairro(String bairro) {
        this.bairro = bairro;
    }

    public String getCep() {
        return cep;
    }

    public void setCep(String cep) {
        this.cep = cep;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public int getQuantidadeEntregas() {
        return quantidadeEntregas;
    }

    public void setQuantidadeEntregas(int quantidadeEntregas) {
        this.quantidadeEntregas = quantidadeEntregas;
    }

    public List<String> getCodigosRastreio() {
        return codigosRastreio;
    }

    public void setCodigosRastreio(List<String> codigosRastreio) {
        this.codigosRastreio = codigosRastreio;
    }
}
