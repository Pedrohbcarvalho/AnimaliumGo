package teste.lucasvegi.pokemongooffline.Model;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import teste.lucasvegi.pokemongooffline.Util.MyApp;
import teste.lucasvegi.pokemongooffline.Util.TokenManager;

public class RetrofitClient {
    private static final String BASE_URL = "http://localhost:3333/";
    private static Retrofit retrofit = null;

    public static BackendApi getApi() {
        if (retrofit == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .addInterceptor(new Interceptor() {
                        @Override
                        public Response intercept(Chain chain) throws IOException {
                            Request requisicaoOriginal = chain.request();
                            Request.Builder construtor = requisicaoOriginal.newBuilder();

                            // Pega o token salvo e injeta no cabeçalho, se existir
                            String token = TokenManager.getToken(MyApp.getAppContext());
                            if (token != null) {
                                construtor.header("Authorization", "Bearer " + token);
                            }

                            return chain.proceed(construtor.build());
                        }
                    })
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit.create(BackendApi.class);
    }
}