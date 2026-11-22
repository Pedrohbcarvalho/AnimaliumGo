package teste.lucasvegi.pokemongooffline.Controller;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import teste.lucasvegi.pokemongooffline.Model.Animal;
import teste.lucasvegi.pokemongooffline.Model.RetrofitClient;
import teste.lucasvegi.pokemongooffline.R;
import teste.lucasvegi.pokemongooffline.Util.ImagemRemotaUtil;

// Tela de detalhes de um animal da fauna (dados do backend: GET /api/animais/{id}).
// Recebe o extra "idAnimal" (int).
public class DetalhesAnimalActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detalhes_animal);

        // Cantos arredondados da foto: recorta a imagem pelo fundo arredondado
        findViewById(R.id.imgFotoAnimal).setClipToOutline(true);

        int idAnimal = getIntent().getIntExtra("idAnimal", -1);
        if (idAnimal == -1) {
            Toast.makeText(this, "Animal não informado", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        carregarAnimal(idAnimal);
    }

    private void carregarAnimal(int idAnimal) {
        RetrofitClient.getApi().getAnimal(idAnimal).enqueue(new Callback<Animal>() {
            @Override
            public void onResponse(Call<Animal> call, Response<Animal> response) {
                if (response.isSuccessful() && response.body() != null) {
                    preencherTela(response.body());
                    findViewById(R.id.progressCarregando).setVisibility(View.GONE);
                    findViewById(R.id.scrollConteudo).setVisibility(View.VISIBLE);
                } else {
                    Log.e("ANIMAL_DETALHES", "Erro na resposta: " + response.code());
                    Toast.makeText(DetalhesAnimalActivity.this, "Animal não encontrado", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onFailure(Call<Animal> call, Throwable t) {
                Log.e("ANIMAL_DETALHES", "Falha de conexão: " + t.getMessage());
                Toast.makeText(DetalhesAnimalActivity.this, "Falha de conexão", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void preencherTela(Animal animal) {
        ((TextView) findViewById(R.id.txtNomePopular)).setText(animal.getNomePopular());
        ((TextView) findViewById(R.id.txtNomeCientifico)).setText(animal.getNomeCientifico());

        preencherLinha(R.id.pilulaTamanho, R.drawable.ic_straighten, "Tamanho", animal.getTamanhoMedio());
        preencherLinha(R.id.pilulaPeso, R.drawable.ic_scale, "Peso", animal.getPesoMedio());

        String habito = animal.getHabito() != null ? animal.getHabito().replace("/", ", ") : null;

        preencherLinha(R.id.linhaClasse, R.drawable.ic_pets, "Classe", animal.getClasseAnimal());
        preencherLinha(R.id.linhaSubordem, R.drawable.ic_account_tree, "Subordem", animal.getSubordem());
        preencherLinha(R.id.linhaFamilia, R.drawable.ic_category, "Família", animal.getFamilia());
        preencherLinha(R.id.linhaHabitat, R.drawable.ic_forest, "Habitat", animal.getHabitat());
        preencherLinha(R.id.linhaPericulosidade, R.drawable.ic_warning, "Periculosidade", animal.getPericulosidade());
        aplicarCorIcone(R.id.linhaPericulosidade, getColor(R.color.periculosidade_fundo), getColor(R.color.periculosidade_icone));
        aplicarStatusIucn(animal.getStatusIUCN());
        preencherLinha(R.id.linhaHabito, R.drawable.ic_bedtime, "Hábito", habito);
        preencherLinha(R.id.linhaDieta, R.drawable.ic_restaurant, "Dieta", animal.getDieta());
        preencherLinha(R.id.linhaTamanho, R.drawable.ic_square_foot, "Tamanho médio", animal.getTamanhoMedio());
        preencherLinha(R.id.linhaPeso, R.drawable.ic_fitness_center, "Peso médio", animal.getPesoMedio());

        carregarFoto(animal.getFotoUrl());
    }

    // Preenche uma linha (ou pílula). Se o valor não existir, a linha some sem deixar espaço.
    private void preencherLinha(int idLinha, int icone, String rotulo, String valor) {
        View linha = findViewById(idLinha);
        if (valor == null || valor.trim().isEmpty()) {
            linha.setVisibility(View.GONE);
            return;
        }
        ((ImageView) linha.findViewById(R.id.imgIcone)).setImageResource(icone);
        ((TextView) linha.findViewById(R.id.txtRotulo)).setText(rotulo);
        ((TextView) linha.findViewById(R.id.txtValor)).setText(valor.trim());
    }

    private void aplicarCorIcone(int idLinha, int corFundo, int corIcone) {
        View linha = findViewById(idLinha);
        linha.findViewById(R.id.caixaIcone).setBackgroundTintList(ColorStateList.valueOf(corFundo));
        ((ImageView) linha.findViewById(R.id.imgIcone)).setColorFilter(corIcone);
    }

    // O ícone e as cores mudam conforme a urgência do status de conservação
    private void aplicarStatusIucn(String status) {
        String texto;
        int icone, corFundo, corIcone;

        switch (codigoIucn(status)) {
            case "LC":
                texto = "Pouco Preocupante (LC)";
                icone = R.drawable.ic_check_circle;
                corFundo = Color.parseColor("#DCF5E3");
                corIcone = Color.parseColor("#006B2C");
                break;
            case "NT":
                texto = "Quase Ameaçada (NT)";
                icone = R.drawable.ic_info;
                corFundo = Color.parseColor("#FFF3C4");
                corIcone = Color.parseColor("#7A5900");
                break;
            case "VU":
                texto = "Vulnerável (VU)";
                icone = R.drawable.ic_warning;
                corFundo = Color.parseColor("#FFDCC3");
                corIcone = Color.parseColor("#6E3900");
                break;
            case "EN":
                texto = "Em Perigo (EN)";
                icone = R.drawable.ic_report;
                corFundo = Color.parseColor("#FFDADA");
                corIcone = Color.parseColor("#920028");
                break;
            case "CR":
                texto = "Criticamente Em Perigo (CR)";
                icone = R.drawable.ic_dangerous;
                corFundo = Color.parseColor("#BA0035");
                corIcone = Color.WHITE;
                break;
            default: // DD ou valor desconhecido
                texto = "Dados Insuficientes (DD)";
                icone = R.drawable.ic_help;
                corFundo = Color.parseColor("#E5E7EB");
                corIcone = Color.parseColor("#4B5563");
                break;
        }

        preencherLinha(R.id.linhaStatusIucn, icone, "Status IUCN", texto);
        aplicarCorIcone(R.id.linhaStatusIucn, corFundo, corIcone);
    }

    // Aceita "lc", "NT", "Pouco preocupante (LC)", "Quase Ameaçada (NT - Near Threatened)"...
    private String codigoIucn(String status) {
        if (status == null) return "DD";
        String s = status.trim().toUpperCase();
        if (s.length() == 2) return s;
        int abre = s.lastIndexOf('(');
        if (abre >= 0 && s.length() >= abre + 3) return s.substring(abre + 1, abre + 3);
        return "DD";
    }

    // Foto em tamanho de tela (mesmo helper que a tela de Captura vai usar)
    private void carregarFoto(String url) {
        ImagemRemotaUtil.carregarFotoGrande((ImageView) findViewById(R.id.imgFotoAnimal), url);
    }

    public void clickVoltar(View v) {
        finish();
    }
}
