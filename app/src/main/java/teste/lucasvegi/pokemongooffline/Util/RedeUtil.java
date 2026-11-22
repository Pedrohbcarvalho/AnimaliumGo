package teste.lucasvegi.pokemongooffline.Util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class RedeUtil {

    // Checagem rápida (sem tráfego): evita esperar o timeout do OkHttp quando não há rede nenhuma.
    // Rede "conectada" não garante internet (ex.: Wi-Fi sem saída); nesse caso o timeout resolve.
    public static boolean temInternet(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }
}
