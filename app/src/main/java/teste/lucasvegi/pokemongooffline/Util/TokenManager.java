package teste.lucasvegi.pokemongooffline.Util;

import android.content.Context;
import android.content.SharedPreferences;

// Guarda a sessão do usuário: access token (curto, ~1h), refresh token (longo, uso único)
// e o login do DONO desses tokens. A senha NUNCA é salva no aparelho.
// Ter um refresh token salvo = existe sessão (o app abre direto no mapa, mesmo offline).
public class TokenManager {
    private static final String PREF_NAME = "AppSessao";
    private static final String KEY_TOKEN = "jwt_token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_LOGIN = "login_sessao";

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // commit() (síncrono): o Authenticator lê os tokens logo em seguida em outra thread
    public static void salvarSessao(Context context, String accessToken, String refreshToken, String login) {
        prefs(context).edit()
                .putString(KEY_TOKEN, accessToken)
                .putString(KEY_REFRESH, refreshToken)
                .putString(KEY_LOGIN, login)
                .commit();
    }

    // Usado na renovação: troca só os tokens, o dono continua o mesmo
    public static void atualizarTokens(Context context, String accessToken, String refreshToken) {
        prefs(context).edit()
                .putString(KEY_TOKEN, accessToken)
                .putString(KEY_REFRESH, refreshToken)
                .commit();
    }

    public static String getToken(Context context) {
        return prefs(context).getString(KEY_TOKEN, null);
    }

    public static String getRefreshToken(Context context) {
        return prefs(context).getString(KEY_REFRESH, null);
    }

    // Login (e-mail) do usuário a quem os tokens pertencem
    public static String getLogin(Context context) {
        return prefs(context).getString(KEY_LOGIN, null);
    }

    public static boolean temSessao(Context context) {
        return getRefreshToken(context) != null && getLogin(context) != null;
    }

    public static void limpar(Context context) {
        prefs(context).edit().clear().commit();
    }
}
