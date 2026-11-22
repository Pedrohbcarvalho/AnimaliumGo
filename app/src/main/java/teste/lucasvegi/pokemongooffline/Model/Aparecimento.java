package teste.lucasvegi.pokemongooffline.Model;

import java.io.Serializable;

/**
 * Created by Lucas on 08/12/2016.
 */
public class Aparecimento implements Serializable {
    private double latitude;
    private double longitude;
    private Pokemon pokemon; // legado: só a troca Bluetooth ainda usa
    private Animal animal;   // animal real sorteado pelo backend (spawn do mapa)

    public Animal getAnimal() {
        return animal;
    }

    public void setAnimal(Animal animal) {
        this.animal = animal;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public Pokemon getPokemon() {
        return pokemon;
    }

    public void setPokemon(Pokemon pokemon) {
        this.pokemon = pokemon;
    }
}
