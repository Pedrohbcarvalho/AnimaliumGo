package teste.lucasvegi.pokemongooffline.Util;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.util.Log;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

import java.io.File;
import java.io.FileOutputStream;

public class BitmapUtil {

    // Fotos VGI enviadas ao bucket: lado maior suficiente para o validador identificar a espécie
    public static final int MAX_LADO_UPLOAD = 1280;
    public static final int QUALIDADE_UPLOAD = 75;

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

    // Decodifica uma foto do disco com o MAIOR lado limitado a maxLado e já na orientação
    // correta (EXIF). Roda em background. Retorna null se o arquivo for inválido.
    public static Bitmap decodificarComRotacao(String caminho, int maxLado) {
        try {
            // 1) Lê só as dimensões, sem alocar memória para os pixels
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(caminho, options);

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                return null; // arquivo vazio/corrompido (ex.: câmera cancelada)
            }

            // 2) inSampleSize (potência de 2) mantendo o maior lado >= maxLado: decodifica
            //    uma versão leve sem perder qualidade em relação ao tamanho final
            int maiorLado = Math.max(options.outWidth, options.outHeight);
            int sample = 1;
            while (maiorLado / (sample * 2) >= maxLado) {
                sample *= 2;
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            Bitmap bitmap = BitmapFactory.decodeFile(caminho, options);
            if (bitmap == null) return null;

            // 3) Ajuste fino para o maior lado ficar exatamente <= maxLado
            int ladoAtual = Math.max(bitmap.getWidth(), bitmap.getHeight());
            if (ladoAtual > maxLado) {
                float escala = (float) maxLado / ladoAtual;
                Bitmap reduzido = Bitmap.createScaledBitmap(bitmap,
                        Math.round(bitmap.getWidth() * escala),
                        Math.round(bitmap.getHeight() * escala), true);
                if (reduzido != bitmap) bitmap.recycle();
                bitmap = reduzido;
            }

            // 4) Corrige a rotação pelo EXIF
            Matrix matrix = new Matrix();
            ExifInterface exif = new ExifInterface(caminho);
            switch (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);
                    break;
            }

            // Sem rotação: devolve direto, sem criar uma segunda cópia na memória
            if (matrix.isIdentity()) return bitmap;

            Bitmap rotacionado = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotacionado != bitmap) bitmap.recycle(); // libera o intermediário imediatamente
            return rotacionado;
        } catch (OutOfMemoryError e) {
            // Última linha de defesa: não derruba o app
            Log.e("BITMAP", "Sem memória para decodificar a foto", e);
        } catch (Exception e) {
            Log.e("BITMAP", "Erro no processamento da foto", e);
        }
        return null;
    }

    // Regrava a foto da câmera (3-6MB) como JPEG leve (~150-300KB) ANTES de qualquer upload:
    // maior lado <= 1280px, rotação aplicada nos pixels (o EXIF se perde no re-encode).
    // Roda em background. Se falhar, o arquivo original fica intacto (e o envio ainda funciona).
    public static boolean comprimirParaUpload(String caminho) {
        long tamanhoAntes = new File(caminho).length();

        Bitmap bitmap = decodificarComRotacao(caminho, MAX_LADO_UPLOAD);
        if (bitmap == null) return false;

        File temp = new File(caminho + ".tmp");
        FileOutputStream out = null;
        boolean ok = false;
        try {
            out = new FileOutputStream(temp);
            ok = bitmap.compress(Bitmap.CompressFormat.JPEG, QUALIDADE_UPLOAD, out);
        } catch (Exception e) {
            Log.e("BITMAP", "Erro ao comprimir a foto", e);
        } finally {
            if (out != null) {
                try { out.close(); } catch (Exception ignored) { }
            }
            bitmap.recycle();
        }

        // Só substitui o original se o arquivo comprimido foi gravado por completo
        if (ok && temp.length() > 0 && temp.renameTo(new File(caminho))) {
            Log.d("BITMAP", "Foto comprimida: " + (tamanhoAntes / 1024) + "KB -> " + (new File(caminho).length() / 1024) + "KB");
            return true;
        }
        temp.delete();
        return false;
    }
}
