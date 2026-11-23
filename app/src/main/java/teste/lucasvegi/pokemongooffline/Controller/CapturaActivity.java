package teste.lucasvegi.pokemongooffline.Controller;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Point;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.RotateAnimation;
import android.widget.AbsoluteLayout;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;

import teste.lucasvegi.pokemongooffline.Model.Animal;
import teste.lucasvegi.pokemongooffline.Model.Aparecimento;
import teste.lucasvegi.pokemongooffline.Model.ControladoraFachadaSingleton;
import teste.lucasvegi.pokemongooffline.R;
import teste.lucasvegi.pokemongooffline.Util.ImagemRemotaUtil;
import teste.lucasvegi.pokemongooffline.Util.RandomUtil;
import teste.lucasvegi.pokemongooffline.Util.ViewUnitsUtil;
import teste.lucasvegi.pokemongooffline.View.CameraPreview;
import teste.lucasvegi.pokemongooffline.View.MiraEnquadramentoView;


public class CapturaActivity extends Activity implements SensorEventListener {
    // ===== CAPTURA POR ENQUADRAMENTO (etapa 1) =====
    // CHAVE DE REVERSÃO: true = catalogar mantendo o animal no círculo central;
    // false = fluxo original da Pokébola (arrastar/arremessar), que continua intacto abaixo.
    private static final boolean MODO_CAPTURA_ENQUADRAMENTO = true;
    // 3 s: varrer a cena passa pelo círculo em < 0,5 s, então exige intenção, mas não cansa o braço
    private static final long TEMPO_ENQUADRAMENTO_MS = 3000;
    private static final float FRACAO_RAIO_MIRA = 0.20f;      //raio do círculo = 20% da largura (~±14° com escalaX = dimenX/72)
    private static final float FRACAO_DESLOCAMENTO_INICIAL = 1.6f; //animal nasce a 1,6 raio do centro: fora do círculo, mas na tela
    private static final long INTERVALO_VERIFICACAO_MS = 50;

    private MiraEnquadramentoView mira;
    private final Handler handlerEnquadramento = new Handler();
    private float raioMira;
    private long tempoEnquadradoMs = 0;
    private long ultimaVerificacao = 0;
    private boolean deslocamentoInicialAplicado = false; //posição inicial do animal já definida (etapa 1 ou 2)

    // ----- etapa 2: cada regra tem a sua chave; false desliga só aquela regra -----
    private static final boolean SPAWN_AO_REDOR = true;      //false = posicionamento da etapa 1 (na tela, giroscópio antigo)
    private static final boolean FUGA_POR_DEMORA = true;     //só tem efeito com SPAWN_AO_REDOR
    private static final boolean LIMITE_TEMPO_ENCONTRO = true;
    private static final boolean SETA_DIRECAO = true;
    private static final boolean TAMANHO_FIXO_NO_ENQUADRAMENTO = true; //false = volta o "zoom" por acelerômetro

    private static final float AZIMUTE_MIN_SPAWN = 20f;  //graus; o círculo cobre ~14°, então nunca nasce enquadrado
    private static final float ELEVACAO_MIN = -10f;      //abaixo disso pareceria "aos pés" do jogador
    private static final float ELEVACAO_MAX = 15f;       //acima disso pareceria "no céu"
    private static final long TEMPO_PARA_FUGIR_MS = 10000;     //tempo FORA do círculo até o animal se assustar
    private static final float SALTO_FUGA_MIN = 60f;     //graus: o bastante para sair do campo de visão (~72°)
    private static final float SALTO_FUGA_MAX = 120f;
    private static final long DURACAO_ANIMACAO_FUGA_MS = 800;
    private static final long TEMPO_MAXIMO_ENCONTRO_MS = 30000;
    private static final long TEMPO_AVISO_FINAL_MS = 5000;     //cronômetro fica vermelho
    private static final double ZONA_MORTA_GIRO_RAD_S = 0.02;  //ignora ruído do giroscópio com o celular parado
    private static final double DT_MAXIMO_GIRO_S = 0.1;        //descarta o 1º evento após pausa (dt enorme)

    //posição do animal "no mundo", em graus, relativa à direção do celular quando a tela abriu
    private float azimuteAnimal = 0;    //+ = à direita
    private float elevacaoAnimal = 0;   //+ = acima
    //rotação acumulada do celular (mesmos eixos/sinais do giroscópio antigo)
    private float yawAcumulado = 0;     //+ = virou para a esquerda
    private float pitchAcumulado = 0;   //+ = câmera inclinada para cima
    private float rollAcumulado = 0;
    private long tempoForaDoCirculoMs = 0;
    private long tempoEncontroMs = 0;
    private boolean encontroEncerrado = false;  //o animal fugiu por tempo (sem registro)
    private ValueAnimator animacaoFuga;
    // ===== FIM CAPTURA POR ENQUADRAMENTO (campos) =====

    private SensorManager sensorManager;
    private Sensor sensor;
    private Sensor accelerometer;
    public ImageView img;
    public ImageView pokebola;
    public int dimenX;  //dimensao horizontal da tela em pixel
    public int dimenY;  //dimensao vertical da tela em pixel
    public float centerX;   //centro horizontal ajustado
    public float centerY;   //centro vertical ajustado
    public float escalaX;   //usada para converter leituras em pixel
    public float escalaY;   //usada para converter leituras em pixel

    public float centerXpokeball;   //centro horizontal ajustado
    public float centerYpokeball;   //centro vertical ajustado

    public float grauXtotal = 0;
    public float grauYtotal = 0;
    public float grauZtotal = 0;

    public float grauXnovo = 0;
    public float grauYnovo = 0;
    public float grauZnovo = 0;

    public float grauXant = 0;
    public float grauYant = 0;
    public float grauZant = 0;

    float distanciaTopoY;
    float distanciaBaseY;
    float distanciaEsquerdaX;
    float distanciaDireitaX;

    public float percentImagePokemon = (float) 0.5;
    public boolean imagemPokemonPreparada = false;
    public float larguraImgPokemon = 0;
    public float alturaImgPokemon = 0;
    public float[] limitesPokemon;

    public float percentImagePokeball = (float) 0.15;
    public boolean imagemPokeballPreparada = false;
    public float larguraImgPokeball = 0;
    public float alturaImgPokeball = 0;
    public float[] limitesPokeball;

    public boolean capturou = false;

