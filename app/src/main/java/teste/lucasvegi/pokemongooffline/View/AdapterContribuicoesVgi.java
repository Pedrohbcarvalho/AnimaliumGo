package teste.lucasvegi.pokemongooffline.View;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.NetworkImageView;

import java.util.List;

import teste.lucasvegi.pokemongooffline.Model.Animal;
import teste.lucasvegi.pokemongooffline.Model.ReportVgi;
import teste.lucasvegi.pokemongooffline.R;

// Cards da tela Minhas Contribuições VGI
public class AdapterContribuicoesVgi extends BaseAdapter {

    // Mesmo valor exibido no painel web (VGI_APPROVED_XP no frontend)
    public static final int XP_APROVADO = 200;

    private final List<ReportVgi> relatos;
    private final Activity activity;
    private final ImageLoader carregadorImagens;

    public AdapterContribuicoesVgi(List<ReportVgi> relatos, Activity activity, ImageLoader carregadorImagens) {
        this.relatos = relatos;
        this.activity = activity;
        this.carregadorImagens = carregadorImagens;
    }

    @Override
    public int getCount() {
        return relatos.size();
    }

    @Override
    public Object getItem(int position) {
        return relatos.get(position);
    }

    @Override
    public long getItemId(int position) {
        return relatos.get(position).getIdReport();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = activity.getLayoutInflater().inflate(R.layout.item_contribuicao_vgi, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        ReportVgi relato = relatos.get(position);
        Animal animal = relato.getAnimal();

        holder.nomePopular.setText(animal != null ? animal.getNomePopular() : "Animal #" + relato.getIdAnimalSugerido());
        holder.nomeCientifico.setText(animal != null ? animal.getNomeCientifico() : "");

        // NetworkImageView: reduz para o tamanho da view, usa cache e cancela o download se a linha for reciclada
        holder.foto.setImageUrl(relato.getFotoReal(), carregadorImagens);

        aplicarStatus(holder, relato.getStatus());
        return convertView;
    }

    private void aplicarStatus(ViewHolder holder, String status) {
        int cor, icone;
        String texto;
        boolean aprovado = "Aprovado".equalsIgnoreCase(status);

        if (aprovado) {
            cor = activity.getColor(R.color.primary);
            icone = R.drawable.ic_verified;
            texto = "Aprovado";
        } else if ("Rejeitado".equalsIgnoreCase(status)) {
            cor = activity.getColor(R.color.tertiary);
            icone = R.drawable.ic_cancel;
            texto = "Não Aprovado";
        } else {
            cor = activity.getColor(R.color.secondary);
            icone = R.drawable.ic_hourglass_empty;
            texto = "Pendente";
        }

        holder.tagStatus.setBackgroundTintList(ColorStateList.valueOf(cor));
        holder.iconeStatus.setImageResource(icone);
        holder.iconeStatus.setColorFilter(Color.WHITE);
        holder.txtStatus.setText(texto);

        holder.badgeXp.setVisibility(aprovado ? View.VISIBLE : View.GONE);
        holder.txtXp.setText("+" + XP_APROVADO + " XP");
    }

    private static class ViewHolder {
        final NetworkImageView foto;
        final View tagStatus, badgeXp;
        final ImageView iconeStatus;
        final TextView txtStatus, txtXp, nomePopular, nomeCientifico;

        ViewHolder(View v) {
            foto = v.findViewById(R.id.imgFotoContribuicao);
            tagStatus = v.findViewById(R.id.tagStatus);
            badgeXp = v.findViewById(R.id.badgeXp);
            iconeStatus = v.findViewById(R.id.imgIconeStatus);
            txtStatus = v.findViewById(R.id.txtStatus);
            txtXp = v.findViewById(R.id.txtXp);
            nomePopular = v.findViewById(R.id.txtNomePopular);
            nomeCientifico = v.findViewById(R.id.txtNomeCientifico);

            // Recorta a foto nos cantos arredondados do card
            v.findViewById(R.id.cardContribuicao).setClipToOutline(true);
        }
    }
}
