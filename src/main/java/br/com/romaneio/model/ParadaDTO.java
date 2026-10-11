package br.com.romaneio.model;

import java.util.ArrayList;
import java.util.List;

public class ParadaDTO {
    private int ordem;
    private String tipo; // "ORIGEM" ou "ENTREGA"
    private String endereco;
    private String logradouro;
    private String numero;
    private String bairro;
    private String cidade;
    private String uf;
    private String cep;
    private Double latitude;
    private Double longitude;
    private Double distanciaDoPontoAnteriorKm;
    private Double distanciaAcumuladaKm;
    private int quantidadeEncomendas;
    private List<EncomendaDTO> encomendas = new ArrayList<>();

    public ParadaDTO() {}

    public ParadaDTO(int ordem, String tipo, String endereco, Double latitude, Double longitude) {
        this.ordem = ordem;
        this.tipo = tipo;
        this.endereco = endereco;
        this.latitude = latitude;
        this.longitude = longitude;
        this.distanciaDoPontoAnteriorKm = 0.0;
        this.distanciaAcumuladaKm = 0.0;
    }

    public void adicionarEncomenda(EncomendaDTO enc) {
        if (enc != null) {
            this.encomendas.add(enc);
            this.quantidadeEncomendas = this.encomendas.size();
        }
    }

    public int getOrdem() {
        return ordem;
    }

    public void setOrdem(int ordem) {
        this.ordem = ordem;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getEndereco() {
        return endereco;
    }

    public void setEndereco(String endereco) {
        this.endereco = endereco;
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

    public String getBairro() {
        return bairro;
    }

    public void setBairro(String bairro) {
        this.bairro = bairro;
    }

    public String getCidade() {
        return cidade;
    }

    public void setCidade(String cidade) {
        this.cidade = cidade;
    }

    public String getUf() {
        return uf;
    }

    public void setUf(String uf) {
        this.uf = uf;
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

    public Double getDistanciaDoPontoAnteriorKm() {
        return distanciaDoPontoAnteriorKm;
    }

    public void setDistanciaDoPontoAnteriorKm(Double distanciaDoPontoAnteriorKm) {
        this.distanciaDoPontoAnteriorKm = distanciaDoPontoAnteriorKm;
    }

    public Double getDistanciaAcumuladaKm() {
        return distanciaAcumuladaKm;
    }

    public void setDistanciaAcumuladaKm(Double distanciaAcumuladaKm) {
        this.distanciaAcumuladaKm = distanciaAcumuladaKm;
    }

    public int getQuantidadeEncomendas() {
        return (encomendas != null) ? encomendas.size() : quantidadeEncomendas;
    }

    public void setQuantidadeEncomendas(int quantidadeEncomendas) {
        this.quantidadeEncomendas = quantidadeEncomendas;
    }

    public List<EncomendaDTO> getEncomendas() {
        return encomendas;
    }

    public void setEncomendas(List<EncomendaDTO> encomendas) {
        this.encomendas = encomendas;
        if (encomendas != null) {
            this.quantidadeEncomendas = encomendas.size();
        }
    }
}
