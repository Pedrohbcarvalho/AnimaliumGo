package teste.lucasvegi.pokemongooffline.Model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

// Classe de dados pura: espelha o JSON de GET /api/usuarios/me
// (e o objeto "user" de POST /api/auth/login).
public class PerfilUsuario implements Serializable {

    @SerializedName("id")
    private String id; // UUID do Supabase Auth

    @SerializedName("nome")
    private String nome;

    @SerializedName("sexo")
    private String sexo; // "M" | "F"

    @SerializedName("foto")
    private String foto; // pode ser null

    @SerializedName("email")
    private String email; // vem do Supabase Auth, não da tabela Usuario

    @SerializedName("dtCadastro")
    private String dtCadastro; // ISO 8601

    @SerializedName("temSessao")
    private boolean temSessao;

    @SerializedName("nivel")
    private int nivel;

    @SerializedName("xp")
    private int xp;

    @SerializedName("isAdmin")
    private boolean admin;

    public PerfilUsuario() {
    }

    public PerfilUsuario(String id, String nome, String sexo, String foto, String email,
                         String dtCadastro, boolean temSessao, int nivel, int xp, boolean admin) {
        this.id = id;
        this.nome = nome;
        this.sexo = sexo;
        this.foto = foto;
        this.email = email;
        this.dtCadastro = dtCadastro;
        this.temSessao = temSessao;
        this.nivel = nivel;
        this.xp = xp;
        this.admin = admin;
    }

    // Getters
    public String getId() { return id; }
    public String getNome() { return nome; }
    public String getSexo() { return sexo; }
    public String getFoto() { return foto; }
    public String getEmail() { return email; }
    public String getDtCadastro() { return dtCadastro; }
    public boolean isTemSessao() { return temSessao; }
    public int getNivel() { return nivel; }
    public int getXp() { return xp; }
    public boolean isAdmin() { return admin; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setNome(String nome) { this.nome = nome; }
    public void setSexo(String sexo) { this.sexo = sexo; }
    public void setFoto(String foto) { this.foto = foto; }
    public void setEmail(String email) { this.email = email; }
    public void setDtCadastro(String dtCadastro) { this.dtCadastro = dtCadastro; }
    public void setTemSessao(boolean temSessao) { this.temSessao = temSessao; }
    public void setNivel(int nivel) { this.nivel = nivel; }
    public void setXp(int xp) { this.xp = xp; }
    public void setAdmin(boolean admin) { this.admin = admin; }
}
