package teste.lucasvegi.pokemongooffline.Controller;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import teste.lucasvegi.pokemongooffline.Model.PerfilUsuario;
import teste.lucasvegi.pokemongooffline.Model.RetrofitClient;
import teste.lucasvegi.pokemongooffline.Util.RedeUtil;
import teste.lucasvegi.pokemongooffline.Util.TokenManager;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import teste.lucasvegi.pokemongooffline.Model.ControladoraFachadaSingleton;
import teste.lucasvegi.pokemongooffline.R;

// Login SEMPRE online: a senha só trafega nesta requisição e nunca é guardada no aparelho.
// Depois do 1º login, o app abre direto no mapa (inclusive offline) enquanto houver
// refresh token salvo; ver ControladoraFachadaSingleton.temSessao().
public class LoginActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
    }

    public void clickLogin(View v){
        EditText edtUsuario = (EditText) findViewById(R.id.edtUsuarioLogin);
        EditText edtSenha = (EditText) findViewById(R.id.edtSenhaLogin);

        String email = edtUsuario.getText().toString().trim();
        String senha = edtSenha.getText().toString();

        if (email.isEmpty() || senha.isEmpty()) {
            Toast.makeText(this, "Informe e-mail e senha.", Toast.LENGTH_SHORT).show();
            return;
        }

        autenticarEEntrar(this, email, senha);
    }

    // Usado também pela CadastrarActivity logo após criar a conta.
    // POST /api/auth/login -> salva tokens -> espelha o perfil no SQLite -> abre o mapa.
    public static void autenticarEEntrar(final Activity tela, final String email, final String senha) {
        if (!RedeUtil.temInternet(tela)) {
            Toast.makeText(tela, "Sem internet. Conecte-se para entrar.", Toast.LENGTH_LONG).show();
            return;
        }

        Log.i("LOGIN", "Autenticando a entrada no sistema...");
        Toast.makeText(tela, "Entrando...", Toast.LENGTH_SHORT).show();

        JsonObject jsonLogin = new JsonObject();
        jsonLogin.addProperty("email", email);
        jsonLogin.addProperty("password", senha);

        RetrofitClient.getApi().fazerLogin(jsonLogin).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (tela.isFinishing() || tela.isDestroyed()) return;

                JsonObject corpo = response.body();
                if (!response.isSuccessful() || corpo == null) {
                    Toast.makeText(tela, "E-mail ou senha inválidos.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!corpo.has("token") || !corpo.has("refreshToken")
                        || !corpo.has("user") || !corpo.get("user").isJsonObject()) {
                    Log.e("LOGIN", "Resposta de login incompleta: " + corpo);
                    Toast.makeText(tela, "Erro ao carregar o perfil do servidor.", Toast.LENGTH_SHORT).show();
                    return;
                }

                // O objeto "user" do login tem o mesmo formato de GET /api/usuarios/me
                PerfilUsuario perfil = new Gson().fromJson(corpo.get("user"), PerfilUsuario.class);

                // O e-mail canônico do servidor identifica o usuário no SQLite e é o dono dos tokens
                String login = perfil.getEmail() != null ? perfil.getEmail() : email;

                TokenManager.salvarSessao(tela,
                        corpo.get("token").getAsString(),
                        corpo.get("refreshToken").getAsString(),
                        login);
                ControladoraFachadaSingleton.getInstance().salvarUsuarioDaNuvem(login, perfil);
                Log.d("LOGIN", "Perfil carregado: " + perfil.getNome() + " (nível " + perfil.getNivel() + ")");

                tela.startActivity(new Intent(tela, MapActivity.class));
                tela.finish();
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Log.e("LOGIN", "Servidor inacessível: " + t.getMessage());
                if (tela.isFinishing() || tela.isDestroyed()) return;
                Toast.makeText(tela, "Não foi possível falar com o servidor. Verifique sua internet.", Toast.LENGTH_LONG).show();
            }
        });
    }

    public void clickCadastrar(View v){
        Intent it = new Intent(this, CadastrarActivity.class);
        startActivity(it);
        finish();
    }
}
