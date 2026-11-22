package teste.lucasvegi.pokemongooffline.Model;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Authenticator;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import teste.lucasvegi.pokemongooffline.Controller.LoginActivity;
import teste.lucasvegi.pokemongooffline.Util.MyApp;
import teste.lucasvegi.pokemongooffline.Util.TokenManager;

public class RetrofitClient {
    private static final String BASE_URL = "https://animalium-go-api.onrender.com/";
    private static Retrofit retrofit = null;
    private static Retrofit retrofitSemAuth = null;

    // API principal: injeta o access token e renova a sessão sozinha quando ele expira (401)
    public static synchronized BackendApi getApi() {
        if (retrofit == null) {
            OkHttpClient client = clienteBase()
                    .addInterceptor(new Interceptor() {
                        @Override
                        public Response intercept(Chain chain) throws IOException {
                            Request.Builder construtor = chain.request().newBuilder();

                            // Pega o token salvo e injeta no cabeçalho, se existir
                            String token = TokenManager.getToken(MyApp.getAppContext());
                            if (token != null) {
                                construtor.header("Authorization", "Bearer " + token);
                            }

                            return chain.proceed(construtor.build());
                        }
                    })
                    .authenticator(new RenovadorDeSessao())
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit.create(BackendApi.class);
    }

    // API "crua", sem token e sem Authenticator: usada só para o refresh síncrono
    // (evita que uma falha no próprio refresh dispare outro refresh em loop).
    private static synchronized BackendApi getApiSemAuth() {
        if (retrofitSemAuth == null) {
            retrofitSemAuth = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(clienteBase().build())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofitSemAuth.create(BackendApi.class);
    }

    private static OkHttpClient.Builder clienteBase() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        return new OkHttpClient.Builder()
                // Conectar é rápido mesmo com o Render "dormindo"; sem internet de verdade
                // (ex.: Wi-Fi sem saída) o app desiste em 15s em vez de 60s.
                .connectTimeout(15, TimeUnit.SECONDS)
                // O Render (plano free) "dorme" e a 1ª resposta pode demorar ~50s
                .readTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(logging);
    }

    // Chamado pelo OkHttp quando uma requisição volta 401 (access token expirado).
    // Troca o refresh token por uma sessão nova e repete a requisição original uma vez.
    private static class RenovadorDeSessao implements Authenticator {
        @Override
        public Request authenticate(Route route, Response response) {
            // synchronized: o refresh token do Supabase é de uso único; duas requisições
            // expirando juntas não podem tentar renová-lo ao mesmo tempo.
            synchronized (RetrofitClient.class) {
                Context ctx = MyApp.getAppContext();

                if (response.priorResponse() != null) return null; // já renovou e voltou 401 de novo
                if (response.request().url().encodedPath().contains("/api/auth/")) return null;

                String refreshToken = TokenManager.getRefreshToken(ctx);
                if (refreshToken == null) return null;

                // Outra thread já renovou enquanto esta esperava o lock: só repete com o token novo
                String tokenAtual = TokenManager.getToken(ctx);
                String tokenUsado = response.request().header("Authorization");
                if (tokenAtual != null && !("Bearer " + tokenAtual).equals(tokenUsado)) {
                    return comToken(response.request(), tokenAtual);
                }

                JsonObject body = new JsonObject();
                body.addProperty("refreshToken", refreshToken);

                retrofit2.Response<JsonObject> resposta;
                try {
                    resposta = getApiSemAuth().renovarSessao(body).execute();
                } catch (IOException e) {
                    // Sem rede / timeout: NÃO desloga. A requisição falha e tenta-se de novo depois.
                    Log.w("SESSAO", "Não foi possível renovar a sessão agora: " + e.getMessage());
                    return null;
                }

                JsonObject sessao = resposta.body();
                if (resposta.isSuccessful() && sessao != null && sessao.has("token") && sessao.has("refreshToken")) {
                    String novoToken = sessao.get("token").getAsString();
                    TokenManager.atualizarTokens(ctx, novoToken, sessao.get("refreshToken").getAsString());
                    Log.i("SESSAO", "Sessão renovada.");
                    return comToken(response.request(), novoToken);
                }

                // 400/401: o servidor recusou o refresh token (expirado/revogado) -> novo login.
                // 5xx: problema no servidor, mantém a sessão.
                if (resposta.code() == 400 || resposta.code() == 401) {
                    Log.w("SESSAO", "Refresh token recusado (" + resposta.code() + "). Encerrando sessão.");
                    encerrarSessaoEIrParaLogin(ctx);
                }
                return null;
            }
        }

        private Request comToken(Request original, String token) {
            return original.newBuilder().header("Authorization", "Bearer " + token).build();
        }

        private void encerrarSessaoEIrParaLogin(final Context ctx) {
            ControladoraFachadaSingleton.getInstance().logoutUser();

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(ctx, "Sua sessão expirou. Entre novamente.", Toast.LENGTH_LONG).show();
                    Intent it = new Intent(ctx, LoginActivity.class);
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    ctx.startActivity(it);
                }
            });
        }
    }
}
