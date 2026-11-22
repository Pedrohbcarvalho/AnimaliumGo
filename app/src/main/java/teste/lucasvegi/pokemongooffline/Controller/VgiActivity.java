package teste.lucasvegi.pokemongooffline.Controller;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import teste.lucasvegi.pokemongooffline.Model.ControladoraFachadaSingleton;
import teste.lucasvegi.pokemongooffline.Model.Pokemon;
import teste.lucasvegi.pokemongooffline.Model.PokemonCapturado;
import teste.lucasvegi.pokemongooffline.Model.SincronizadorVgi;
import teste.lucasvegi.pokemongooffline.Util.BitmapUtil;
import teste.lucasvegi.pokemongooffline.R;
import teste.lucasvegi.pokemongooffline.View.CameraPreview;



public class VgiActivity extends Activity {

    private boolean activityAtiva = true;
    private ImageButton btnVoltar;
    private ImageView imgFotoAnimal;
    private TextView txtCoordenadas;
    private AutoCompleteTextView autoCompleteAnimais;
    private Button btnEnviarAvistamento;

    private Map<String, Pokemon> mapaNomeAnimal;
    private String caminhoFotoAtual;

    private double latitudeCaptura = 0.0;
    private double longitudeCaptura = 0.0;

    // Evitam decodificar a mesma foto duas vezes
    private boolean carregandoFoto = false;
    private boolean fotoExibida = false;

