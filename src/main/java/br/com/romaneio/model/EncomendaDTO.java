package br.com.romaneio.model;

import java.util.Objects;

public class EncomendaDTO {
    private String codigo;
    private String sequence;
    private String cep;
    private String logradouro;
    private String numero;
    private String complemento;
    private String bairro;
    private String cidade;
    private String uf;
    private String corredor;
    private Double latitude;
    private Double longitude;
    private String enderecoCompleto;

    public EncomendaDTO() {}

    public EncomendaDTO(String codigo, String cep, String logradouro, String numero,
                        String complemento, String bairro, String cidade, String uf) {
        this.codigo = codigo;
        setCep(cep);
        this.logradouro = logradouro;
        this.numero = numero;
        this.complemento = complemento;
        this.bairro = bairro;
        this.cidade = cidade;
        this.uf = uf;
        this.enderecoCompleto = montarEnderecoCompleto();
    }

    public String montarEnderecoCompleto() {
        StringBuilder sb = new StringBuilder();
        if (logradouro != null && !logradouro.isBlank()) {
            sb.append(logradouro.trim());
        }
        if (numero != null && !numero.isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(numero.trim());
        }
        if (complemento != null && !complemento.isBlank()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(complemento.trim());
        }
        if (bairro != null && !bairro.isBlank()) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(bairro.trim());
        }
        if (cidade != null && !cidade.isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(cidade.trim());
        }
        if (uf != null && !uf.isBlank()) {
            sb.append(" - ").append(uf.trim());
        }
        if (cep != null && !cep.isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(cep.trim());
        }
        return sb.toString();
    }

    public String getChaveLocalizacao() {
        // Chave para agrupar encomendas no mesmo ponto de entrega / parada
        StringBuilder sb = new StringBuilder();
        if (cep != null && !cep.isBlank()) {
            sb.append(cep.replaceAll("[^0-9]", "")).append("|");
        }
        if (logradouro != null) {
            sb.append(logradouro.trim().toLowerCase()).append("|");
        }
        if (numero != null) {
            sb.append(numero.trim().toLowerCase()).append("|");
        }
        if (bairro != null) {
            sb.append(bairro.trim().toLowerCase());
        }
        return sb.toString();
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getSequence() {
        return sequence;
    }

    public void setSequence(String sequence) {
        this.sequence = sequence;
    }

    public String getCep() {
        return cep;
    }

    public void setCep(String cep) {
        if (cep != null && !cep.isBlank()) {
            String digits = cep.replaceAll("[^0-9]", "");
            if (digits.length() == 8) {
                this.cep = digits.substring(0, 5) + "-" + digits.substring(5);
            } else {
                this.cep = cep.trim();
            }
        } else {
            this.cep = null;
        }
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

    public String getCorredor() {
        return corredor;
    }

    public void setCorredor(String corredor) {
        this.corredor = corredor;
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

    public String getEnderecoCompleto() {
        if (enderecoCompleto == null || enderecoCompleto.isBlank()) {
            enderecoCompleto = montarEnderecoCompleto();
        }
        return enderecoCompleto;
    }

    public void setEnderecoCompleto(String enderecoCompleto) {
        this.enderecoCompleto = enderecoCompleto;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EncomendaDTO that = (EncomendaDTO) o;
        return Objects.equals(codigo, that.codigo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(codigo);
    }

    @Override
    public String toString() {
        return "EncomendaDTO{" +
                "codigo='" + codigo + '\'' +
                ", cep='" + cep + '\'' +
                ", logradouro='" + logradouro + '\'' +
                ", numero='" + numero + '\'' +
                ", bairro='" + bairro + '\'' +
                ", cidade='" + cidade + '\'' +
                '}';
    }
}
