package teste.lucasvegi.pokemongooffline.Model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class Animal implements Serializable {

    @SerializedName("id")
    private int id;

    @SerializedName("nomePopular")
    private String nomePopular;

    @SerializedName("nomeCientifico")
    private String nomeCientifico;

    @SerializedName("classe")
    private String classeAnimal;

    @SerializedName("subordem")
    private String subordem;

    @SerializedName("familia")
    private String familia;

    @SerializedName("tamanhoMedio")
    private String tamanhoMedio;

    @SerializedName("pesoMedio")
    private String pesoMedio;

    @SerializedName("dieta")
    private String dieta;

    @SerializedName("habito")
    private String habito;

    @SerializedName("habitat")
    private String habitat;

    @SerializedName("statusIUCN")
    private String statusIUCN;

    @SerializedName("periculosidade")
    private String periculosidade;

    @SerializedName("foto")
    private String fotoUrl;

    public Animal() {
    }

    // Getters
    public int getId() { return id; }
    public String getNomePopular() { return nomePopular; }
    public String getNomeCientifico() { return nomeCientifico; }
    public String getClasseAnimal() { return classeAnimal; }
    public String getSubordem() { return subordem; }
    public String getFamilia() { return familia; }
    public String getTamanhoMedio() { return tamanhoMedio; }
    public String getPesoMedio() { return pesoMedio; }
    public String getDieta() { return dieta; }
    public String getHabito() { return habito; }
    public String getHabitat() { return habitat; }
    public String getStatusIUCN() { return statusIUCN; }
    public String getPericulosidade() { return periculosidade; }
    public String getFotoUrl() { return fotoUrl; }


    public void setId(int id) { this.id = id; }
    public void setNomePopular(String nomePopular) { this.nomePopular = nomePopular; }
    public void setNomeCientifico(String nomeCientifico) { this.nomeCientifico = nomeCientifico; }
    public void setClasseAnimal(String classeAnimal) { this.classeAnimal = classeAnimal; }
    public void setSubordem(String subordem) { this.subordem = subordem; }
    public void setFamilia(String familia) { this.familia = familia; }
    public void setTamanhoMedio(String tamanhoMedio) { this.tamanhoMedio = tamanhoMedio; }
    public void setPesoMedio(String pesoMedio) { this.pesoMedio = pesoMedio; }
    public void setDieta(String dieta) { this.dieta = dieta; }
    public void setHabito(String habito) { this.habito = habito; }
    public void setHabitat(String habitat) { this.habitat = habitat; }
    public void setStatusIUCN(String statusIUCN) { this.statusIUCN = statusIUCN; }
    public void setPericulosidade(String periculosidade) { this.periculosidade = periculosidade; }
    public void setFotoUrl(String fotoUrl) { this.fotoUrl = fotoUrl; }
}