    public float xInicioTouch = 0;
    public float yInicioTouch = 0;
    public float xFimTouch = 0;
    public float yFimTouch = 0;
    public long tempoInicial = 0;
    public long tempoFinal = 0;
    float diferencaX;
    float diferencaY;
    long duracaoTouch;
    float velocidadeX; //pixel por milisegundo
    float velocidadeY; //pixel por milisegundo
    float velocidadeXoriginal;
    float velocidadeYoriginal;
    //public DeslocamentoPokebola deslocamentoPokebola;

    public MediaPlayer mp;
    public int countSound = 0; // initialise outside listener to prevent looping

    private CameraPreview mPreview;
    private Camera mCamera;

    private TextView nomePkmnCaptura;

    private Animal animal;
    private Aparecimento ap;

    private MediaPlayer mpBattle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //setContentView(R.layout.view_captura);
        preparaCamera();

        //Obtem gerenciador de sensores
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        //Obtem sensores a serem utilizados
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        //Obtem a resolução da tela
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        dimenX = size.x;
        dimenY = size.y;

        //Toast.makeText(this, "X: " + dimenX + "px Y: " + dimenY + "px", Toast.LENGTH_LONG).show();

        //Inicia tema da batalha pokemon
        mpBattle = MediaPlayer.create(getBaseContext(), R.raw.battle);
        mpBattle.setLooping(true);
        atribuirVolumeMediaPlayer(mpBattle, 90); //diminui o volume do tema da batalha para compensar audio alto
        mpBattle.start();
    }

    @Override
    protected void onResume() {
        super.onResume();

        //Recebe Intent com o Aparecimento (animal real) vindo do mapa
        Intent it = getIntent();
        ap = (Aparecimento) it.getSerializableExtra("aparecimento");
        animal = ap.getAnimal();

        //Define label avisando se o animal é conhecido ou novo. Se as capturas do jogador
        //ainda não carregaram do servidor, esconde o rótulo em vez de arriscar um "novo" errado.
        TextView labelPkmnNovo = (TextView) findViewById(R.id.labelPkmnNovo);
        ControladoraFachadaSingleton controladora = ControladoraFachadaSingleton.getInstance();
        if (!controladora.isCapturasCarregadas())
            labelPkmnNovo.setVisibility(View.INVISIBLE);
        else if (controladora.isAnimalCapturado(animal.getId()))
            labelPkmnNovo.setText("conhecido");
        else
            labelPkmnNovo.setText("novo");

        //seta label com o nome do animal a ser capturado
        nomePkmnCaptura = (TextView) findViewById(R.id.txtNomePkmnCaptura);
        //garante só rodar após as views estarem na tela
        nomePkmnCaptura.post(new Runnable() {
            @Override
            public void run() {
                nomePkmnCaptura.setText(animal.getNomePopular());
                nomePkmnCaptura.measure(0,0);
                //Posiciona o nome na lateral superior direita com margem de 8dp à direita
                nomePkmnCaptura.setX(dimenX - nomePkmnCaptura.getMeasuredWidth() - ViewUnitsUtil.convertDpToPixel(8));
            }
        });

        img = (ImageView) findViewById(R.id.pokemon);
        pokebola = (ImageView) findViewById(R.id.pokeball);

        if (MODO_CAPTURA_ENQUADRAMENTO) {
            configuraEnquadramento();
            //o posicionamento é postado depois do runnable de configuraPokemon(), então roda já com o tamanho calculado
            ImagemRemotaUtil.carregarFotoGrande(img, animal.getFotoUrl(), () -> {
                configuraPokemon();
                if (SPAWN_AO_REDOR)
                    sorteiaPosicaoAoRedor();     //etapa 2: em qualquer direção (360°)
                else
                    posicionaAnimalForaDaMira(); //etapa 1: deslocado, mas na tela
            });
            iniciaVerificacaoEnquadramento();
        } else {
            configuraPokebola();

            //Ícone GRANDE: a mesma foto do pin do mapa, decodificada no tamanho da tela.
            //configuraPokemon() mede a imagem real, então só roda depois que ela carregar.
            ImagemRemotaUtil.carregarFotoGrande(img, animal.getFotoUrl(), this::configuraPokemon);
        }

        if(sensor != null){
            //Começa a escutar os sensores utilizados
            sensorManager.registerListener(this, sensor,SensorManager.SENSOR_DELAY_GAME);
        }

        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        //Para de escutar os sensores
        sensorManager.unregisterListener(this);
        handlerEnquadramento.removeCallbacks(verificaEnquadramento); //só a verificação: um finish() já agendado continua valendo
        imagemPokemonPreparada = false;
        imagemPokeballPreparada = false;

        //pause a musica da batalha
        mpBattle.pause();

        //mPreview.releaseCamera();
        //releaseCamera();
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        //continua a musica da batalha
        mpBattle.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        //para a musica da batalha e devolve o recurso para o sistema
        mpBattle.pause();
        mpBattle.release();

        if (animacaoFuga != null) animacaoFuga.cancel(); //enquadramento: não animar uma view de tela já fechada
    }

    private void animate(double fromDegrees, double toDegrees, long durationMillis, ImageView img) {
        final RotateAnimation rotate = new RotateAnimation((float) fromDegrees, (float) toDegrees,
                RotateAnimation.RELATIVE_TO_SELF, 0.5f,
                RotateAnimation.RELATIVE_TO_SELF, 0.5f);
        img.clearAnimation();
        rotate.setDuration(durationMillis);
        rotate.setFillEnabled(true);
        rotate.setFillAfter(true);
        img.startAnimation(rotate);
    }

    private HashMap<Integer, Long> timestamp = new HashMap<>();

    private double getSensorElapsedSeconds(SensorEvent event) {
        Long lastTimestamp = timestamp.put(event.sensor.getType(), event.timestamp);

        if (lastTimestamp == null)
            return 0;

        return (event.timestamp - lastTimestamp) / 1000000000f;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_GYROSCOPE:
                onGyroscopeChanged(event);
                break;
            case Sensor.TYPE_LINEAR_ACCELERATION:
                onAccelerationChanged(event);
                break;
            default:
                throw new IllegalStateException("Unreachable");
        }

    }

    double accelerationNoise, speed, distance;
    long accelerationSamples;

    private double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }

    private void onAccelerationChanged(SensorEvent event) {
        //enquadramento: tamanho fixo (o zoom abaixo deriva e encolhe/aumenta o animal sozinho)
        if (MODO_CAPTURA_ENQUADRAMENTO && TAMANHO_FIXO_NO_ENQUADRAMENTO) return;

        double elapsed = getSensorElapsedSeconds(event);

        double accelerationSensor = -event.values[2];
        double accelerationSensorMagnitude = Math.abs(accelerationSensor);
        double accelerationSensorDirection = Math.signum(accelerationSensor);

        accelerationNoise += (1 / ++accelerationSamples) * (accelerationSensorMagnitude - accelerationNoise);

        if (isCenaPronta()) {
            // Atenua o ruído na aceleração.
            double acceleration = Math.max(accelerationSensorMagnitude - accelerationNoise, 0) * accelerationSensorDirection;

            speed = clamp(speed + acceleration * elapsed, -0.25f, +0.25f);
            distance = clamp(distance + speed * elapsed, 0.25f, 0.75f);

            percentImagePokemon = 1f - (float) distance;
            configuraPokemon();

            Log.i("Accel", String.format("A=%.1f S=%.1f D=%.1f N=%.1f", acceleration, speed, distance, accelerationNoise));
        }
    }

    public void onGyroscopeChanged(SensorEvent event) {
        //enquadramento etapa 2: posição angular (360°); o código antigo abaixo segue intacto para os outros modos
        if (MODO_CAPTURA_ENQUADRAMENTO && SPAWN_AO_REDOR) {
            atualizaPosicaoAngular(event);
            return;
        }

        if(isCenaPronta()) {
            float xNovo = img.getX();
            float yNovo = img.getY();

            //mantem tela ligada
            WindowManager.LayoutParams params = this.getWindow().getAttributes();
            params.flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
            this.getWindow().setAttributes(params);

            //Leitura de valores acelerômetro - USAR QUANDO ORIENTAÇÃO FOR TRAVADA EM PORTRAIT
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            //obtem graus deslocados desde a ultima medição
            grauXnovo = (float) ((x * 57.2958) * 0.02); //0.02 segundos devido ao SENSOR_DELAY_GAME
            grauYnovo = (float) ((y * 57.2958) * 0.02); //0.02 segundos devido ao SENSOR_DELAY_GAME
            grauZnovo = (float) ((z * 57.2958) * 0.02); //0.02 segundos devido ao SENSOR_DELAY_GAME

            //total de graus deslocados desde o inicio
            grauXtotal += grauXnovo;
            grauYtotal += grauYnovo;
            grauZtotal += grauZnovo;

            Log.i("Posição", "X: " + x + " Y: " + y + " Z: " + z);
            Log.i("GrauNovo", "X: " + grauXnovo + " Y: " + grauYnovo + " Z: " + grauZnovo);
            Log.i("Grau", "X: " + grauXtotal + " Y: " + grauYtotal + " Z: " + grauZtotal);

            //atualiza a posição da imagem caso o dispositivo seja deslocado consideravelmente
            if (grauYtotal > grauYant + 0.01 || grauYtotal < grauYant - 0.01) {
                xNovo = img.getX() + (grauYnovo * escalaX);
                img.setX(xNovo);
            } else {
                grauYtotal = grauYant; //elimina pequenos ruidos do sensor com dispositivo parado
            }

            //atualiza a posição da imagem caso o dispositivo seja deslocado consideravelmente
            if (grauXtotal > grauXant + 0.01 || grauXtotal < grauXant - 0.01) {
                yNovo = img.getY() + (grauXnovo * escalaY);
                img.setY(yNovo);
            } else {
                grauXtotal = grauXant; //elimina pequenos ruidos do sensor com dispositivo parado
            }

            //atualiza a posição da imagem caso o dispositivo seja deslocado consideravelmente
            if (grauZtotal > grauZant + 0.01 || grauZtotal < grauZant - 0.01) {
                //rodarImagem(img,grauZtotal);
                //TODO - compensar a alteração de largura e altura da imagem após rotação evitando o corte da imagem

                //animate(0,grauZtotal,0,img);

                Log.d("Pivot", "X: " + img.getPivotX() + " Y: " + img.getPivotY());

                img.setRotation(grauZtotal);
            } else {
                grauZtotal = grauZant; //elimina pequenos ruidos do sensor com dispositivo parado
            }

            //guarda medição para comparar com a proxima
            grauXant = grauXtotal;
            grauYant = grauYtotal;
            grauZant = grauZtotal;

            Log.i("IMAGEM", "X: " + img.getX() + " Y: " + img.getY());

            //RETORNANDO IMAGEM PARA A TELA PARA COMPLETAR O GIRO DE 360°-------------------------------

            //girando horizontalmente para a direita
            if (grauYtotal < 0) {
                //obtem diferença em graus em relação ao centro
                if (Math.abs(Math.abs(grauYtotal) - 360) <= distanciaDireitaX / escalaX) {
                    img.setX(dimenX - 10); //compensa os limites para ser possível comparação ser verdadeira, uma vez que primeiro valor dela será sempre positivo
                    centerX = img.getX();
                    distanciaEsquerdaX = centerX;
                    distanciaDireitaX = dimenX - centerX;
                    grauYtotal = 0;
                }
            }

            //girando horizontalmente para a esquerda
            //arrumar reentrada compensando a largura da imagem - FEITO
            if (grauYtotal > 0) {
                //obtem diferença em graus em relação ao centro
                if (Math.abs(Math.abs(grauYtotal) - 360) <= (distanciaEsquerdaX + larguraImgPokemon) / escalaX) {
                    //if(Math.abs(Math.abs(grauYtotal) - 360) <= distanciaEsquerdaX/escalaX){

                    Log.d("Passei", "DE: " + distanciaEsquerdaX + " DimX: " + dimenX + " PI: " + percentImagePokemon + " EX: " + escalaX);
                    Log.d("Passei", "estive aqui horizontal " + grauYtotal + " " + (distanciaEsquerdaX + larguraImgPokemon) / escalaX);

                    //img.setX(5); //compensa os limites
                    //img.setX(-img.getMeasuredWidth());

                    img.setX(-larguraImgPokemon);
                    centerX = img.getX();
                    //distanciaEsquerdaX = centerX;
                    //distanciaEsquerdaX = escalaX; //para forçar quociente 1 após primeiro loop
                    distanciaEsquerdaX = escalaX - larguraImgPokemon; //para forçar quociente 1 após primeiro loop
                    distanciaDireitaX = dimenX - centerX;
                    grauYtotal = 0;
                }
            }

            //girando verticalmente para a cima
            //arrumar reentrada compensando a altura da imagem - FEITO
            if (grauXtotal > 0) {
                //obtem diferença em graus em relação ao centro
                if (Math.abs(Math.abs(grauXtotal) - 360) <= (distanciaTopoY + alturaImgPokemon) / escalaY) {
                    //if(Math.abs(Math.abs(grauXtotal) - 360) <= distanciaTopoY/escalaY){

                    Log.d("Passei", "estive aqui vertical " + grauXtotal + " " + (distanciaTopoY + alturaImgPokemon) / escalaY);

                    //img.setY(5); //compensa os limites
                    //img.setY(-img.getMeasuredHeight());

                    img.setY(-alturaImgPokemon);
                    centerY = img.getY();
                    //distanciaTopoY = centerY;
                    //distanciaTopoY = escalaY; //para forçar quociente 1 após primeiro loop
                    distanciaTopoY = escalaY - alturaImgPokemon; //para forçar quociente 1 após primeiro loop
                    distanciaBaseY = dimenY - centerY;
                    grauXtotal = 0;
                }
            }

            //girando verticalmente para a baixo
            if (grauXtotal < 0) {
                //obtem diferença em graus em relação ao centro
                if (Math.abs(Math.abs(grauXtotal) - 360) <= distanciaBaseY / escalaY) {
                    img.setY(dimenY - 10); //compensa os limites
                    centerY = img.getY();
                    distanciaTopoY = centerY;
                    distanciaBaseY = dimenY - centerY;
                    grauXtotal = 0;
                }
            }

            //obtem limites do pokemon
            // TODO: obter limites tambem fora do onSensorChanged para permitir celulares sem o giroscópio a jogarem
            limitesPokemon = getLeftRightTopBottomImage(img.getX(),img.getY(),alturaImgPokemon,larguraImgPokemon);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        //mudou a precisão
    }

    public void preparaCamera(){
        // Hide the window title.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_captura);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Create an instance of Camera
       // mCamera = getCameraInstance();

        // Create our Preview view and set it as the content of our activity.
        mPreview = new CameraPreview(this);
        FrameLayout frameLayout = (FrameLayout) findViewById(R.id.camera_preview);
        frameLayout.addView(mPreview);

        AbsoluteLayout absolutLayoutControls = (AbsoluteLayout) findViewById(R.id.imagemAR);
        absolutLayoutControls.bringToFront();

    }

    /** A safe way to get an instance of the Camera object. */
    public static Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open();
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    public float[] getLeftRightTopBottomImage(float x, float y, float altura, float largura){
        float limites[] = new float[4];
        limites[0] = x;
        limites[1] = x + largura;
        limites[2] = y;
        limites[3] = y + altura;

        Log.d("Limites", "E: " + limites[0] + " D: " + limites[1] + " C: " + limites[2] + " B: " + limites[3]);
        return limites;
    }

    public void configuraPokemon(){
        //garante só rodar após as views estarem na tela
        img.post(new Runnable() {
            @Override
            public void run() {
                //define largura do pokemon em relação ao tamanho da tela
                larguraImgPokemon = dimenX * percentImagePokemon;
                //tamanho real da foto do animal (bitmap já carregado); não depende de o layout
                //já ter medido a view depois do setImageBitmap
                float larguraOriginal = img.getDrawable() != null ? img.getDrawable().getIntrinsicWidth() : img.getMeasuredWidth();
                float alturaOriginal = img.getDrawable() != null ? img.getDrawable().getIntrinsicHeight() : img.getMeasuredHeight();
                //obtem propoção da imagem redimensionada
                float proporcaoPokemon = (larguraImgPokemon * 100) / larguraOriginal;
                //define altura do animal de forma proporcional
                alturaImgPokemon = alturaOriginal * proporcaoPokemon / 100;

                //obtem o centro da tela
                //COLOCAR ELE RANDOMICO Mais pra frente
                centerX = dimenX / 2 - (((int) larguraImgPokemon) / 2);
                centerY = dimenY / 2 - (((int) alturaImgPokemon) / 2);

                //X: 1200 Y: 1834 CX: 300.0 CY: 459.0 IMG_X: 600 IMG_Y: 917

                //modifica o tamanho da imagem e centraliza o mesmo na tela
                AbsoluteLayout.LayoutParams params = new AbsoluteLayout.LayoutParams((int) larguraImgPokemon, (int) alturaImgPokemon, (int) centerX, (int) centerY);
                img.setLayoutParams(params);

                //calcula distâncias iniciais
                distanciaTopoY = centerY;
                distanciaBaseY = dimenY - centerY;
                distanciaEsquerdaX = centerX;
                distanciaDireitaX = dimenX - centerX;

                //calcula a escala
                escalaX = dimenX / 72; //cada grau vale escala pixel - 72º é o campo de visão considerado
                escalaY = dimenY / 72;

                Log.i("Dimensão", "X: " + dimenX + " Y: " + dimenY + " CX: " + centerX + " CY: " + centerY +
                        " IMG_X: " + (int) larguraImgPokemon + " IMG_Y: " + (int) alturaImgPokemon);

                imagemPokemonPreparada = true;
            }
        });
    }

//    public void configuraEfeitoCaptura(){
//        mp = MediaPlayer.create(getBaseContext(), R.raw.quicando);
//        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){
//            int maxCount = 3;
//            @Override
//            public void onCompletion(MediaPlayer mediaPlayer) {
//                if(countSound < maxCount) {
//                    countSound++;
//                    mediaPlayer.seekTo(0); //TODO: pesquisar o que faz metodo seekTo MediaPlayer
//                    mediaPlayer.start();
//
//                    //anima a pokebola junto com o som de quique
//                    if(countSound % 2 == 0) {
//                        //pokebola.setRotation(0);
//                        pokebola.animate().rotation(0).start();
//
//                    } else {
//                        //pokebola.setRotation(-20);
//                        pokebola.animate().rotation(-20).start();
//                    }
//
//
//                }else{
//                    countSound = 0;
//
//                    MediaPlayer mp2 = MediaPlayer.create(getBaseContext(), R.raw.sucesso);
//                    mp2.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
//                        @Override
//                        public void onCompletion(MediaPlayer mp) {
//                            //TODO: enviar captura para o servidor antes de fechar tela. Talvez fazer isso diretamento no método capturar
//                            ControladoraFachadaSingleton.getInstance().getUsuario().capturar(ap);
//                            ControladoraFachadaSingleton.getInstance().aumentaXp("captura");   //atualiza XP do usuário após uma captura
//                            finish(); //fecha o módulo de captura quando a música de sucesso acaba
//                        }
//                    });
//
//                    //pausa a musica da batalha e começa a do sucesso
//                    mpBattle.pause();
//                    mp2.start();
//
//                    Toast.makeText(getBaseContext(),pkmn.getNome() + " foi capturado! \\o/",Toast.LENGTH_LONG).show();
//                }
//            }});
//        //diminui o volume do tema da batalha antes da pokebola quicar
//        atribuirVolumeMediaPlayer(mpBattle, 85);
//
//        mp.start();
//
//        //substituiu imagem do pokemon por explosão
//        img.setImageResource(R.drawable.explosion);
//
//        img.post(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    Thread.sleep(350);
//                    img.setVisibility(View.INVISIBLE);
//                } catch (Exception e) {
//
//                }
//            }
//        });
//    }

    public void configuraEfeitoCaptura() {
        mp = MediaPlayer.create(getBaseContext(), R.raw.quicando);
        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            int maxCount = 3;

            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                if (countSound < maxCount) {
                    countSound++;
                    mediaPlayer.seekTo(0); // Reinicia o áudio do quique do início
                    mediaPlayer.start();

                    // Mantém a animação de rotação original
                    if (countSound % 2 == 0) {
                        pokebola.animate().rotation(0).start();
                    } else {
                        pokebola.animate().rotation(-20).start();
                    }
                } else {
                    countSound = 0;

                    // Registra a captura no servidor IMEDIATAMENTE após os 3 quiques.
                    // O XP local é só o feedback imediato: a resposta do POST traz o valor oficial.
                    ControladoraFachadaSingleton.getInstance().registrarCapturaAnimal(ap);
                    ControladoraFachadaSingleton.getInstance().aumentaXp("captura");

                    MediaPlayer mp2 = MediaPlayer.create(getBaseContext(), R.raw.sucesso);
                    mpBattle.pause(); // Pausa a música de fundo
                    mp2.start();

                    Toast.makeText(getBaseContext(), animal.getNomePopular() + " foi capturado! \\o/", Toast.LENGTH_LONG).show();

                    // Handler garante que o finish() ocorra independente do sucesso do áudio
                    new android.os.Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            finish();
                        }
                    }, 4500); // Aguarda 2.5 segundos (tempo médio do som de sucesso)
                }
            }
        });

        atribuirVolumeMediaPlayer(mpBattle, 85);
        mp.start();

        // Troca para a imagem de explosão
        img.setImageResource(R.drawable.explosion);

        // Esconde a imagem após 350ms sem travar a UI Thread
        img.postDelayed(new Runnable() {
            @Override
            public void run() {
                img.setVisibility(View.INVISIBLE);
            }
        }, 350);
    }

    public void atribuirVolumeMediaPlayer(MediaPlayer mediaPlayer, int volume){
        int maxVolume = 100;
        float log1=(float)(Math.log(maxVolume-volume)/Math.log(maxVolume));
        mediaPlayer.setVolume(1-log1,1-log1);
    }

    public void configuraPokebola(){
        //garante só rodar após as views estarem na tela
        pokebola.post(new Runnable() {
            @Override
            public void run() {
                //define largura da pokebola em relação ao tamanho da tela
                larguraImgPokeball = dimenX * percentImagePokeball;
                //obtem propoção da imagem redimensionada
                float proporcaoPokebola = (larguraImgPokeball * 100) / pokebola.getMeasuredWidth();
                //define altura da pokebola de forma proporcional
                alturaImgPokeball = pokebola.getMeasuredHeight() * proporcaoPokebola / 100;

                //obtem o centro da tela
                centerXpokeball = dimenX / 2 - (((int) larguraImgPokeball) / 2);
                centerYpokeball = dimenY - (int) alturaImgPokeball - 75; //menos 40 para compensar a barra de tarefas do android

                //X: 1200 Y: 1834 CX: 300.0 CY: 459.0 IMG_X: 600 IMG_Y: 917

                //modifica o tamanho da imagem e centraliza o mesmo na tela
                AbsoluteLayout.LayoutParams params = new AbsoluteLayout.LayoutParams((int) larguraImgPokeball, (int) alturaImgPokeball, (int) centerXpokeball, (int) centerYpokeball);
                pokebola.setLayoutParams(params);

                Log.i("Dimensão", "X: " + dimenX + " Y: " + dimenY + " CX_pokeball: " + centerXpokeball + " CY_pokeball: " + centerYpokeball +
                        " IMG_X_pokeball: " + (int) larguraImgPokeball + " IMG_Y_pokeball: " + (int) alturaImgPokeball);

                imagemPokeballPreparada = true;
            }
        });

        configuraTouchPokebola();
    }

    public void configuraTouchPokebola(){
        //configura o listener de toque na pokebola
        pokebola.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                //TODO: PESQUISAR O COMPORTAMENTO EXATO DOS MÉTODOS getRawX() e getRawY()
                float x = event.getRawX();
                float y = event.getRawY();
                Log.d("Mover Pokebola", "X: " + x + " Y: " + y);

                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_POINTER_DOWN:
                        //tratamento se pressionou a imagem
                        mp = MediaPlayer.create(getBaseContext(), R.raw.arremesso);
                        mp.start();

                        tempoInicial = System.currentTimeMillis();
                        xInicioTouch = pokebola.getX();
                        yInicioTouch = pokebola.getY();

                        Log.d("Mover Pokebola", "Tocou na pokebola");
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_POINTER_UP:
                        //tratamento se retirou o dado da imagem

                        tempoFinal = System.currentTimeMillis();
                        xFimTouch = pokebola.getX();
                        yFimTouch = pokebola.getY();

                        diferencaX = Math.abs(xInicioTouch - xFimTouch);
                        diferencaY = Math.abs(yInicioTouch - yFimTouch);
                        duracaoTouch = tempoFinal - tempoInicial;

                        velocidadeX = diferencaX / duracaoTouch; //pixel por milisegundo
                        velocidadeY = diferencaY / duracaoTouch; //pixel por milisegundo

                        //inicia a Thread
                        //deslocamentoPokebola = new DeslocamentoPokebola();
                        //deslocamentoPokebola.execute(); //MÉTODO doInBackground da class DeslocamentoPokebola é executado

                        velocidadeXoriginal = velocidadeX;
                        velocidadeYoriginal = velocidadeY;

                        while (velocidadeX > 0 && velocidadeY > 0 && !capturou) {
                            int tempo = 25; //compensa a velocidade para a pokebola acelerar mais
                            //pokebola arremessada para a direita
                            if (xFimTouch >= xInicioTouch) {
                                pokebola.setX(pokebola.getX() + (tempo * velocidadeX));
                            } else {
                                pokebola.setX(pokebola.getX() - (tempo * velocidadeX));
                            }

                            //pokebola arremessada para cima
                            if (yFimTouch <= yInicioTouch) {
                                pokebola.setY(pokebola.getY() - (tempo * velocidadeY));
                            } else {
                                pokebola.setY(pokebola.getY() + (tempo * velocidadeY));
                            }

                            //---------------VERIFICA CAPTURA-------------
                            //obtem limites da pokebola
                            limitesPokeball = getLeftRightTopBottomImage(pokebola.getX(), pokebola.getY(), alturaImgPokeball, larguraImgPokeball);
                            Log.d("LimPokeball", "E: " + limitesPokeball[0] + " D: " + limitesPokeball[1] + " C: " + limitesPokeball[2] + " B: " + limitesPokeball[3]);

                            //verifica se houve captura pela interseção de imagens
                            if (isCapturou(limitesPokeball, limitesPokemon, capturou)) {
                                capturou = true;
                                configuraEfeitoCaptura();
                                Log.w("Mover Pokebola", "Capturou por arremesso " + getTime());
                                break;
                            }
                            //--------------------------------------------

                            Log.d("Deslocamento", "Atualizei..VX: " + velocidadeX + " VY: " + velocidadeY);

                            //reduzindo a velocidade da pokebola
                            velocidadeX = velocidadeX - (velocidadeXoriginal * (float) 0.045);
                            velocidadeY = velocidadeY - (velocidadeYoriginal * (float) 0.045);
                        }

                        if(pokebola.getX() > dimenX || pokebola.getX() < 0 ||
                                pokebola.getY() > dimenY || pokebola.getY() < 0){

                            Toast.makeText(getBaseContext(),"Tente novamente...",Toast.LENGTH_SHORT).show();

                            pokebola.setX(centerXpokeball);
                            pokebola.setY(centerYpokeball);
                        }

                        Log.d("Mover Pokebola", "Retirou o dedo da pokebola");
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        //tratamento para arrastar imagem
                        Log.d("Mover Pokebola", "Moveu a pokebola");

                        //pokebola.setX(x - (larguraImgPokeball / 2));
                        //pokebola.setY(y - (alturaImgPokeball / 2));

                        pokebola.setX(x - (larguraImgPokeball / 2));
                        pokebola.setY((y - (alturaImgPokeball / 3)) - (alturaImgPokeball / 2)); //-(alturaImgPokeball / 3) para suavizar o movimento

                        //---------------VERIFICA CAPTURA-------------
                        //obtem limites da pokebola
                        limitesPokeball = getLeftRightTopBottomImage(pokebola.getX(), pokebola.getY(), alturaImgPokeball, larguraImgPokeball);
                        Log.d("LimPokeball", "E: " + limitesPokeball[0] + " D: " + limitesPokeball[1] + " C: " + limitesPokeball[2] + " B: " + limitesPokeball[3]);

                        //verifica se houve captura pela interseção de imagens
                        if (isCapturou(limitesPokeball, limitesPokemon, capturou)) {
                            Log.w("Mover Pokebola", "Capturou por tocar " + getTime());
                            capturou = true;
                            configuraEfeitoCaptura();
                        }
                        //---------------------------------------------

                        return true;

                    default:
                        Log.d("Mover Pokebola", "Evento não classificado na pokebola");
                }

                return false;
            }
        });
    }

    // ===== CAPTURA POR ENQUADRAMENTO (etapa 1) =====
    // Etapa 1: permanência no círculo central + encerramento do fluxo.
    // Etapa 2 (chaves próprias no topo): spawn em 360° com limite vertical, seta de direção,
    // fuga por demora, tempo-limite do encontro e tamanho fixo.
    // Ainda NÃO existe: menu de frutas.

    //Com a Pokébola desligada ela nunca fica "preparada"; os sensores só dependem dela no fluxo antigo
    private boolean isCenaPronta() {
        return imagemPokemonPreparada && (MODO_CAPTURA_ENQUADRAMENTO || imagemPokeballPreparada);
    }

    //Esconde a Pokébola e coloca a mira por cima de tudo (uma vez só, mesmo com vários onResume)
    private void configuraEnquadramento() {
        pokebola.setVisibility(View.GONE);
        //FIT_CENTER amplia um bitmap menor que a caixa (centerInside não amplia): defesa extra contra foto pequena
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        //o giroscópio antigo ligava isto a cada evento; o motor angular não passa por lá
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (mira != null) return;

        raioMira = dimenX * FRACAO_RAIO_MIRA;
        mira = new MiraEnquadramentoView(this);
        mira.setRaio(raioMira);
        mira.setCentro(dimenX / 2f, dimenY / 2f); //mesmo centro usado para posicionar o animal em configuraPokemon()

        AbsoluteLayout camada = (AbsoluteLayout) findViewById(R.id.imagemAR);
        camada.addView(mira, new AbsoluteLayout.LayoutParams(
                AbsoluteLayout.LayoutParams.MATCH_PARENT, AbsoluteLayout.LayoutParams.MATCH_PARENT, 0, 0));
    }

    //O animal nasce deslocado (mas visível) para o jogador ter que enquadrar.
    //Usa translation, igual ao giroscópio, então os layout params refeitos por configuraPokemon() não desfazem o deslocamento.
    //Usado só com SPAWN_AO_REDOR = false; o spawn fora do eixo da câmera (etapa 2) é sorteiaPosicaoAoRedor().
    private void posicionaAnimalForaDaMira() {
        img.post(new Runnable() {
            @Override
            public void run() {
                if (deslocamentoInicialAplicado) return;
                deslocamentoInicialAplicado = true;

                //sem giroscópio não há como mover o animal até o círculo: deixa no centro
                if (sensor == null) return;

                double angulo = RandomUtil.randomDoubleInRange(0, 2 * Math.PI);
                float distancia = raioMira * FRACAO_DESLOCAMENTO_INICIAL;
                img.setTranslationX(img.getTranslationX() + (float) (Math.cos(angulo) * distancia));
                img.setTranslationY(img.getTranslationY() + (float) (Math.sin(angulo) * distancia));
            }
        });
    }

    private void iniciaVerificacaoEnquadramento() {
        handlerEnquadramento.removeCallbacks(verificaEnquadramento);
        ultimaVerificacao = SystemClock.uptimeMillis();
        handlerEnquadramento.postDelayed(verificaEnquadramento, INTERVALO_VERIFICACAO_MS);
    }

    //Roda por timer (não pelo onSensorChanged) para contar tempo mesmo com o celular parado
    private final Runnable verificaEnquadramento = new Runnable() {
        @Override
        public void run() {
            if (capturou || encontroEncerrado) return;

            long agora = SystemClock.uptimeMillis();
            long delta = agora - ultimaVerificacao;
            ultimaVerificacao = agora;

            if (isCenaPronta() && deslocamentoInicialAplicado) {
                boolean dentro;
                if (SPAWN_AO_REDOR) {
                    //reaplica a cada tick: mantém a imagem certa mesmo sem eventos do giroscópio
                    aplicaPosicaoAngular();
                    //decide pela posição angular (independe do layout já ter sido refeito)
                    dentro = Math.hypot(deslocamentoTelaX(), deslocamentoTelaY()) <= raioMira;
                } else {
                    //etapa 1. Centro do animal: a rotação do giroscópio gira em torno dele, então não interfere
                    float centroAnimalX = img.getX() + larguraImgPokemon / 2;
                    float centroAnimalY = img.getY() + alturaImgPokemon / 2;
                    dentro = Math.hypot(centroAnimalX - dimenX / 2f, centroAnimalY - dimenY / 2f) <= raioMira;
                }

                //fora do círculo o progresso esvazia no dobro da velocidade (tremor rápido não zera tudo)
                if (dentro)
                    tempoEnquadradoMs += delta;
                else
                    tempoEnquadradoMs = Math.max(0, tempoEnquadradoMs - 2 * delta);

                mira.setAnimalDentro(dentro);
                mira.setProgresso(tempoEnquadradoMs / (float) TEMPO_ENQUADRAMENTO_MS);

                if (tempoEnquadradoMs >= TEMPO_ENQUADRAMENTO_MS) {
                    capturou = true;
                    Log.w("Enquadramento", "Catalogou por enquadramento " + getTime());
                    concluirCatalogacao();
                    return;
                }

                if (SETA_DIRECAO && SPAWN_AO_REDOR) atualizaSeta();

                //FUGA: só conta o tempo FORA do círculo; quem está enquadrando nunca é punido.
                //FUTURO (não implementar agora): a fruta certa do menu lateral pausaria esta regra.
                if (FUGA_POR_DEMORA && SPAWN_AO_REDOR && sensor != null && !dentro) {
                    tempoForaDoCirculoMs += delta;
                    if (tempoForaDoCirculoMs >= TEMPO_PARA_FUGIR_MS && !isFugindo()) fogeParaOutroPonto();
                }

                if (LIMITE_TEMPO_ENCONTRO) {
                    tempoEncontroMs += delta;
                    long restante = Math.max(0, TEMPO_MAXIMO_ENCONTRO_MS - tempoEncontroMs);
                    mira.setTempoRestante((int) Math.ceil(restante / 1000.0), restante <= TEMPO_AVISO_FINAL_MS);
                    //prorrogação amigável: se o anel está enchendo, deixa terminar
                    boolean prorrogacao = dentro && tempoEnquadradoMs > 0;
                    if (restante == 0 && !prorrogacao) {
                        encerrarPorFuga();
                        return;
                    }
                }
            }

            handlerEnquadramento.postDelayed(this, INTERVALO_VERIFICACAO_MS);
        }
    };

    // ----- etapa 2: posição angular (o animal "existe" ao redor do jogador, em 360°) -----

    private static float normalizaGraus(float graus) {
        graus %= 360f;
        if (graus >= 180f) graus -= 360f;
        if (graus < -180f) graus += 360f;
        return graus;
    }

    //Distância, em pixels, do centro do animal até o centro da tela
    private float deslocamentoTelaX() {
        return normalizaGraus(azimuteAnimal + yawAcumulado) * escalaX;
    }

    private float deslocamentoTelaY() {
        return (pitchAcumulado - elevacaoAnimal) * escalaY;
    }

    //Integra o giroscópio com o dt real do evento (o código antigo supõe 0,02 s fixos)
    private void atualizaPosicaoAngular(SensorEvent event) {
        double dt = getSensorElapsedSeconds(event);
        if (dt <= 0 || dt > DT_MAXIMO_GIRO_S) return;

        //mesmos eixos e sinais do giroscópio antigo: X = vertical, Y = horizontal, Z = giro da imagem
        if (Math.abs(event.values[0]) > ZONA_MORTA_GIRO_RAD_S) pitchAcumulado += Math.toDegrees(event.values[0] * dt);
        if (Math.abs(event.values[1]) > ZONA_MORTA_GIRO_RAD_S) yawAcumulado += Math.toDegrees(event.values[1] * dt);
        if (Math.abs(event.values[2]) > ZONA_MORTA_GIRO_RAD_S) rollAcumulado += Math.toDegrees(event.values[2] * dt);
        yawAcumulado = normalizaGraus(yawAcumulado);

        aplicaPosicaoAngular();
    }

    private void aplicaPosicaoAngular() {
        if (!isCenaPronta() || !deslocamentoInicialAplicado || encontroEncerrado) return;
        img.setX(dimenX / 2f + deslocamentoTelaX() - larguraImgPokemon / 2);
        img.setY(dimenY / 2f + deslocamentoTelaY() - alturaImgPokemon / 2);
        img.setRotation(rollAcumulado); //mantém o animal "nivelado" com o mundo, como antes
    }

    //Sorteia onde o animal está ao redor do jogador: qualquer direção, altura crível.
    //FUTURO (não implementar agora): a faixa de elevação pode depender de animal.getHabito() (ex.: arborícola mais alto).
    private void sorteiaPosicaoAoRedor() {
        img.post(new Runnable() {
            @Override
            public void run() {
                if (!deslocamentoInicialAplicado) {
                    //sem giroscópio não há como procurar: fica no centro (azimute 0, elevação 0)
                    if (sensor != null) {
                        float lado = Math.random() < 0.5 ? -1f : 1f;
                        float relativo = lado * (float) RandomUtil.randomDoubleInRange(AZIMUTE_MIN_SPAWN, 180);
                        //relativo à direção ATUAL do celular (o jogador pode ter girado enquanto a foto baixava)
                        azimuteAnimal = normalizaGraus(relativo - yawAcumulado);
                        elevacaoAnimal = (float) RandomUtil.randomDoubleInRange(ELEVACAO_MIN, ELEVACAO_MAX);
                    }
                    deslocamentoInicialAplicado = true;
                    Log.d("Enquadramento", "Spawn: azimute " + azimuteAnimal + "° elevação " + elevacaoAnimal + "°");
                }
                aplicaPosicaoAngular();
            }
        });
    }

    //Seta na borda enquanto o centro do animal está fora da tela
    private void atualizaSeta() {
        float dx = deslocamentoTelaX();
        float dy = deslocamentoTelaY();
        boolean foraDaTela = Math.abs(dx) > dimenX / 2f || Math.abs(dy) > dimenY / 2f;
        mira.setIndicador(foraDaTela, (float) Math.toDegrees(Math.atan2(dy, dx)));
    }

    private boolean isFugindo() {
        return animacaoFuga != null && animacaoFuga.isRunning();
    }

    //O animal se assusta e corre para outro ponto: sempre para LONGE de onde a câmera aponta
    private void fogeParaOutroPonto() {
        tempoForaDoCirculoMs = 0;

        final float azimuteInicial = azimuteAnimal;
        final float elevacaoInicial = elevacaoAnimal;
        float relativoAtual = normalizaGraus(azimuteAnimal + yawAcumulado);
        float lado = relativoAtual != 0 ? Math.signum(relativoAtual) : (Math.random() < 0.5 ? -1f : 1f);
        final float salto = lado * (float) RandomUtil.randomDoubleInRange(SALTO_FUGA_MIN, SALTO_FUGA_MAX);
        final float elevacaoFinal = (float) RandomUtil.randomDoubleInRange(ELEVACAO_MIN, ELEVACAO_MAX);

        //anima a posição angular: se estiver à vista o jogador VÊ o animal correr; se não, a seta muda
        animacaoFuga = ValueAnimator.ofFloat(0f, 1f);
        animacaoFuga.setDuration(DURACAO_ANIMACAO_FUGA_MS);
        animacaoFuga.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animacao) {
                float f = (float) animacao.getAnimatedValue();
                azimuteAnimal = normalizaGraus(azimuteInicial + salto * f);
                elevacaoAnimal = elevacaoInicial + (elevacaoFinal - elevacaoInicial) * f;
                aplicaPosicaoAngular();
            }
        });
        animacaoFuga.start();

        mira.setMensagemTemporaria("Ele se assustou e correu!");
        Log.d("Enquadramento", "Fugiu por demora: salto de " + salto + "°");
    }

    //Tempo-limite estourou: o animal foge de vez, sem registro nem XP, e a tela volta ao mapa
    private void encerrarPorFuga() {
        encontroEncerrado = true;
        if (animacaoFuga != null) animacaoFuga.cancel();
        Log.w("Enquadramento", "Encontro encerrado por tempo " + getTime());

        mira.setFugiu();
        mpBattle.pause();
        float lado = Math.random() < 0.5 ? -1f : 1f;
        img.animate().alpha(0f).translationXBy(lado * dimenX * 0.3f).setDuration(600).start();

        Toast.makeText(getBaseContext(), animal.getNomePopular() + " fugiu! Continue explorando para encontrá-lo de novo.",
                Toast.LENGTH_LONG).show();

        handlerEnquadramento.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, 2500);
    }

    //Mesmo encerramento do fluxo antigo (registro no servidor, XP, som de sucesso, finish),
    //sem quiques/explosão. Duplicado de propósito para não tocar em configuraEfeitoCaptura().
    private void concluirCatalogacao() {
        // O XP local é só o feedback imediato: a resposta do POST traz o valor oficial.
        ControladoraFachadaSingleton.getInstance().registrarCapturaAnimal(ap);
        ControladoraFachadaSingleton.getInstance().aumentaXp("captura");

        mira.setConcluido();
        img.animate().alpha(0f).setDuration(600).start();

        MediaPlayer mp2 = MediaPlayer.create(getBaseContext(), R.raw.sucesso);
        mpBattle.pause();
        mp2.start();

        Toast.makeText(getBaseContext(), animal.getNomePopular() + " foi catalogado! \\o/", Toast.LENGTH_LONG).show();

        //mesmo tempo do fluxo antigo para o som de sucesso tocar antes de voltar ao mapa
        handlerEnquadramento.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, 4500);
    }
    // ===== FIM CAPTURA POR ENQUADRAMENTO =====

    public String getTime(){
        Calendar c = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("dd:MMMM:yyyy HH:mm:ss a");
        String strDate = sdf.format(c.getTime());
        return strDate;
    }

    //verifica interseção
    public boolean isCapturou(float pkball[],float pkmn[], boolean cap){
        if(pkball[0] <= pkmn[1] &&
                pkmn[0] <= pkball[1] &&
                pkball[2] <= pkmn[3] &&
                pkmn[2] <= pkball[3] && !cap){
            return true;
        }else{
            return false;
        }
    }

   /*private void releaseCamera(){
        if (mCamera != null){
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }*/

    class DeslocamentoPokebola extends AsyncTask<String, String, String> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            /*pDialog = new ProgressDialog(Colaboracao.this);
            pDialog.setMessage("ENVIANDO...");
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(false);
            pDialog.show();*/
        }

        protected String doInBackground(String... args) {
            /*
                        tempoFinal = System.currentTimeMillis();
                        xFimTouch = pokebola.getX();
                        yFimTouch = pokebola.getY();

                        diferencaX = Math.abs(xInicioTouch - xFimTouch);
                        diferencaY = Math.abs(yInicioTouch - yFimTouch);
                        duracaoTouch = tempoFinal - tempoInicial;

                        velocidadeX = diferencaX/duracaoTouch; //pixel por milisegundo
                        velocidadeY = diferencaY/duracaoTouch; //pixel por milisegundo
             */

            velocidadeY = velocidadeY*3;
            velocidadeX = velocidadeX*3;

            velocidadeXoriginal = velocidadeX;
            velocidadeYoriginal = velocidadeY;

            while(velocidadeX > 0 && velocidadeY > 0){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        int tempo = 100;
                        //pokebola arremessada para a direita
                        if(xFimTouch >= xInicioTouch) {
                            pokebola.setX(pokebola.getX() + (tempo * velocidadeX));
                        }else{
                            pokebola.setX(pokebola.getX() - (tempo * velocidadeX));
                        }

                        //pokebola arremessada para cima
                        if(yFimTouch <= yInicioTouch) {
                            pokebola.setY(pokebola.getY() - (tempo * velocidadeY));
                        }else{
                            pokebola.setY(pokebola.getY() + (tempo * velocidadeY));
                        }

                        try {
                            Thread.sleep(tempo);
                        }catch (Exception e){
                            Log.e("ERRO", "sleep asyncTask");
                        }

                        Log.d("Deslocamento", "Atualizei..VX: " + velocidadeX + " VY: " + velocidadeY);

                        velocidadeX = velocidadeX - (velocidadeXoriginal*(float)0.03);
                        velocidadeY = velocidadeY - (velocidadeYoriginal*(float)0.03);

                        if(velocidadeX < 0 || velocidadeY < 0)
                            return;

                    }
                });

            }



            return null;
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            /*pDialog.dismiss();
            Log.i("CANCELOU", "CANCELOU ASYNC TASK");*/
        }

        protected void onPostExecute(String file_url) {
            //pDialog.dismiss();
            Log.d("Deslocamento", "acabei..");
        }
    }

}


