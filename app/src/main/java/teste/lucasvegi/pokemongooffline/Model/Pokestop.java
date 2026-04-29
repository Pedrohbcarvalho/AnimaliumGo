package teste.lucasvegi.pokemongooffline.Model;

import android.graphics.Bitmap;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.Serializable;
import java.util.Date;

import teste.lucasvegi.pokemongooffline.R;

/**
 * Created by Lucas on 02/12/2016.
 */
public class Pokestop implements Serializable{
    private String id;
    private String nome;
    private transient Bitmap foto = null;
    private Double lat;
    private Double longi;
    private String descri;
    private Date acesso;
    private boolean disponivel;

    public Pokestop(){

    }

    public Pokestop(String ID, String Name) {
        this.id = ID;
        this.nome = Name;
        //this.foto = Photo;
        this.acesso = null;
        this.disponivel = true;
    }

    public String getID() {
        return id;
    }

    public void setID(String id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getDescri() {
        return descri;
    }

    public void setDescri(String descri) {
        this.descri = descri;
    }

    public double getlat() {
        return lat;
    }

    public void setlat(double lat) {
        this.lat = lat;
    }

    public double getlongi() {
        return longi;
    }

    public void setlong(double longi) {
        this.longi = longi;
    }

    public Bitmap getFoto() {
        return foto;
    }

    public void setFoto(Bitmap foto) {
        this.foto = foto;
    }

    public Date getUltimoAcesso() {
        return acesso;
    }

    public void setUltimoAcesso(Date tempo) {
        this.acesso = tempo;
    }

    public boolean getDisponivel() {
        return disponivel;
    }

    public void setDisponivel(boolean disp) {
        this.disponivel = disp;
    }

}
