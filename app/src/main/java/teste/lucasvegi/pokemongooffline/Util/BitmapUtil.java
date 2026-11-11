package teste.lucasvegi.pokemongooffline.Util;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

public class BitmapUtil {

    // Mesma assinatura publica de antes: a MapActivity nao precisa mudar nada.
    public static BitmapDescriptor getBitmapDescriptor(Resources res, int resId, int width, int height) {
        // 1) Le so as dimensoes, sem alocar os pixels
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, opts);

        // 2) Decodifica ja reduzido (potencia de 2), mantendo o tamanho >= alvo
        opts.inSampleSize = calcularSampleSize(opts.outWidth, opts.outHeight, width, height);
        opts.inJustDecodeBounds = false;
        Bitmap origem = BitmapFactory.decodeResource(res, resId, opts);
        if (origem == null) {
            return BitmapDescriptorFactory.defaultMarker();
        }

        // 3) Escala exata e libera APENAS o intermediario
        Bitmap redimensionado = Bitmap.createScaledBitmap(origem, width, height, true);
        if (redimensionado != origem) {
            origem.recycle();
        }
        // NAO reciclar 'redimensionado': o BitmapDescriptor mantem a referencia e o Maps
        // le o bitmap depois (addMarker/setIcon). Reciclar aqui gera marcador em branco ou crash.
        return BitmapDescriptorFactory.fromBitmap(redimensionado);
    }

    public static int calcularSampleSize(int origW, int origH, int alvoW, int alvoH) {
        int sample = 1;
        while ((origW / (sample * 2)) >= alvoW && (origH / (sample * 2)) >= alvoH) {
            sample *= 2;
        }
        return sample;
    }
}