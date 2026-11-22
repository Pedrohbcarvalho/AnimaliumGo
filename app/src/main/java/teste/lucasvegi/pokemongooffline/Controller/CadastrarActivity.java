package teste.lucasvegi.pokemongooffline.Controller;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import teste.lucasvegi.pokemongooffline.Model.RetrofitClient;
import teste.lucasvegi.pokemongooffline.Util.RedeUtil;
import teste.lucasvegi.pokemongooffline.R;

public class CadastrarActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastrar);
    }

    public void clickVoltar(View v){
        Intent it = new Intent(this,LoginActivity.class);
        startActivity(it);
        finish();
    }

    public void clickCadastrarUser(View v){
        try {
            Log.i("CADASTRO", "Cadastrando o usuário no sistema...");

            boolean cadOk = true;

            EditText edtNome = (EditText) findViewById(R.id.edtNomeCadastro);

            EditText edtUsuario = (EditText) findViewById(R.id.edtUsuarioCadastro);

            EditText edtSenha = (EditText) findViewById(R.id.edtSenhaCadastro);
            EditText edtConfirmaSenha = (EditText) findViewById(R.id.edtConfirmacaoSenhaCadastro);
            RadioGroup sexo = (RadioGroup) findViewById(R.id.grupoSexo);

            //obtem dados informados pelo usuário
            String nome = edtNome.getText().toString();
            String user = edtUsuario.getText().toString().trim(); // e-mail
            String senha = edtSenha.getText().toString();
            String confSenha = edtConfirmaSenha.getText().toString();
            String nomeSexo = "";

            //obtem informação do radio group
            if (sexo.getCheckedRadioButtonId() == R.id.sexoMasculino)
                nomeSexo = "M";
            else
                nomeSexo = "F";

            //Verifica preenchimento de campos obrigatórios e valida dados
            if (nome.length() == 0 || nome.length() > 50) {
                Toast.makeText(this, "Informe um nome com até 50 caracteres!", Toast.LENGTH_SHORT).show();
                cadOk = false;
            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(user).matches()) {
                Toast.makeText(this, "Informe um e-mail válido!", Toast.LENGTH_SHORT).show();
                cadOk = false;
            } else if (senha.length() < 6 || senha.length() > 45) {
                Toast.makeText(this, "A senha deve ter entre 6 e 45 caracteres!", Toast.LENGTH_SHORT).show();
                cadOk = false;
            } else if (confSenha.length() == 0) {
                Toast.makeText(this, "Informe a confirmação da senha!", Toast.LENGTH_SHORT).show();
                cadOk = false;
            } else if (!senha.equals(confSenha)) {
                Toast.makeText(this, "Confirmação de senha inválida!\nDigite-a novamente.", Toast.LENGTH_SHORT).show();
                cadOk = false;
            }

            //Cadastra no servidor (a conta é criada no Supabase Auth) e depois entra pelo login normal
            if (cadOk) {
                cadastrarNoServidor(nome, user, senha, nomeSexo);
            }

        }catch (Exception e){
            Log.e("CADASTRO", "ERRO: " + e.getMessage());
        }
    }

    // A senha só trafega nesta requisição (e no login logo em seguida); nunca é salva no aparelho
    private void cadastrarNoServidor(String nome, final String email, final String senha, String sexo) {
        if (!RedeUtil.temInternet(this)) {
            Toast.makeText(this, "Sem internet. Conecte-se para criar sua conta.", Toast.LENGTH_LONG).show();
            return;
        }

        JsonObject dados = new JsonObject();
        dados.addProperty("nome", nome);
        dados.addProperty("email", email);
        dados.addProperty("password", senha);
        dados.addProperty("sexo", sexo);

        Toast.makeText(this, "Criando conta...", Toast.LENGTH_SHORT).show();

        RetrofitClient.getApi().registrar(dados).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (isFinishing() || isDestroyed()) return;

                if (response.isSuccessful()) {
                    Toast.makeText(CadastrarActivity.this, "Conta criada!", Toast.LENGTH_SHORT).show();
                    LoginActivity.autenticarEEntrar(CadastrarActivity.this, email, senha);
                } else if (response.code() == 409) {
                    Toast.makeText(CadastrarActivity.this, "Este e-mail já está cadastrado.", Toast.LENGTH_LONG).show();
                } else {
                    Log.e("CADASTRO", "Erro no cadastro: " + response.code());
                    Toast.makeText(CadastrarActivity.this, "Problemas ao cadastrar. Confira os dados e tente novamente.", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Log.e("CADASTRO", "Falha de conexão: " + t.getMessage());
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(CadastrarActivity.this, "Não foi possível falar com o servidor. Verifique sua internet.", Toast.LENGTH_LONG).show();
            }
        });
    }
}
