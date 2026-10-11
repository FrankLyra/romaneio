package br.com.romaneio.model;

import java.util.ArrayList;
import java.util.List;

public class ResumoRuaDTO {
    private String nomeRua;
    private String bairro;
    private String cep;
    private int totalEntregas;
    private int totalEnderecosDistintos;
    private List<ResumoEnderecoDTO> enderecos = new ArrayList<>();

    public ResumoRuaDTO() {}

    public ResumoRuaDTO(String nomeRua, String bairro, String cep,
                         int totalEntregas, int totalEnderecosDistintos,
                         List<ResumoEnderecoDTO> enderecos) {
        this.nomeRua = nomeRua;
        this.bairro = bairro;
        this.cep = cep;
        this.totalEntregas = totalEntregas;
        this.totalEnderecosDistintos = totalEnderecosDistintos;
        this.enderecos = enderecos != null ? enderecos : new ArrayList<>();
    }

    public String getNomeRua() {
        return nomeRua;
    }

    public void setNomeRua(String nomeRua) {
        this.nomeRua = nomeRua;
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

    public int getTotalEntregas() {
        return totalEntregas;
    }

    public void setTotalEntregas(int totalEntregas) {
        this.totalEntregas = totalEntregas;
    }

    public int getTotalEnderecosDistintos() {
        return totalEnderecosDistintos;
    }

    public void setTotalEnderecosDistintos(int totalEnderecosDistintos) {
        this.totalEnderecosDistintos = totalEnderecosDistintos;
    }

    public List<ResumoEnderecoDTO> getEnderecos() {
        return enderecos;
    }

    public void setEnderecos(List<ResumoEnderecoDTO> enderecos) {
        this.enderecos = enderecos;
    }
}
