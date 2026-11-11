package teste.lucasvegi.pokemongooffline.Model; // Ou o pacote onde você decidir guardar

import java.util.List;

import com.google.gson.JsonObject;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Body;

public interface BackendApi {

    // Retorna uma lista pronta de objetos Animal em Java
    @GET("api/animais")
    Call<List<Animal>> getAnimais();

    // Dados de um animal específico (inclui retângulos de spawn)
    @GET("api/animais/{id}")
    Call<Animal> getAnimal(@Path("id") int idAnimal);

    @POST("api/auth/login")
    Call<JsonObject> fazerLogin(@Body JsonObject loginData);

    // Perfil do usuário logado (id, nome, sexo, foto, nivel, xp, isAdmin, email...)
    @GET("api/usuarios/me")
    Call<JsonObject> getMeuPerfil();

    // Atualiza o progresso do jogador. Body: {"xp": int, "nivel": int (1-40)}
    @PATCH("api/usuarios/me/progresso")
    Call<JsonObject> atualizarProgresso(@Body JsonObject progresso);

    // Registra uma captura. Body: {"idAnimal": int, "latitude": double, "longitude": double}
    @POST("api/capturas")
    Call<JsonObject> registrarCaptura(@Body JsonObject captura);

    // Faz a ponte para a submissão de relatórios do VGI
    // @Multipart é usado porque vamos enviar a foto como um arquivo binário,
    // exatamente da mesma forma que o Express recebe via 'multer' no backend.
    @Multipart
    @POST("api/vgi")
    Call<JsonObject> enviarAvistamento(
            @Part MultipartBody.Part arquivo,
            @Part("idAnimalSugerido") RequestBody idAnimalSugerido,
            @Part("latitude") RequestBody latitude,
            @Part("longitude") RequestBody longitude
    );

    // Lista os relatos VGI (mais recentes primeiro)
    @GET("api/vgi")
    Call<List<ReportVgi>> getRelatosVgi();

    // Eventos com dataInicio <= agora <= dataFim
    @GET("api/eventos/ativos")
    Call<List<Evento>> getEventosAtivos();
}
