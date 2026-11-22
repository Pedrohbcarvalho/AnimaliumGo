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
import retrofit2.http.Query;
import retrofit2.http.Body;

public interface BackendApi {

    // Retorna uma lista pronta de objetos Animal em Java
    @GET("api/animais")
    Call<List<Animal>> getAnimais();

    // Dados de um animal específico (inclui retângulos de spawn)
    @GET("api/animais/{id}")
    Call<Animal> getAnimal(@Path("id") int idAnimal);

    // Resposta: {"token", "refreshToken", "expiresAt", "user"}
    @POST("api/auth/login")
    Call<JsonObject> fazerLogin(@Body JsonObject loginData);

    // Body: {"nome", "email", "password", "sexo"}. Depois do cadastro o app faz o login normal.
    @POST("api/auth/register")
    Call<JsonObject> registrar(@Body JsonObject dados);

    // Body: {"refreshToken"}. Resposta: {"token", "refreshToken", "expiresAt"}.
    // 401 = refresh token inválido/revogado (o usuário precisa logar de novo).
    @POST("api/auth/refresh")
    Call<JsonObject> renovarSessao(@Body JsonObject body);

    // Perfil do usuário logado (id, nome, sexo, foto, nivel, xp, isAdmin, email...)
    @GET("api/usuarios/me")
    Call<PerfilUsuario> getMeuPerfil();

    // Informa uma ação que gera XP; o backend decide o valor e recalcula o nível.
    // Body: {"acao": "pokestop" | "choca"}. Resposta: usuário atualizado (com xp e nivel).
    @PATCH("api/usuarios/me/progresso")
    Call<JsonObject> atualizarProgresso(@Body JsonObject progresso);

    // Registra uma captura. Body: {"idAnimal": int, "latitude": double, "longitude": double}
    // e, para animal chocado de ovo, "origem": "ovo" (o backend não checa a área nesse caso).
    // Resposta: a captura + {"progresso": {"xp", "nivel"}} (sem progresso quando origem = ovo).
    @POST("api/capturas")
    Call<JsonObject> registrarCaptura(@Body JsonObject captura);

    // Espécies que o usuário logado já obteve (1 por espécie). Resposta: Animal[] puro.
    @GET("api/capturas/me")
    Call<List<Animal>> getMinhasCapturas();

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

    // Relatos VGI só do usuário logado (mais recentes primeiro)
    @GET("api/vgi/me")
    Call<List<ReportVgi>> getMeusRelatosVgi();

    // Eventos com dataInicio <= agora <= dataFim
    @GET("api/eventos/ativos")
    Call<List<Evento>> getEventosAtivos();

    // Sorteio de animais pela localização (habitat + VGI + hábito/horário). quantidade: 1 a 10
    @GET("api/spawn/animais")
    Call<List<Animal>> getSpawnAnimais(
            @Query("latitude") double latitude,
            @Query("longitude") double longitude,
            @Query("quantidade") int quantidade
    );
}
