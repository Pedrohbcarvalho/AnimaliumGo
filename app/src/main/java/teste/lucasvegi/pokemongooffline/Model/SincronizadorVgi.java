package teste.lucasvegi.pokemongooffline.Model;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Response;
import teste.lucasvegi.pokemongooffline.Util.BancoDadosSingleton;
import teste.lucasvegi.pokemongooffline.Util.MyApp;
import teste.lucasvegi.pokemongooffline.Util.RedeUtil;
import teste.lucasvegi.pokemongooffline.Util.TokenManager;

// Fila de relatos VGI ("salva primeiro, envia depois").
// Todo relato é gravado no SQLite (tabela relato_pendente) com o login do AUTOR antes de
// qualquer tentativa de envio. Ele só sai do aparelho quando o dono da sessão atual
// (TokenManager.getLogin) é o próprio autor; assim o relato de A nunca é enviado com o token de B.
public final class SincronizadorVgi {

    public enum Resultado {
        ENVIADO,        // servidor confirmou: linha e foto apagadas
        SALVO_OFFLINE,  // sem conexão: fica na fila
        PENDENTE        // servidor respondeu erro (ou relato de outro usuário): fica na fila
    }

    public interface AoTerminar {
        void concluido(Resultado resultado);
    }

    private static final String TABELA = "relato_pendente";
    private static final String PASTA_FOTOS = "vgi_pendentes";

    private static boolean sincronizando = false;

    private SincronizadorVgi() { }

    // Move a foto (já comprimida) para o armazenamento interno do app (o Android não limpa
    // essa pasta sozinho, ao contrário do cache) e grava o relato na fila. Retorna o id, ou -1.
    public static long salvarNaFila(Context ctx, String loginUsuario, int idAnimal,
                                    double latitude, double longitude, File fotoTemporaria) {
        File pasta = new File(ctx.getFilesDir(), PASTA_FOTOS);
        if (!pasta.exists() && !pasta.mkdirs()) return -1;

        File destino = new File(pasta, UUID.randomUUID() + ".jpg");
        if (!moverArquivo(fotoTemporaria, destino)) return -1;

        ContentValues valores = new ContentValues();
        valores.put("loginUsuario", loginUsuario);
        valores.put("idAnimal", idAnimal);
        valores.put("latitude", latitude);
        valores.put("longitude", longitude);
        valores.put("caminhoFoto", destino.getAbsolutePath());
        valores.put("dtCriacao", System.currentTimeMillis());

        long id = BancoDadosSingleton.getInstance().inserir(TABELA, valores);
        if (id == -1) destino.delete();
        return id;
    }

