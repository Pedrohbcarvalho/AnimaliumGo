package teste.lucasvegi.pokemongooffline.Controller;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import teste.lucasvegi.pokemongooffline.Model.ControladoraFachadaSingleton;
import teste.lucasvegi.pokemongooffline.Model.Pokemon;
import teste.lucasvegi.pokemongooffline.Model.PokemonCapturado;
import teste.lucasvegi.pokemongooffline.R;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import com.google.gson.JsonObject;
import teste.lucasvegi.pokemongooffline.Model.RetrofitClient;

public class VgiActivity extends Activity {

    private boolean activityAtiva = true;
    private ImageButton btnVoltar;
    private ImageView imgFotoAnimal;
    private TextView txtCoordenadas;
    private AutoCompleteTextView autoCompleteAnimais;
    private Button btnEnviarAvistamento;

    private Map<String, Pokemon> mapaNomeAnimal;
    private final int REQUEST_IMAGE_CAPTURE = 100;
    private String caminhoFotoAtual;

    private double latitudeCaptura = 0.0;
    private double longitudeCaptura = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vgi);

        btnVoltar = findViewById(R.id.btnVoltar);
        imgFotoAnimal = findViewById(R.id.imgFotoAnimal);
        txtCoordenadas = findViewById(R.id.txtCoordenadas);
        autoCompleteAnimais = findViewById(R.id.autoCompleteAnimais);
        btnEnviarAvistamento = findViewById(R.id.btnEnviarAvistamento);

        btnVoltar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                carregarAnimaisNoAutoCompleteAssincrono();
            }
        }).start();

        btnEnviarAvistamento.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enviarAvistamento();
            }
        });

        File photoFile = new File(getCacheDir(), "foto_avistamento.jpg");
        caminhoFotoAtual = photoFile.getAbsolutePath();

        // Se for nulo, significa que a tela está sendo aberta vinda do mapa
        if (savedInstanceState == null) {
            if (photoFile.exists()) {
                photoFile.delete();
            }
            abrirCamera();
        } else {
            // Se não for nulo, o Android está reconstruindo a tela
            exibirFotoCapturada();
        }

        latitudeCaptura = getIntent().getDoubleExtra("latitude", 0.0);
        longitudeCaptura = getIntent().getDoubleExtra("longitude", 0.0);

        if (latitudeCaptura != 0.0 && longitudeCaptura != 0.0) {
            String coords = String.format("Lat: %.5f\nLng: %.5f", latitudeCaptura, longitudeCaptura);
            txtCoordenadas.setText(coords);
            txtCoordenadas.setVisibility(View.VISIBLE);
        } else {
            txtCoordenadas.setText("Buscando GPS...");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        activityAtiva = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityAtiva = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        activityAtiva = false;

        if (caminhoFotoAtual != null) {
            File f = new File(caminhoFotoAtual);
            if (f.exists()) f.delete();
        }
    }

    private void abrirCamera() {
        Intent takePictureIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            try {
                File photoFile = new File(caminhoFotoAtual);
                android.net.Uri photoURI = androidx.core.content.FileProvider.getUriForFile(this,
                        "teste.lucasvegi.pokemongooffline.fileprovider", photoFile);

                takePictureIntent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, photoURI);
                takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            } catch (Exception e) {
                Log.e("VGI", "Erro ao criar ficheiro para câmara: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE) {
            if (resultCode == RESULT_OK) {
                exibirFotoCapturada();
            } else {
                finish();
            }
        }
    }

    private void exibirFotoCapturada() {
        File f = new File(caminhoFotoAtual);
        if (f.exists()) {
            Bitmap fotoCorrigida = carregarEProcessarFoto(caminhoFotoAtual);
            if (fotoCorrigida != null) {
                imgFotoAnimal.setImageBitmap(fotoCorrigida);
            }
        }
    }

    private Bitmap carregarEProcessarFoto(String caminho) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(caminho, options);

            int maxSize = 1024;
            int scale = 1;
            while (options.outWidth / scale / 2 >= maxSize || options.outHeight / scale / 2 >= maxSize) {
                scale *= 2;
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = scale;
            Bitmap bitmapLeve = BitmapFactory.decodeFile(caminho, options);

            ExifInterface exif = new ExifInterface(caminho);
            int orientacao = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);

            Matrix matrix = new Matrix();
            switch (orientacao) {
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

            if (bitmapLeve != null) {
                return Bitmap.createBitmap(bitmapLeve, 0, 0, bitmapLeve.getWidth(), bitmapLeve.getHeight(), matrix, true);
            }
        } catch (Exception e) {
            Log.e("VGI", "Erro no processamento EXIF", e);
        }
        return null;
    }

    private void carregarAnimaisNoAutoCompleteAssincrono() {
        try {
            // Verifica se o utilizador ainda está na memória após usar a câmera
            if (ControladoraFachadaSingleton.getInstance().getUsuario() == null) {
                boolean restaurouSessao = ControladoraFachadaSingleton.getInstance().temSessao();

                if (!restaurouSessao) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(VgiActivity.this, "Sessão expirada. A regressar...", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });
                    return;
                }
            }

            Map<Pokemon, List<PokemonCapturado>> pokemonsDoUsuario = ControladoraFachadaSingleton.getInstance().getUsuario().getPokemons();

            final Map<String, Pokemon> mapaTemp = new HashMap<>();
            final List<String> nomesAnimais = new ArrayList<>();

            if (pokemonsDoUsuario != null) {
                for (Pokemon p : pokemonsDoUsuario.keySet()) {
                    mapaTemp.put(p.getNome(), p);
                    nomesAnimais.add(p.getNome());
                }
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!activityAtiva) return;

                    mapaNomeAnimal = mapaTemp;
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(VgiActivity.this, android.R.layout.simple_dropdown_item_1line, nomesAnimais);
                    autoCompleteAnimais.setAdapter(adapter);

                    autoCompleteAnimais.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            autoCompleteAnimais.showDropDown();
                        }
                    });
                }
            });
        } catch (Exception e) {
            Log.e("VGI", "Erro ao carregar lista de animais", e);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    finish();
                }
            });
        }
    }

    private void enviarAvistamento() {
        if (mapaNomeAnimal == null) return;

        String nomeDigitado = autoCompleteAnimais.getText().toString();
        Pokemon animalEscolhido = mapaNomeAnimal.get(nomeDigitado);

        if (animalEscolhido == null) {
            Toast.makeText(this, "Por favor, busque e selecione um animal válido da sua lista.", Toast.LENGTH_SHORT).show();
            return;
        }

        File file = new File(caminhoFotoAtual);
        if (!file.exists()) {
            Toast.makeText(this, "Tire uma foto antes de enviar.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Prepara a foto para upload
        RequestBody requestFile = RequestBody.create(MediaType.parse("image/jpeg"), file);
        // O parâmetro "file" deve bater com o nome do campo configurado no Multer
        MultipartBody.Part bodyFoto = MultipartBody.Part.createFormData("foto", file.getName(), requestFile);

        // Prepara os textos com as chaves exatas do backend
        RequestBody reqIdAnimal = RequestBody.create(MediaType.parse("text/plain"), "2");
        // RequestBody reqIdAnimal = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(animalEscolhido.getNumero()));
        RequestBody reqLat = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(latitudeCaptura));
        RequestBody reqLng = RequestBody.create(MediaType.parse("text/plain"), String.valueOf(longitudeCaptura));

        Toast.makeText(this, "Enviando avistamento...", Toast.LENGTH_SHORT).show();

        // Envia para a nuvem
        RetrofitClient.getApi().enviarAvistamento(bodyFoto, reqIdAnimal, reqLat, reqLng).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(VgiActivity.this, "Registro enviado com sucesso!", Toast.LENGTH_LONG).show();
                    file.delete(); // Limpa a foto temporária
                    finish();
                } else {
                    Toast.makeText(VgiActivity.this, "Erro no servidor: " + response.code(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                // Futuramente, a fila Offline-First será implementada aqui
                Toast.makeText(VgiActivity.this, "Falha de rede. O avistamento não foi enviado.", Toast.LENGTH_LONG).show();
            }
        });
    }
}