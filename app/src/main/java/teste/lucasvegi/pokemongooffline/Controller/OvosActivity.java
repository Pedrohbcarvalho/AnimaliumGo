package teste.lucasvegi.pokemongooffline.Controller;

import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import teste.lucasvegi.pokemongooffline.Model.Animal;
import teste.lucasvegi.pokemongooffline.Model.ControladoraFachadaSingleton;
import teste.lucasvegi.pokemongooffline.Model.Ovo;
import teste.lucasvegi.pokemongooffline.R;
import teste.lucasvegi.pokemongooffline.Util.ImagemRemotaUtil;
import teste.lucasvegi.pokemongooffline.Util.ViewUnitsUtil;
import teste.lucasvegi.pokemongooffline.View.AdapterOvos;

public class OvosActivity extends Activity implements AdapterView.OnItemClickListener {
    private static final int TAMANHO_FOTO_TOAST_DP = 120;
    private List<Ovo> ovos;
    private MediaPlayer mediaPlayer;
    private Toast toastChocado;
    private List<Animal> animaisChocados = new ArrayList<Animal>();
    private Location localizacaoAtual = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ovos);
        try {
            TextView txtTotal = (TextView) findViewById(R.id.txtOvosTotal);


            ovos = ControladoraFachadaSingleton.getInstance().getOvos();
            ListView listView = (ListView) findViewById(R.id.listaOvos);

            Intent it = getIntent();
            localizacaoAtual = it.getParcelableExtra("location");

            // Percorre de trás para frente: remover um ovo chocado não desloca os que ainda faltam verificar
            for (int i = ovos.size() - 1; i >= 0; i--) {
                if (ovos.get(i).getLocalizacao() == null && ovos.get(i).getIncubado() == 1) {
                    ovos.get(i).setLocalizacao(localizacaoAtual);
                }

                if (ovos.get(i).getIncubado() == 1) {

                    double distancia = localizacaoAtual.distanceTo(ovos.get(i).getLocalizacao()) / 1000;
                    Log.i("OVOS", "Distância: " + distancia);
                    ovos.get(i).setKmAndado(ovos.get(i).getKmAndado() + distancia);
                    ovos.get(i).setLocalizacao(localizacaoAtual);
                    ControladoraFachadaSingleton.getInstance().setKmAndado(ovos.get(i).getIdOvo(), ovos.get(i).getKmAndado());

                    //testa se ovo chocou
                    if (ovos.get(i).getKmAndado() >= ovos.get(i).getKm()) {
                        //sorteia o animal (REGRA TEMPORÁRIA: qualquer animal) e registra no servidor.
                        //null = lista de animais ainda não carregada: o ovo espera, sem perder a km.
                        Animal animal = ControladoraFachadaSingleton.getInstance().chocarOvo(ovos.get(i).getIdOvo(), ovos.get(i).getLocalizacao());
                        if (animal == null) continue;

                        ovos.get(i).setChocado(1);
                        ControladoraFachadaSingleton.getInstance().setChocado(ovos.get(i).getIdOvo(), 1);
                        //ovo já foi exibido
                        ControladoraFachadaSingleton.getInstance().setExibido(ovos.get(i).getIdOvo(), 1);

                        //remove ovo da lista de ovos
                        animaisChocados.add(animal);
                        ovos.remove(i);

                    }
                }
            }
            //total de ovos na lista
            int total = ControladoraFachadaSingleton.getInstance().getOvos().size();
            txtTotal.setText("Ovos: " + total + "/9");

            AdapterOvos adapterOvos = new AdapterOvos(ovos, this);
            listView.setAdapter(adapterOvos);
            listView.setOnItemClickListener(this);
            mediaPlayer = MediaPlayer.create(getBaseContext(), R.raw.tema_menu);
            mediaPlayer.setLooping(true);
            exibirChocados();
        }catch (Exception e){
            Log.e("OVOS", "ERRO: " + e.getMessage());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                mediaPlayer.start();
            }
        } catch (Exception e) {
            Log.e("OVOS", "ERRO: " + e.getMessage());
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
            Log.e("OVOS", "ERRO: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }
        }catch (Exception e){
            Log.e("OVOS", "ERRO: " + e.getMessage());
        }
    }


    public void clickVoltar(View v){
        for (int i = 0; i < ovos.size(); i++) {
            if (ovos.get(i).getLocalizacao() == null && ovos.get(i).getIncubado() == 1) {
                ovos.get(i).setLocalizacao(localizacaoAtual);
            }
        }
        finish();
    }

    // Toast com os animais que acabaram de chocar. Os layouts têm 1, 2 ou 3 fotos:
    // com mais de 3 ovos de uma vez, mostra as 3 primeiras fotos e todos os nomes.
    public void exibirChocados(){
        int qtd = animaisChocados.size();
        if (qtd == 0) return;

        int layoutId, raizId;
        if (qtd == 1) { layoutId = R.layout.toast_pkmn_chocado; raizId = R.id.toast_pkmn_chocado; }
        else if (qtd == 2) { layoutId = R.layout.toast_pkmn_chocado2; raizId = R.id.toast_pkmn_chocado2; }
        else { layoutId = R.layout.toast_pkmn_chocado3; raizId = R.id.toast_pkmn_chocado3; }

        View layout = getLayoutInflater().inflate(layoutId, (ViewGroup) findViewById(raizId));

        StringBuilder nomes = new StringBuilder();
        for (int i = 0; i < qtd; i++) {
            if (i > 0) nomes.append(i == qtd - 1 ? " e " : ", ");
            nomes.append(animaisChocados.get(i).getNomePopular());
        }
        TextView txt = (TextView) layout.findViewById(R.id.txtPokemon);
        txt.setText("Oba! " + nomes + (qtd == 1 ? " foi chocado!" : " foram chocados!"));

        int[] idsFotos = {R.id.imgPokemon, R.id.imgPokemon2, R.id.imgPokemon3};
        int ladoPx = (int) ViewUnitsUtil.convertDpToPixel(TAMANHO_FOTO_TOAST_DP);
        for (int i = 0; i < Math.min(qtd, 3); i++) {
            ImageView foto = (ImageView) layout.findViewById(idsFotos[i]);
            foto.setImageDrawable(null); // tira o placeholder do layout até a foto baixar
            ImagemRemotaUtil.carregarImagem(foto, animaisChocados.get(i).getFotoUrl(), ladoPx, null);
        }

        toastChocado = new Toast(getApplicationContext());
        toastChocado.setGravity(Gravity.CENTER_VERTICAL, 0, 100);
        toastChocado.setDuration(Toast.LENGTH_LONG);
        toastChocado.setView(layout);
        toastChocado.show();
        animaisChocados.clear();
    }


    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

    }
}