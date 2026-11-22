package teste.lucasvegi.pokemongooffline.Util;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.Volley;
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

    private static synchronized RequestQueue getFila() {
        if (fila == null) fila = Volley.newRequestQueue(MyApp.getAppContext());
        return fila;
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

        // CENTER_INSIDE: cabe inteiro no quadrado do pin sem distorcer.
        // ARGB_8888: preserva a transparência de PNGs recortados.
        ImageRequest requisicao = new ImageRequest(url,
                bitmap -> {
                    // NÃO reciclar: o BitmapDescriptor e o cache continuam usando este bitmap
                    cachePins.put(chave, bitmap);
                    callback.carregado(BitmapDescriptorFactory.fromBitmap(bitmap));
                },
                px, px, ImageView.ScaleType.CENTER_INSIDE, Bitmap.Config.ARGB_8888,
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

        ImageRequest requisicao = new ImageRequest(url,
                bitmap -> {
                    if (!url.equals(destino.getTag())) return; // a view já mostra outra imagem
                    destino.setImageBitmap(bitmap);
                    if (aoCarregar != null) aoCarregar.run();
                },
                ladoPx, ladoPx, ImageView.ScaleType.CENTER_INSIDE, Bitmap.Config.ARGB_8888,
                erro -> Log.e("IMAGEM_REMOTA", "Erro ao baixar foto: " + url + " - " + erro.getMessage()));
        getFila().add(requisicao);
    }
}
