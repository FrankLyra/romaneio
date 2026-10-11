package br.com.romaneio.model;

import java.util.ArrayList;
import java.util.List;

public class ResumoRotaLetraDTO {
    private String letraRota;
    private int totalEncomendas;
    private int totalRuasDistintas;
    private int totalParadasUnicas;
    private List<ResumoRuaDTO> ruas = new ArrayList<>();

    public ResumoRotaLetraDTO() {}

    public ResumoRotaLetraDTO(String letraRota, int totalEncomendas,
                              int totalRuasDistintas, int totalParadasUnicas,
                              List<ResumoRuaDTO> ruas) {
        this.letraRota = letraRota;
        this.totalEncomendas = totalEncomendas;
        this.totalRuasDistintas = totalRuasDistintas;
        this.totalParadasUnicas = totalParadasUnicas;
        this.ruas = ruas != null ? ruas : new ArrayList<>();
    }

    public String getLetraRota() {
        return letraRota;
    }

    public void setLetraRota(String letraRota) {
        this.letraRota = letraRota;
    }

    public int getTotalEncomendas() {
        return totalEncomendas;
    }

    public void setTotalEncomendas(int totalEncomendas) {
        this.totalEncomendas = totalEncomendas;
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

    public List<ResumoRuaDTO> getRuas() {
        return ruas;
    }

    public void setRuas(List<ResumoRuaDTO> ruas) {
        this.ruas = ruas;
    }
}
