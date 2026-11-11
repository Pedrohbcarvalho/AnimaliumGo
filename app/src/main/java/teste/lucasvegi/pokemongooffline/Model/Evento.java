package teste.lucasvegi.pokemongooffline.Model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.List;

// Classe de dados pura: espelha o JSON de GET /api/eventos/ativos
public class Evento implements Serializable {

    @SerializedName("idEvento")
    private int idEvento;

    @SerializedName("nome")
    private String nome;

    @SerializedName("dataInicio")
    private String dataInicio; // ISO 8601

    @SerializedName("dataFim")
    private String dataFim; // ISO 8601

    @SerializedName("animais")
    private List<EventoAnimal> animais;

    @SerializedName("retangulos")
    private List<Retangulo> retangulos;

    public Evento() {
    }

    public int getIdEvento() { return idEvento; }
    public String getNome() { return nome; }
    public String getDataInicio() { return dataInicio; }
    public String getDataFim() { return dataFim; }
    public List<EventoAnimal> getAnimais() { return animais; }
    public List<Retangulo> getRetangulos() { return retangulos; }

    public static class EventoAnimal implements Serializable {
        @SerializedName("idAnimal")
        private int idAnimal;

        @SerializedName("taxaAparicao")
        private double taxaAparicao;

        @SerializedName("animal")
        private Animal animal;

        public int getIdAnimal() { return idAnimal; }
        public double getTaxaAparicao() { return taxaAparicao; }
        public Animal getAnimal() { return animal; }
    }

    public static class Retangulo implements Serializable {
        @SerializedName("lat_sw")
        private double latSw;

        @SerializedName("lng_sw")
        private double lngSw;

        @SerializedName("lat_ne")
        private double latNe;

        @SerializedName("lng_ne")
        private double lngNe;

        public double getLatSw() { return latSw; }
        public double getLngSw() { return lngSw; }
        public double getLatNe() { return latNe; }
        public double getLngNe() { return lngNe; }
    }
}
