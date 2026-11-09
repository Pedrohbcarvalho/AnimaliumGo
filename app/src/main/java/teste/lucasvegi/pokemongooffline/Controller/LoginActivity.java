package teste.lucasvegi.pokemongooffline.Controller;

import com.google.gson.JsonObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import teste.lucasvegi.pokemongooffline.Model.RetrofitClient;
import teste.lucasvegi.pokemongooffline.Util.TokenManager;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import teste.lucasvegi.pokemongooffline.Model.ControladoraFachadaSingleton;
import teste.lucasvegi.pokemongooffline.R;

public class LoginActivity extends Activity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_login);

    }

    public void clickLogin(View v){
        try {
            Log.i("LOGIN", "Autenticando a entrada no sistema...");

            EditText edtUsuario = (EditText) findViewById(R.id.edtUsuarioLogin);
            EditText edtSenha = (EditText) findViewById(R.id.edtSenhaLogin);

            final String usuario = edtUsuario.getText().toString();
            final String senha = edtSenha.getText().toString();

            // 1. Prepara o JSON para o Node.js (Ajuste a chave "email" se seu backend usar "username" ou "login")
            JsonObject jsonLogin = new JsonObject();
            jsonLogin.addProperty("email", usuario);
            jsonLogin.addProperty("password", senha);

            // 2. Tenta fazer login na nuvem
            RetrofitClient.getApi().fazerLogin(jsonLogin).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        // 1. Pega o token da resposta
                        JsonObject body = response.body();
                        String token = body.get("token").getAsString();
                        TokenManager.salvarToken(LoginActivity.this, token);

                        // 2. Extrai o perfil fiel da nuvem (Supabase)
                        JsonObject user = body.getAsJsonObject("user");
                        String nomeCloud = user.has("nome") && !user.get("nome").isJsonNull() ? user.get("nome").getAsString() : usuario;
                        String sexoCloud = user.has("sexo") && !user.get("sexo").isJsonNull() ? user.get("sexo").getAsString() : "M";
                        String fotoCloud = user.has("foto") && !user.get("foto").isJsonNull() ? user.get("foto").getAsString() : "";
                        int nivelCloud = user.has("nivel") ? user.get("nivel").getAsInt() : 1;
                        int xpCloud = user.has("xp") ? user.get("xp").getAsInt() : 0;
                        String dtCadCloud = user.has("dtCadastro") && !user.get("dtCadastro").isJsonNull() ? user.get("dtCadastro").getAsString() : "";

                        // 3. Tenta logar no cache local
                        boolean logouLocal = ControladoraFachadaSingleton.getInstance().loginUser(usuario, senha);

                        // 4. Se o usuário não existe no celular (primeiro login offline-first), fazemos o Sync
                        if (!logouLocal) {
                            // Usa o seu método padrão para preparar o terreno (limpar tabelas, criar doces, etc.)
                            ControladoraFachadaSingleton.getInstance().cadastrarUser(usuario, senha, nomeCloud, sexoCloud, fotoCloud);

                            // Sobrescreve os dados padrão do SQLite com o progresso real da nuvem
                            android.content.ContentValues valores = new android.content.ContentValues();
                            valores.put("nivel", nivelCloud);
                            valores.put("xp", xpCloud);
                            if (!dtCadCloud.isEmpty()) valores.put("dtCadastro", dtCadCloud);

                            teste.lucasvegi.pokemongooffline.Util.BancoDadosSingleton.getInstance().atualizar("usuario", valores, "login='" + usuario + "'");

                            // Recarrega o usuário na memória do singleton com os dados espelhados
                            ControladoraFachadaSingleton.getInstance().loginUser(usuario, senha);
                        }

                        // 5. Entra no jogo com o banco local 100% igual à nuvem
                        Intent it = new Intent(LoginActivity.this, MapActivity.class);
                        startActivity(it);
                        finish();
                    } else {
                        Toast.makeText(LoginActivity.this, "Credenciais inválidas no servidor!", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<JsonObject> call, Throwable t) {
                    // 5. Fallback Offline: Se não tem internet, tenta validar apenas pelo banco local
                    Log.e("LOGIN", "Servidor inacessível, tentando login offline. Erro: " + t.getMessage());

                    if (ControladoraFachadaSingleton.getInstance().loginUser(usuario, senha)) {
                        Toast.makeText(LoginActivity.this, "Modo Offline Ativado", Toast.LENGTH_SHORT).show();
                        Intent it = new Intent(LoginActivity.this, MapActivity.class);
                        startActivity(it);
                        finish();
                    } else {
                        Toast.makeText(LoginActivity.this, "Falha de rede e usuário não salvo localmente.", Toast.LENGTH_LONG).show();
                    }
                }
            });
        } catch (Exception e){
            Log.e("LOGIN", "ERRO: " + e.getMessage());
        }
    }

    /*
    public void clickLogin(View v){
        try {
            Log.i("LOGIN", "Autenticando a entrada no sistema...");

            EditText edtUsuario = (EditText) findViewById(R.id.edtUsuarioLogin);
            EditText edtSenha = (EditText) findViewById(R.id.edtSenhaLogin);

            //Obtem dados do usuário
            String usuario = edtUsuario.getText().toString();
            String senha = edtSenha.getText().toString();

            if (ControladoraFachadaSingleton.getInstance().loginUser(usuario, senha)) {
                Intent it = new Intent(this, MapActivity.class);
                startActivity(it);
                finish();
            } else {
                Toast.makeText(this, "Usuário e/ou senha inválido(s)!", Toast.LENGTH_SHORT).show();
            }
        }catch (Exception e){
            Log.e("LOGIN", "ERRO: " + e.getMessage());
        }

    } */

    public void clickCadastrar(View v){
        Intent it = new Intent(this, CadastrarActivity.class);
        startActivity(it);
        finish();
    }
}