    // Tenta enviar um relato da fila. O callback roda na UI thread (Retrofit).
    public static void enviar(final long id, final AoTerminar aoTerminar) {
        Cursor c = BancoDadosSingleton.getInstance().buscar(TABELA,
                new String[]{"loginUsuario", "idAnimal", "latitude", "longitude", "caminhoFoto"},
                "id = ?", new String[]{String.valueOf(id)}, "");

        if (!c.moveToFirst()) {
            c.close();
            aoTerminar.concluido(Resultado.PENDENTE);
            return;
        }

        String autor = c.getString(c.getColumnIndex("loginUsuario"));
        int idAnimal = c.getInt(c.getColumnIndex("idAnimal"));
        double latitude = c.getDouble(c.getColumnIndex("latitude"));
        double longitude = c.getDouble(c.getColumnIndex("longitude"));
        final File foto = new File(c.getString(c.getColumnIndex("caminhoFoto")));
        c.close();

        // Trava de segurança: o backend define o autor pelo token, então só envia
        // se o token atual pertence ao autor do relato.
        if (!autor.equals(TokenManager.getLogin(MyApp.getAppContext()))) {
            aoTerminar.concluido(Resultado.PENDENTE);
            return;
        }

        if (!foto.exists()) {
            // Sem a foto o servidor recusaria para sempre: remove o registro órfão
            Log.e("VGI_FILA", "Foto do relato " + id + " não existe mais. Removendo da fila.");
            remover(id, foto);
            aoTerminar.concluido(Resultado.PENDENTE);
            return;
        }

        RequestBody arquivo = RequestBody.create(MediaType.parse("image/jpeg"), foto);
        MultipartBody.Part parteFoto = MultipartBody.Part.createFormData("foto", foto.getName(), arquivo);
        MediaType texto = MediaType.parse("text/plain");

        RetrofitClient.getApi().enviarAvistamento(parteFoto,
                RequestBody.create(texto, String.valueOf(idAnimal)),
                RequestBody.create(texto, String.valueOf(latitude)),
                RequestBody.create(texto, String.valueOf(longitude))
        ).enqueue(new retrofit2.Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful()) {
                    remover(id, foto);
                    aoTerminar.concluido(Resultado.ENVIADO);
                } else {
                    // Não descarta em erro: o backend responde 400 também para falhas de
                    // upload no Supabase, e perder o relato do usuário é pior que reenviar.
                    Log.e("VGI_FILA", "Servidor recusou o relato " + id + ": " + response.code());
                    aoTerminar.concluido(Resultado.PENDENTE);
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Log.w("VGI_FILA", "Sem conexão para o relato " + id + ": " + t.getMessage());
                aoTerminar.concluido(Resultado.SALVO_OFFLINE);
            }
        });
    }

    // Envia, um por vez, os relatos pendentes do usuário da sessão atual.
    // Chamado quando o app abre com sessão (MapActivity). Relatos de outros usuários
    // do aparelho ficam guardados até o dono deles entrar.
    public static void sincronizarPendentes() {
        if (sincronizando) return;

        Context ctx = MyApp.getAppContext();
        String login = TokenManager.getLogin(ctx);
        if (login == null || !RedeUtil.temInternet(ctx)) return;

        List<Long> ids = new ArrayList<>();
        Cursor c = BancoDadosSingleton.getInstance().buscar(TABELA, new String[]{"id"},
                "loginUsuario = ?", new String[]{login}, "id asc");
        while (c.moveToNext()) ids.add(c.getLong(0));
        c.close();

        if (ids.isEmpty()) return;

        sincronizando = true;
        Log.i("VGI_FILA", "Sincronizando " + ids.size() + " relato(s) pendente(s).");
        enviarProximo(ids, 0, 0);
    }

    private static void enviarProximo(final List<Long> ids, final int indice, final int enviados) {
        if (indice >= ids.size()) {
            terminarSincronizacao(enviados);
            return;
        }

        enviar(ids.get(indice), new AoTerminar() {
            @Override
            public void concluido(Resultado resultado) {
                if (resultado == Resultado.SALVO_OFFLINE) {
                    // Caiu a conexão: para aqui, o resto tenta na próxima abertura
                    terminarSincronizacao(enviados);
                    return;
                }
                enviarProximo(ids, indice + 1, enviados + (resultado == Resultado.ENVIADO ? 1 : 0));
            }
        });
    }

    private static void terminarSincronizacao(int enviados) {
        sincronizando = false;
        if (enviados > 0) {
            String msg = enviados == 1
                    ? "1 relato salvo offline foi enviado!"
                    : enviados + " relatos salvos offline foram enviados!";
            Toast.makeText(MyApp.getAppContext(), msg, Toast.LENGTH_LONG).show();
        }
    }

    public static int contarPendentes(String login) {
        if (login == null) return 0;
        Cursor c = BancoDadosSingleton.getInstance().buscar(TABELA, new String[]{"id"},
                "loginUsuario = ?", new String[]{login}, "");
        int total = c.getCount();
        c.close();
        return total;
    }

    private static void remover(long id, File foto) {
        BancoDadosSingleton.getInstance().deletar(TABELA, "id = ?", new String[]{String.valueOf(id)});
        if (foto.exists()) foto.delete();
    }

    // renameTo funciona quando origem e destino estão na mesma partição (cache e files do app
    // normalmente estão); se não der, copia e apaga.
    private static boolean moverArquivo(File origem, File destino) {
        if (origem.renameTo(destino)) return true;

        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(origem);
            out = new FileOutputStream(destino);
            byte[] buffer = new byte[8192];
            int lidos;
            while ((lidos = in.read(buffer)) != -1) out.write(buffer, 0, lidos);
            out.close();
            out = null;
            origem.delete();
            return true;
        } catch (Exception e) {
            Log.e("VGI_FILA", "Erro ao mover a foto para a fila", e);
            destino.delete();
            return false;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) { }
            try { if (out != null) out.close(); } catch (Exception ignored) { }
        }
    }
}
