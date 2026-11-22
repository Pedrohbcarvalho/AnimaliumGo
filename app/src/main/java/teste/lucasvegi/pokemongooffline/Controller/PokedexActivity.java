package teste.lucasvegi.pokemongooffline.Controller;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import teste.lucasvegi.pokemongooffline.Model.Animal;
import teste.lucasvegi.pokemongooffline.Model.ControladoraFachadaSingleton;
import teste.lucasvegi.pokemongooffline.Model.RetrofitClient;
import teste.lucasvegi.pokemongooffline.R;
import teste.lucasvegi.pokemongooffline.View.AdapterPokedex;

// Diário do jogador (antiga Pokédex): lista os animais que ele já obteve, vindos do servidor.
// Toque simples em um item abre a ficha do animal (DetalhesAnimalActivity).
public class PokedexActivity extends Activity implements AdapterView.OnItemClickListener{

    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pokedex);

        try {
            final TextView txtTotal = (TextView) findViewById(R.id.txtPokedexTotal);
            final ListView listView = (ListView) findViewById(R.id.listaPokedex);
            listView.setOnItemClickListener(this);
            txtTotal.setText("Carregando...");

            RetrofitClient.getApi().getMinhasCapturas().enqueue(new Callback<List<Animal>>() {
                @Override
                public void onResponse(Call<List<Animal>> call, Response<List<Animal>> response) {
                    if (isFinishing() || isDestroyed()) return;

                    if (response.isSuccessful() && response.body() != null) {
                        List<Animal> animais = response.body();
                        // Aproveita para atualizar o "novo"/"conhecido" da tela de captura
                        ControladoraFachadaSingleton.getInstance().setIdsAnimaisCapturados(animais);
                        txtTotal.setText("Animais registrados: " + animais.size());
                        listView.setAdapter(new AdapterPokedex(animais, PokedexActivity.this));
                    } else {
                        Log.e("DIARIO", "Erro ao carregar o diário: HTTP " + response.code());
                        falhaAoCarregar(txtTotal);
                    }
                }

                @Override
                public void onFailure(Call<List<Animal>> call, Throwable t) {
                    if (isFinishing() || isDestroyed()) return;
                    Log.e("DIARIO", "Falha ao carregar o diário: " + t.getMessage());
                    falhaAoCarregar(txtTotal);
                }
            });

            //Prepara a musica tema do menu
            mediaPlayer = MediaPlayer.create(getBaseContext(), R.raw.tema_menu);
            mediaPlayer.setLooping(true);

        }catch (Exception e){
            Log.e("DIARIO", "ERRO: " + e.getMessage());
        }
    }

    private void falhaAoCarregar(TextView txtTotal) {
        txtTotal.setText("Animais registrados: -");
        Toast.makeText(this, "Não foi possível carregar o diário.", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                mediaPlayer.start();
            }
        } catch (Exception e) {
            Log.e("DIARIO", "ERRO: " + e.getMessage());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            }
        } catch (Exception e) {
            Log.e("DIARIO", "ERRO: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            //devolve o recurso para o sistema
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }
        }catch (Exception e){
            Log.e("DIARIO", "ERRO: " + e.getMessage());
        }
    }

    public void clickVoltar(View v){
        finish();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        try {
            Animal animal = (Animal) parent.getAdapter().getItem(position);
            Intent it = new Intent(this, DetalhesAnimalActivity.class);
            it.putExtra("idAnimal", animal.getId());
            startActivity(it);
        }catch (Exception e){
            Log.e("DIARIO", "ERRO no click: " + e.getMessage());
        }
    }
}
