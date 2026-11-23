package teste.lucasvegi.pokemongooffline.Util;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.ImageRequest;

import java.io.File;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

// Carrega a MESMA URL de foto do animal em dois tamanhos, sem bibliotecas novas (só Volley):
//  - pin do mapa: minúsculo, do tamanho dos antigos sprites 8-bits;
//  - foto grande: tamanho de tela (captura, ficha do animal).
// O ImageRequest do Volley lê as dimensões antes e decodifica com inSampleSize, então a
// foto de 1024px do bucket nunca é alocada inteira na RAM só para virar um pin de 96px.
public final class ImagemRemotaUtil {

    // Os sprites antigos (res/drawable/i*.png) têm 32x32px na pasta base (mdpi) = 32dp na tela
    public static final int TAMANHO_PIN_DP = 32;

    public interface AoCarregarPin {
        void carregado(BitmapDescriptor icone);
    }

    // Uma fila só para o app inteiro (contexto da aplicação). O cache em disco do Volley
    // também evita baixar de novo imagens já vistas, inclusive com internet fraca.
    private static RequestQueue fila;

    // Pins já prontos: ~36KB cada (96x96 ARGB em xxhdpi); 2MB comportam ~50 animais
    private static final LruCache<String, Bitmap> cachePins = new LruCache<String, Bitmap>(2 * 1024 * 1024) {
        @Override
        protected int sizeOf(String chave, Bitmap bitmap) {
            return bitmap.getByteCount();
        }
    };

    private ImagemRemotaUtil() { }

    // O padrão do ImageRequest (1 s, 2 tentativas) estoura em rede móvel/CDN frio e o pin fica vermelho
    private static final int TIMEOUT_IMAGEM_MS = 8000;
    // Pin e foto grande da mesma URL ocupam duas entradas (chave por tamanho); os 5MB padrão do
    // Volley encheriam rápido e forçariam downloads de novo
    private static final int CACHE_DISCO_BYTES = 20 * 1024 * 1024;

    private static synchronized RequestQueue getFila() {
        if (fila == null) {
            //equivalente ao Volley.newRequestQueue(ctx), só que com cache em disco maior
            File pasta = new File(MyApp.getAppContext().getCacheDir(), "volley");
            fila = new RequestQueue(new DiskBasedCache(pasta, CACHE_DISCO_BYTES), new BasicNetwork(new HurlStack()));
            fila.start();
        }
        return fila;
    }

    // Todas as imagens passam por aqui. A chave de cache do Volley é só a URL: se o pin (32dp) e a
    // foto grande da mesma URL estiverem baixando ao mesmo tempo, o Volley entrega o MESMO Bitmap já
    // decodificado às duas (WaitingRequestManager) e a captura mostra o animal minúsculo.
    // Com a chave incluindo o tamanho, cada tamanho tem sua requisição e seu bitmap.
    private static ImageRequest criarRequisicao(final String url, final int ladoPx,
                                                Response.Listener<Bitmap> aoBaixar, Response.ErrorListener aoFalhar) {
        // CENTER_INSIDE: cabe inteiro no quadrado sem distorcer.
        // ARGB_8888: preserva a transparência de PNGs/WebPs recortados.
        ImageRequest requisicao = new ImageRequest(url, aoBaixar, ladoPx, ladoPx,
                ImageView.ScaleType.CENTER_INSIDE, Bitmap.Config.ARGB_8888, aoFalhar) {
            @Override
            public String getCacheKey() {
                return url + "#" + ladoPx;
            }
        };
        requisicao.setRetryPolicy(new DefaultRetryPolicy(TIMEOUT_IMAGEM_MS, 1, 1f));
        return requisicao;
    }

    // Pin do mapa. Chamar na UI thread depois do onMapReady (BitmapDescriptorFactory exige o
    // Maps inicializado). Em erro devolve o marcador padrão, para o animal não sumir do mapa.
    public static void carregarPinMapa(final String url, final AoCarregarPin callback) {
        if (url == null || url.isEmpty()) {
            callback.carregado(BitmapDescriptorFactory.defaultMarker());
            return;
        }

        final int px = (int) ViewUnitsUtil.convertDpToPixel(TAMANHO_PIN_DP);
        final String chave = url + "#" + px;

        Bitmap emCache = cachePins.get(chave);
        if (emCache != null) {
            callback.carregado(BitmapDescriptorFactory.fromBitmap(emCache));
            return;
        }

        ImageRequest requisicao = criarRequisicao(url, px,
                bitmap -> {
                    // NÃO reciclar: o BitmapDescriptor e o cache continuam usando este bitmap
                    cachePins.put(chave, bitmap);
                    callback.carregado(BitmapDescriptorFactory.fromBitmap(bitmap));
                },
                erro -> {
                    Log.e("IMAGEM_REMOTA", "Erro ao baixar pin: " + url + " - " + erro.getMessage());
                    callback.carregado(BitmapDescriptorFactory.defaultMarker());
                });
        getFila().add(requisicao);
    }

    // Foto em tamanho de tela. ARGB_8888 preserva o fundo transparente das imagens de espécie
    // (RGB_565 não tem canal alfa); é uma única imagem por tela, então a RAM extra é pequena.
    public static void carregarFotoGrande(final ImageView destino, final String url) {
        carregarFotoGrande(destino, url, null);
    }

    // Mesma foto grande, avisando quando o bitmap já está na ImageView (ex.: a tela de captura
    // precisa medir a imagem real para dimensionar o animal).
    public static void carregarFotoGrande(final ImageView destino, final String url, final Runnable aoCarregar) {
        int larguraTela = Resources.getSystem().getDisplayMetrics().widthPixels;
        carregarImagem(destino, url, larguraTela, aoCarregar);
    }

    // Base dos tamanhos em ImageView: decodifica a imagem com no máximo `ladoPx` de lado.
    // A URL fica na tag da view: em listas que reciclam linhas, um download antigo que chega
    // atrasado não sobrescreve a imagem da linha nova.
    public static void carregarImagem(final ImageView destino, final String url, int ladoPx, final Runnable aoCarregar) {
        destino.setTag(url);
        if (url == null || url.isEmpty()) return;

        ImageRequest requisicao = criarRequisicao(url, ladoPx,
                bitmap -> {
                    if (!url.equals(destino.getTag())) return; // a view já mostra outra imagem
                    destino.setImageBitmap(bitmap);
                    if (aoCarregar != null) aoCarregar.run();
                },
                erro -> Log.e("IMAGEM_REMOTA", "Erro ao baixar foto: " + url + " - " + erro.getMessage()));
        getFila().add(requisicao);
    }
}
