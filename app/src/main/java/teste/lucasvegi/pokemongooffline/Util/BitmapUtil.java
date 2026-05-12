package teste.lucasvegi.pokemongooffline.Util;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

public class BitmapUtil {

    public static BitmapDescriptor getBitmapDescriptor(Resources res, int resId, int width, int height) {
        Bitmap imageBitmap = BitmapFactory.decodeResource(res, resId);
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(imageBitmap, width, height, true);
        return BitmapDescriptorFactory.fromBitmap(resizedBitmap);
    }
}