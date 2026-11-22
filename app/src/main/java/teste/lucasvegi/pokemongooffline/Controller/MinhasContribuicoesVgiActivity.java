package teste.lucasvegi.pokemongooffline.Controller;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.LruCache;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.Volley;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import teste.lucasvegi.pokemongooffline.Model.Animal;
import teste.lucasvegi.pokemongooffline.Model.ReportVgi;
import teste.lucasvegi.pokemongooffline.Model.RetrofitClient;
import teste.lucasvegi.pokemongooffline.R;
import teste.lucasvegi.pokemongooffline.View.AdapterContribuicoesVgi;

// Lista os relatos VGI do usuário logado (GET /api/vgi/me), com painel de contagem, busca e filtro por status.
public class MinhasContribuicoesVgiActivity extends Activity {

    private final List<ReportVgi> todos = new ArrayList<>();     // tudo que veio da API
    private final List<ReportVgi> filtrados = new ArrayList<>(); // o que o adapter mostra
    private String filtroStatus = null;                          // null = Todos

    private int qtdAprovados, qtdPendentes, qtdRejeitados;

    private ListView listView;
    private EditText edtBusca;
    private AdapterContribuicoesVgi adapter;
    private RequestQueue filaImagens;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_minhas_contribuicoes_vgi);

        listView = findViewById(R.id.listaContribuicoes);
        edtBusca = findViewById(R.id.edtBusca);

        // Imagens: Volley com cache em memória (1/8 da memória do app) + cache em disco do próprio Volley
        final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 8)) {
            @Override
            protected int sizeOf(String url, Bitmap bitmap) {
                return bitmap.getByteCount();
            }
        };
        filaImagens = Volley.newRequestQueue(this);
        ImageLoader carregadorImagens = new ImageLoader(filaImagens, new ImageLoader.ImageCache() {
            @Override
            public Bitmap getBitmap(String url) { return cache.get(url); }

            @Override
            public void putBitmap(String url, Bitmap bitmap) { cache.put(url, bitmap); }
        });

        adapter = new AdapterContribuicoesVgi(filtrados, this, carregadorImagens);
        listView.addFooterView(getLayoutInflater().inflate(R.layout.item_rodape_validacao_vgi, listView, false), null, false);
        listView.setAdapter(adapter);

        // Toque num card: abre a ficha do animal relatado
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Intent it = new Intent(this, DetalhesAnimalActivity.class);
            it.putExtra("idAnimal", filtrados.get(position).getIdAnimalSugerido());
            startActivity(it);
        });

        edtBusca.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                findViewById(R.id.btnLimparBusca).setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                aplicarFiltros();
            }
        });

        atualizarContadores();
        carregarContribuicoes();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        filaImagens.stop(); // interrompe downloads pendentes
    }

    // ---------------- API e estados ----------------

    private void carregarContribuicoes() {
        findViewById(R.id.progressCarregando).setVisibility(View.VISIBLE);
        findViewById(R.id.areaMensagem).setVisibility(View.GONE);

        RetrofitClient.getApi().getMeusRelatosVgi().enqueue(new Callback<List<ReportVgi>>() {
            @Override
            public void onResponse(Call<List<ReportVgi>> call, Response<List<ReportVgi>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.e("MINHAS_VGI", "Erro na resposta: " + response.code());
                    mostrarMensagem("Não foi possível carregar seus registros.", true);
                    return;
                }

                todos.clear();
                todos.addAll(response.body());
                atualizarContadores();

                if (todos.isEmpty()) {
                    mostrarMensagem("Você ainda não enviou nenhum registro VGI.", false);
                    return;
                }

                findViewById(R.id.progressCarregando).setVisibility(View.GONE);
                // A partir daqui o ListView alterna sozinho entre a lista e "Nenhum registro encontrado"
                listView.setEmptyView(findViewById(R.id.txtNenhumResultado));
                aplicarFiltros();
            }

            @Override
            public void onFailure(Call<List<ReportVgi>> call, Throwable t) {
                Log.e("MINHAS_VGI", "Falha de conexão: " + t.getMessage());
                mostrarMensagem("Não foi possível carregar seus registros.\nVerifique sua conexão.", true);
            }
        });
    }

    private void mostrarMensagem(String texto, boolean mostrarTentarNovamente) {
        findViewById(R.id.progressCarregando).setVisibility(View.GONE);
        listView.setVisibility(View.GONE);
        ((TextView) findViewById(R.id.txtMensagem)).setText(texto);
        findViewById(R.id.btnTentarNovamente).setVisibility(mostrarTentarNovamente ? View.VISIBLE : View.GONE);
        findViewById(R.id.areaMensagem).setVisibility(View.VISIBLE);
    }

    public void clickTentarNovamente(View v) {
        carregarContribuicoes();
    }

    // ---------------- Busca e filtros ----------------

    private void aplicarFiltros() {
        String busca = normalizar(edtBusca.getText().toString().trim());

        filtrados.clear();
        for (ReportVgi relato : todos) {
            if (filtroStatus != null && !filtroStatus.equalsIgnoreCase(relato.getStatus())) continue;
            if (!busca.isEmpty() && !textoPesquisavel(relato).contains(busca)) continue;
            filtrados.add(relato);
        }
        adapter.notifyDataSetChanged();
    }

    // Nome popular, nome científico e "#id" (sem acentos e em minúsculas)
    private String textoPesquisavel(ReportVgi relato) {
        Animal animal = relato.getAnimal();
        String nomes = animal != null ? animal.getNomePopular() + " " + animal.getNomeCientifico() : "";
        return normalizar(nomes + " #" + relato.getIdReport());
    }

    private String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
    }

    public void clickFiltro(View v) {
        int id = v.getId();
        if (id == R.id.filtroAprovados) filtroStatus = "Aprovado";
        else if (id == R.id.filtroPendentes) filtroStatus = "Pendente";
        else if (id == R.id.filtroRejeitados) filtroStatus = "Rejeitado";
        else filtroStatus = null;

        atualizarPilulas();
        aplicarFiltros();
    }

    public void clickLimparBusca(View v) {
        edtBusca.setText(""); // o TextWatcher reaplica os filtros
    }

    // ---------------- Painel e pílulas ----------------

    private void atualizarContadores() {
        qtdAprovados = qtdPendentes = qtdRejeitados = 0;
        for (ReportVgi relato : todos) {
            if ("Aprovado".equalsIgnoreCase(relato.getStatus())) qtdAprovados++;
            else if ("Rejeitado".equalsIgnoreCase(relato.getStatus())) qtdRejeitados++;
            else qtdPendentes++;
        }

        preencherContador(R.id.contadorTotal, todos.size(), "enviados", null);
        preencherContador(R.id.contadorAprovados, qtdAprovados, "aprovados", getColor(R.color.primary));
        preencherContador(R.id.contadorPendentes, qtdPendentes, "pendentes", getColor(R.color.secondary));
        preencherContador(R.id.contadorRejeitados, qtdRejeitados, "rejeitados", getColor(R.color.tertiary));
        atualizarPilulas();
    }

    private void preencherContador(int idContador, int numero, String rotulo, Integer cor) {
        View contador = findViewById(idContador);
        TextView txtNumero = contador.findViewById(R.id.txtNumero);
        txtNumero.setText(String.valueOf(numero));
        ((TextView) contador.findViewById(R.id.txtRotulo)).setText(rotulo);

        if (cor != null) {
            txtNumero.setTextColor(cor);
            View bolinha = contador.findViewById(R.id.bolinhaCor);
            bolinha.setBackgroundTintList(ColorStateList.valueOf(cor));
            bolinha.setVisibility(View.VISIBLE);
        }
    }

    private void atualizarPilulas() {
        estilizarPilula(R.id.filtroTodos, "Todos", todos.size(), null, filtroStatus == null);
        estilizarPilula(R.id.filtroAprovados, "Aprovados", qtdAprovados, getColor(R.color.primary), "Aprovado".equals(filtroStatus));
        estilizarPilula(R.id.filtroPendentes, "Pendentes", qtdPendentes, getColor(R.color.secondary), "Pendente".equals(filtroStatus));
        estilizarPilula(R.id.filtroRejeitados, "Rejeitados", qtdRejeitados, getColor(R.color.tertiary), "Rejeitado".equals(filtroStatus));
    }

    // Ativa: fundo verde e texto branco. Inativa: fundo branco, texto cinza e bolinha na cor do status.
    private void estilizarPilula(int idPilula, String rotulo, int quantidade, Integer corBolinha, boolean ativa) {
        TextView pilula = findViewById(idPilula);
        String texto = (corBolinha != null ? "●  " : "") + rotulo + "  " + quantidade;

        SpannableString span = new SpannableString(texto);
        if (corBolinha != null && !ativa) {
            span.setSpan(new ForegroundColorSpan(corBolinha), 0, 1, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        pilula.setText(span);
        pilula.setTextColor(ativa ? Color.WHITE : getColor(R.color.on_surface_variant));
        pilula.setBackgroundTintList(ColorStateList.valueOf(ativa ? getColor(R.color.primary) : Color.WHITE));
    }

    public void clickVoltar(View v) {
        finish();
    }
}