    // Câmera DENTRO do app (mesma CameraPreview da CapturaActivity).
    // O app nunca vai para segundo plano, então o Android não o mata e não há tela branca ao voltar.
    private final int CAMERA_PERMISSION = 200;
    private FrameLayout frameCamera;
    private Button btnTirarFoto;
    private CameraPreview cameraPreview; // != null enquanto o preview está na tela
    private boolean tirandoFoto = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vgi);

        btnVoltar = findViewById(R.id.btnVoltar);
        imgFotoAnimal = findViewById(R.id.imgFotoAnimal);
        txtCoordenadas = findViewById(R.id.txtCoordenadas);
        autoCompleteAnimais = findViewById(R.id.autoCompleteAnimais);
        btnEnviarAvistamento = findViewById(R.id.btnEnviarAvistamento);
        frameCamera = findViewById(R.id.cameraPreviewVgi);
        btnTirarFoto = findViewById(R.id.btnTirarFoto);

        btnTirarFoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cameraPreview != null) {
                    tirarFoto();   // preview aberto: captura
                } else {
                    tirarOutra();  // foto na tela: descarta e reabre o preview
                }
            }
        });

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

        // Tela aberta vinda do mapa: descarta qualquer foto que tenha sobrado de um uso anterior
        if (savedInstanceState == null && photoFile.exists()) {
            photoFile.delete();
        }

        // Se já existe foto (tela reconstruída pelo Android), mostra ela; senão, abre o preview
        if (photoFile.exists()) {
            exibirFotoCapturada();
        } else {
            iniciarCamera();
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

        // Só apaga a foto quando o usuário realmente sai da tela.
        // Em recriações (rotação, sistema destruindo a Activity) a foto precisa continuar existindo.
        if (isFinishing() && caminhoFotoAtual != null) {
            File f = new File(caminhoFotoAtual);
            if (f.exists()) f.delete();
        }
    }

    // Coloca o preview da câmera dentro do card da foto.
    // A CameraPreview abre a câmera sozinha quando aparece e a libera sozinha quando sai da tela
    // (inclusive se o usuário minimizar o app), igual na CapturaActivity.
    private void iniciarCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
            return; // continua no onRequestPermissionsResult
        }
        if (cameraPreview != null) return;

        cameraPreview = new CameraPreview(this);
        frameCamera.addView(cameraPreview);
        frameCamera.setVisibility(View.VISIBLE);
        btnTirarFoto.setText("CAPTURAR");
    }

    // Tirar o preview da tela faz a CameraPreview liberar a câmera (surfaceDestroyed)
    private void pararCamera() {
        if (cameraPreview != null) {
            frameCamera.removeView(cameraPreview);
            cameraPreview = null;
        }
        frameCamera.setVisibility(View.GONE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                iniciarCamera();
            } else {
                Toast.makeText(this, "Permita o uso da câmera para registrar o avistamento.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void tirarFoto() {
        if (tirandoFoto || cameraPreview == null) return;
        tirandoFoto = true;
        btnTirarFoto.setEnabled(false); // evita toque duplo

        boolean disparou = cameraPreview.tirarFoto(new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] dados, Camera camera) {
                salvarFotoEmBackground(dados);
            }
        });

        if (!disparou) {
            tirandoFoto = false;
            btnTirarFoto.setEnabled(true);
            Toast.makeText(this, "A câmera ainda está iniciando. Tente de novo.", Toast.LENGTH_SHORT).show();
        }
    }

    // Grava o JPEG no MESMO arquivo que o envio já usa (foto_avistamento.jpg), fora da UI Thread
    private void salvarFotoEmBackground(final byte[] dados) {
        final String caminho = caminhoFotoAtual;

        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean salvou = false;
                FileOutputStream out = null;
                try {
                    out = new FileOutputStream(caminho);
                    out.write(dados);
                    salvou = true;
                } catch (Exception e) {
                    Log.e("VGI", "Erro ao salvar a foto", e);
                } finally {
                    if (out != null) {
                        try { out.close(); } catch (Exception ignored) { }
                    }
                }

                // Regrava já leve (~200KB): é este arquivo que vai para a fila e para o bucket.
                // Se a compressão falhar, o JPEG original continua válido para envio.
                if (salvou) {
                    BitmapUtil.comprimirParaUpload(caminho);
                }

                final boolean ok = salvou;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        tirandoFoto = false;
                        btnTirarFoto.setEnabled(true);
                        if (isFinishing() || isDestroyed()) return;

                        // Depois do takePicture o preview para; libera a câmera de qualquer forma
                        pararCamera();

                        if (ok) {
                            exibirFotoCapturada(); // decodifica em background e mostra no card
                        } else {
                            Toast.makeText(VgiActivity.this, "Não foi possível salvar a foto. Tente de novo.", Toast.LENGTH_SHORT).show();
                            iniciarCamera();
                        }
                    }
                });
            }
        }).start();
    }

    // Descarta a foto atual e volta para o preview
    private void tirarOutra() {
        if (carregandoFoto) return; // a foto anterior ainda está sendo exibida

        File f = new File(caminhoFotoAtual);
        if (f.exists()) f.delete();

        imgFotoAnimal.setImageDrawable(null);
        fotoExibida = false;
        iniciarCamera();
    }

    private void exibirFotoCapturada() {
        // Já está carregando ou já exibiu: não decodifica de novo
        if (carregandoFoto || fotoExibida) return;

        final String caminho = caminhoFotoAtual;
        if (caminho == null || !new File(caminho).exists()) return;

        carregandoFoto = true;

        // Decodificação pesada fora da UI Thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap fotoCorrigida = BitmapUtil.decodificarComRotacao(caminho, 1024);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        carregandoFoto = false;

                        // A Activity pode ter sido fechada enquanto a foto era processada
                        if (isFinishing() || isDestroyed()) {
                            if (fotoCorrigida != null) fotoCorrigida.recycle();
                            return;
                        }

                        if (fotoCorrigida != null) {
                            pararCamera(); // garante que o preview não fique por cima da foto
                            imgFotoAnimal.setImageBitmap(fotoCorrigida);
                            fotoExibida = true;
                            btnTirarFoto.setText("TIRAR OUTRA");
                        } else {
                            iniciarCamera(); // arquivo inválido: volta para o preview
                        }
                    }
                });
            }
        }).start();
    }

    private void carregarAnimaisNoAutoCompleteAssincrono() {
        try {
            // Verifica se o utilizador ainda está na memória após usar a câmera
            ControladoraFachadaSingleton controladora = ControladoraFachadaSingleton.getInstance();
            boolean sessaoOk;
            // Mesmo lock usado na MapActivity: se as duas telas restaurarem a sessão ao mesmo
            // tempo, as listas internas da controladora (ovos, doces) podem ser corrompidas
            synchronized (controladora) {
                sessaoOk = controladora.getUsuario() != null || controladora.temSessao();
            }

            if (!sessaoOk) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(VgiActivity.this, "Sessão expirada. A regressar...", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
                return;
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
                    // Antes era "if (!activityAtiva) return;". A câmera abre logo no onCreate, a tela
                    // entra em onPause e a lista era DESCARTADA: AutoComplete sem adapter e o envio
                    // saía cedo por mapaNomeAnimal == null. Só descarta se a tela estiver morrendo.
                    if (isFinishing() || isDestroyed()) return;

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

        // A foto ainda está sendo gravada/comprimida em background
        if (tirandoFoto || carregandoFoto) {
            Toast.makeText(this, "Aguarde a foto ficar pronta.", Toast.LENGTH_SHORT).show();
            return;
        }

        File file = new File(caminhoFotoAtual);
        if (!file.exists()) {
            Toast.makeText(this, "Tire uma foto antes de enviar.", Toast.LENGTH_SHORT).show();
            return;
        }

        // TODO (pré-existente): enviar o id real -> animalEscolhido.getNumero()
        int idAnimal = 2;

        // "Salva primeiro, envia depois": o relato vai para a fila local amarrado ao autor.
        // Se não houver internet, ele é enviado na próxima vez que este usuário abrir o app.
        String autor = ControladoraFachadaSingleton.getInstance().getUsuario().getLogin();
        long idRelato = SincronizadorVgi.salvarNaFila(this, autor, idAnimal, latitudeCaptura, longitudeCaptura, file);
        if (idRelato == -1) {
            Toast.makeText(this, "Não foi possível salvar o relato. Tente de novo.", Toast.LENGTH_LONG).show();
            return;
        }

        btnEnviarAvistamento.setEnabled(false); // evita envio duplicado
        Toast.makeText(this, "Enviando avistamento...", Toast.LENGTH_SHORT).show();

        SincronizadorVgi.enviar(idRelato, new SincronizadorVgi.AoTerminar() {
            @Override
            public void concluido(SincronizadorVgi.Resultado resultado) {
                String msg;
                switch (resultado) {
                    case ENVIADO:
                        msg = "Registro enviado com sucesso!";
                        break;
                    case SALVO_OFFLINE:
                        msg = "Sem conexão. O relato foi salvo e será enviado automaticamente.";
                        break;
                    default:
                        msg = "O servidor não aceitou agora. O relato foi salvo e será reenviado depois.";
                        break;
                }
                Toast.makeText(VgiActivity.this, msg, Toast.LENGTH_LONG).show();
                if (!isFinishing() && !isDestroyed()) finish();
            }
        });
    }
}
