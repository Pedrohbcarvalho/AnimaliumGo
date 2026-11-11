package teste.lucasvegi.pokemongooffline.Model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

// Classe de dados pura: espelha a tabela AnimalUsuario (uma captura específica).
// Resposta de POST /api/capturas.
public class AnimalCapturado implements Serializable {

    @SerializedName("usuarioId")
    private String usuarioId;

    @SerializedName("idAnimal")
    private int idAnimal;

    @SerializedName("dtCaptura")
    private String dtCaptura; // ISO 8601, gerada pelo servidor

    @SerializedName("latitude")
    private double latitude;

    @SerializedName("longitude")
    private double longitude;

    @SerializedName("estaBloqueado")
    private boolean estaBloqueado;

    // Espécie capturada (presente quando o backend faz include: { animal: true })
    @SerializedName("animal")
    private Animal animal;

    public AnimalCapturado() {
    }

    public AnimalCapturado(String usuarioId, int idAnimal, String dtCaptura,
                           double latitude, double longitude, boolean estaBloqueado, Animal animal) {
        this.usuarioId = usuarioId;
        this.idAnimal = idAnimal;
        this.dtCaptura = dtCaptura;
        this.latitude = latitude;
        this.longitude = longitude;
        this.estaBloqueado = estaBloqueado;
        this.animal = animal;
    }

    // Getters
    public String getUsuarioId() { return usuarioId; }
    public int getIdAnimal() { return idAnimal; }
    public String getDtCaptura() { return dtCaptura; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public boolean isEstaBloqueado() { return estaBloqueado; }
    public Animal getAnimal() { return animal; }

    // Setters
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }
    public void setIdAnimal(int idAnimal) { this.idAnimal = idAnimal; }
    public void setDtCaptura(String dtCaptura) { this.dtCaptura = dtCaptura; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public void setEstaBloqueado(boolean estaBloqueado) { this.estaBloqueado = estaBloqueado; }
    public void setAnimal(Animal animal) { this.animal = animal; }
}
