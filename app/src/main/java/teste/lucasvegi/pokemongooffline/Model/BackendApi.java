package teste.lucasvegi.pokemongooffline.Model; // Ou o pacote onde você decidir guardar

import java.util.List;

import com.google.gson.JsonObject;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Body;

public interface BackendApi {

    // Retorna uma lista pronta de objetos Animal em Java
    @GET("api/animais")
    Call<List<Animal>> getAnimais();

    // Faz a ponte para a submissão de relatórios do VGI
    // @Multipart [e usado porque vamos enviar a foto como um arquivo binário,
    // exatamente da mesma forma que o Express recebe via 'multer' no backend.
    @Multipart
    @POST("api/vgi")
    Call<ResponseBody> enviarAvistamento(
            @Part("animal_id") RequestBody animalId,
            @Part("latitude") RequestBody latitude,
            @Part("longitude") RequestBody longitude,
            @Part MultipartBody.Part foto
    );

    @POST("api/auth/login")
    Call<JsonObject> fazerLogin(@Body JsonObject loginData);

    @Multipart
    @POST("api/vgi")
    Call<JsonObject> enviarAvistamento(
            @Part MultipartBody.Part arquivo,
            @Part("idAnimalSugerido") RequestBody idAnimalSugerido,
            @Part("latitude") RequestBody latitude,
            @Part("longitude") RequestBody longitude
    );
}