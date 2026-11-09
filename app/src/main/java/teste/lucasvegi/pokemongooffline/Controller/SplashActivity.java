package teste.lucasvegi.pokemongooffline.Controller;

import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.net.PlacesClient;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import teste.lucasvegi.pokemongooffline.Model.ControladoraFachadaSingleton;
import teste.lucasvegi.pokemongooffline.R;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.util.List;
import android.util.Log;
import teste.lucasvegi.pokemongooffline.Model.RetrofitClient;
import teste.lucasvegi.pokemongooffline.Model.Animal;

public class SplashActivity extends Activity {

    private final int LOCATION_PERMISSION = 1;
    boolean permissao_local = false;
    private WebView webView;
    private TextView versaoApp;
    private static int SPLASH_TIME_OUT = 6000;
    private MediaPlayer mp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), "AIzaSyDATgN7KkYfXdALYKaxzvez087PHB6Xtm4");
        }

        RetrofitClient.getApi().getAnimais().enqueue(new Callback<List<Animal>>() {
            @Override
            public void onResponse(Call<List<Animal>> call, Response<List<Animal>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Log.d("TESTE_RETROFIT", "Sucesso! Animais recebidos: " + response.body().size());
                    for (Animal a : response.body()) {
                        Log.d("TESTE_RETROFIT", "Animal: " + a.getNomePopular());
                    }
                } else {
                    Log.e("TESTE_RETROFIT", "Erro na resposta: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<Animal>> call, Throwable t) {
                Log.e("TESTE_RETROFIT", "Falha de conexão: " + t.getMessage());
            }
        });

        configuraViewInicio();
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_PERMISSION);
        else
            configuraSomAbertura();
        //espera um tempo antes de ir para a proxima tela
        /*new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent i = new Intent(getApplicationContext(), MapActivity.class);
                startActivity(i);
                finish();
            }
        }, SPLASH_TIME_OUT);*/

    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    protected void configuraViewInicio(){
        //Mantem a tela de splash ligada enquanto ela é exibida.
        WindowManager.LayoutParams params = this.getWindow().getAttributes();
        params.flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;

        //Configura web view loader carregamento do app
        webView = (WebView) findViewById(R.id.loaderSplash);
        webView.loadUrl("file:///android_asset/loading.gif");
        webView.setBackgroundColor(Color.TRANSPARENT);

        //exibe versão do app dinamicamente
        versaoApp = (TextView) findViewById(R.id.versaoApp);

        //OBTEM A VERSÃO DO APLICATIVO
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = pInfo.versionName;
            versaoApp.setText("v. " + version);
        }catch (Exception e){
            Log.e("SPLASH","ERRO: " + e.getMessage());
        }
    }

    protected void configuraSomAbertura(){
        try {
            mp = MediaPlayer.create(this, R.raw.abertura2);
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mediaPlayer) {
                    //Executa quando a musica de abertura acaba
                    if(ControladoraFachadaSingleton.getInstance().temSessao()) {
                        Intent i = new Intent(getBaseContext(), MapActivity.class);
                        startActivity(i);
                        finish();
                    }else {
                        Intent i = new Intent(getBaseContext(), LoginActivity.class);
                        startActivity(i);
                        finish();
                    }
                }
            });
            mp.start();
        }catch (Exception e){
            Log.e("SPLASH","ERRO: " + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case LOCATION_PERMISSION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permissão concedida", 5000);
                    Log.d("PERMISSAO", "DEIXOU USAR CAMERA");
                    permissao_local=true;
                    configuraSomAbertura();
                }
                else {
                    Toast.makeText(this, "Permissão Necessária para saber seu local", 5000);
                    Log.d("PERMISSAO", "NAO DEIXOU USAR GPS");
                    permissao_local=true;
                    configuraSomAbertura();
                }
            }
        }
    }
}
