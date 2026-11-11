package teste.lucasvegi.pokemongooffline.Model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

// Classe de dados pura: espelha o JSON de GET /api/vgi
public class ReportVgi implements Serializable {

    @SerializedName("idReport")
    private int idReport;

    @SerializedName("idAnimalSugerido")
    private int idAnimalSugerido;

    @SerializedName("fotoReal")
    private String fotoReal;

    @SerializedName("latitude")
    private double latitude;

    @SerializedName("longitude")
    private double longitude;

    @SerializedName("status")
    private String status; // "Pendente" | "Aprovado" | "Rejeitado"

    @SerializedName("dtReport")
    private String dtReport; // ISO 8601

    @SerializedName("animal")
    private Animal animal;

    public ReportVgi() {
    }

    public int getIdReport() { return idReport; }
    public int getIdAnimalSugerido() { return idAnimalSugerido; }
    public String getFotoReal() { return fotoReal; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getStatus() { return status; }
    public String getDtReport() { return dtReport; }
    public Animal getAnimal() { return animal; }
}
