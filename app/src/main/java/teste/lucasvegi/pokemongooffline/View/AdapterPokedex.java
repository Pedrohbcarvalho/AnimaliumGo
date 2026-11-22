package teste.lucasvegi.pokemongooffline.View;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import teste.lucasvegi.pokemongooffline.Model.Animal;
import teste.lucasvegi.pokemongooffline.R;
import teste.lucasvegi.pokemongooffline.Util.ImagemRemotaUtil;
import teste.lucasvegi.pokemongooffline.Util.ViewUnitsUtil;

/**
 * Lista do Diário: só os animais que o jogador já obteve (GET /api/capturas/me).
 * Created by Lucas on 20/12/2016.
 */
public class AdapterPokedex extends BaseAdapter {

    // Miniatura da linha (o ImageView tem 55dp): decodifica pequena, não no tamanho da tela
    private static final int TAMANHO_MINIATURA_DP = 64;

    private List<Animal> animais;
    private Activity act;

    public AdapterPokedex(List<Animal> animais, Activity act) {
        this.animais = animais;
        this.act = act;
    }

    @Override
    public int getCount() {
        return animais.size();
    }

    @Override
    public Object getItem(int position) {
        return animais.get(position);
    }

    @Override
    public long getItemId(int position) {
        return animais.get(position).getId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView != null ? convertView
                : act.getLayoutInflater().inflate(R.layout.lista_pokedex_personalizada, parent, false);

        try {
            Animal animal = animais.get(position);

            TextView nome = (TextView) view.findViewById(R.id.txtNomePokemonPokedex);
            TextView nomeCientifico = (TextView) view.findViewById(R.id.txtNumeroPokemonPokedex);
            ImageView imagem = (ImageView) view.findViewById(R.id.imagemPokemonPokedex);

            nome.setText(animal.getNomePopular());
            nomeCientifico.setText(animal.getNomeCientifico());

            // Linha reciclada: limpa a foto antiga até a nova baixar
            imagem.setImageDrawable(null);
            int ladoPx = (int) ViewUnitsUtil.convertDpToPixel(TAMANHO_MINIATURA_DP);
            ImagemRemotaUtil.carregarImagem(imagem, animal.getFotoUrl(), ladoPx, null);
        } catch (Exception e) {
            Log.e("DIARIO", "ERRO ao montar linha: " + e.getMessage());
        }
        return view;
    }
}
