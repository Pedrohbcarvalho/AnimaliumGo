package teste.lucasvegi.pokemongooffline.View;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/**
 * Mira da captura por enquadramento: círculo pontilhado no centro da tela, anel de progresso,
 * dica curta e (etapa 2) seta de direção na borda, cronômetro do encontro e mensagens rápidas.
 * Só desenha; quem decide posição, tempo e regras é a CapturaActivity.
 */
public class MiraEnquadramentoView extends View {
    private static final int COR_NEUTRA = Color.argb(200, 255, 255, 255);
    private static final int COR_DENTRO = Color.parseColor("#7FFC97"); //mesma cor do xp_texto
    private static final int COR_ALERTA = Color.parseColor("#FF5A5A");
    private static final long DURACAO_MENSAGEM_MS = 2000;

    private final Paint pincelCirculo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pincelProgresso = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pincelTexto = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pincelSeta = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pincelContornoSeta = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF areaArco = new RectF();
    private final Path seta = new Path();
    private final float densidade;
    private final DashPathEffect tracejado;

    private float centroX = -1;
    private float centroY = -1;
    private float raio = 0;
    private float progresso = 0;    //0..1
    private boolean animalDentro = false;
    private boolean concluido = false;
    private boolean fugiu = false;

    //etapa 2
    private boolean indicadorVisivel = false;
    private float anguloIndicador = 0;    //graus na tela: 0 = direita, 90 = baixo
    private int segundosRestantes = -1;   //-1 esconde o cronômetro
    private boolean alertaTempo = false;
    private String mensagemTemporaria = null;
    private long fimMensagem = 0;

    public MiraEnquadramentoView(Context context) {
        super(context);
        densidade = getResources().getDisplayMetrics().density;

        pincelCirculo.setStyle(Paint.Style.STROKE);
        pincelCirculo.setStrokeWidth(3 * densidade);
        tracejado = new DashPathEffect(new float[]{12 * densidade, 8 * densidade}, 0);
        pincelCirculo.setPathEffect(tracejado);

        pincelProgresso.setStyle(Paint.Style.STROKE);
        pincelProgresso.setStrokeWidth(6 * densidade);
        pincelProgresso.setStrokeCap(Paint.Cap.ROUND);
        pincelProgresso.setColor(COR_DENTRO);

        pincelTexto.setColor(Color.WHITE);
        pincelTexto.setTextAlign(Paint.Align.CENTER);
        pincelTexto.setTextSize(16 * densidade);
        pincelTexto.setFakeBoldText(true);
        pincelTexto.setShadowLayer(4 * densidade, 0, 0, Color.BLACK); //legível sobre qualquer fundo da câmera

        pincelSeta.setStyle(Paint.Style.FILL);
        pincelSeta.setColor(COR_NEUTRA);
        //contorno escuro em vez de sombra: setShadowLayer em formas exigiria camada em software
        pincelContornoSeta.setStyle(Paint.Style.STROKE);
        pincelContornoSeta.setStrokeWidth(3 * densidade);
        pincelContornoSeta.setStrokeJoin(Paint.Join.ROUND);
        pincelContornoSeta.setColor(Color.argb(140, 0, 0, 0));
        //chevron apontando para a direita (0°), centrado na origem; é girado no onDraw
        float t = 14 * densidade;
        seta.moveTo(t, 0);
        seta.lineTo(-t * 0.6f, -t);
        seta.lineTo(-t * 0.1f, 0);
        seta.lineTo(-t * 0.6f, t);
        seta.close();

        setClickable(false);
        setFocusable(false);
    }

    public void setCentro(float x, float y) {
        centroX = x;
        centroY = y;
        invalidate();
    }

    public void setRaio(float raio) {
        this.raio = raio;
        invalidate();
    }

    public void setProgresso(float progresso) {
        this.progresso = Math.max(0, Math.min(1, progresso));
        invalidate();
    }

    public void setAnimalDentro(boolean dentro) {
        if (animalDentro != dentro) {
            animalDentro = dentro;
            invalidate();
        }
    }

    public void setConcluido() {
        concluido = true;
        progresso = 1;
        indicadorVisivel = false;
        segundosRestantes = -1;
        invalidate();
    }

    // ===== etapa 2 =====

    //Seta na borda apontando para o animal fora da tela
    public void setIndicador(boolean visivel, float anguloGraus) {
        indicadorVisivel = visivel;
        anguloIndicador = anguloGraus;
        invalidate();
    }

    public void setTempoRestante(int segundos, boolean alerta) {
        segundosRestantes = segundos;
        alertaTempo = alerta;
        invalidate();
    }

    //Substitui a dica por ~2 s (ex.: "Ele se assustou e correu!")
    public void setMensagemTemporaria(String mensagem) {
        mensagemTemporaria = mensagem;
        fimMensagem = SystemClock.uptimeMillis() + DURACAO_MENSAGEM_MS;
        invalidate();
        postInvalidateDelayed(DURACAO_MENSAGEM_MS + 50); //redesenha para voltar à dica normal
    }

    public void setFugiu() {
        fugiu = true;
        progresso = 0;
        animalDentro = false;
        indicadorVisivel = false;
        segundosRestantes = -1;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (raio <= 0) return;

        float cx = centroX >= 0 ? centroX : getWidth() / 2f;
        float cy = centroY >= 0 ? centroY : getHeight() / 2f;

        //círculo: pontilhado enquanto procura, contínuo quando conclui
        pincelCirculo.setColor((animalDentro || concluido) && !fugiu ? COR_DENTRO : COR_NEUTRA);
        pincelCirculo.setPathEffect(concluido ? null : tracejado);
        canvas.drawCircle(cx, cy, raio, pincelCirculo);

        //anel de progresso por fora do círculo, começando no topo
        if (progresso > 0) {
            float folga = 8 * densidade;
            areaArco.set(cx - raio - folga, cy - raio - folga, cx + raio + folga, cy + raio + folga);
            canvas.drawArc(areaArco, -90, 360 * progresso, false, pincelProgresso);
        }

        pincelTexto.setColor(Color.WHITE);
        canvas.drawText(textoDica(), cx, cy + raio + 40 * densidade, pincelTexto);

        //cronômetro do encontro, discreto no topo (abaixo dos rótulos "novo" e do nome)
        if (segundosRestantes >= 0) {
            pincelTexto.setColor(alertaTempo ? COR_ALERTA : Color.WHITE);
            String tempo = String.format("%d:%02d", segundosRestantes / 60, segundosRestantes % 60);
            canvas.drawText(tempo, getWidth() / 2f, 64 * densidade, pincelTexto);
            pincelTexto.setColor(Color.WHITE);
        }

        if (indicadorVisivel) desenhaSeta(canvas);
    }

    private String textoDica() {
        if (fugiu) return "Fugiu!";
        if (concluido) return "Catalogado!";
        if (mensagemTemporaria != null && SystemClock.uptimeMillis() < fimMensagem) return mensagemTemporaria;
        if (animalDentro) return "Segure firme...";
        if (indicadorVisivel) return "Procure o animal ao redor";
        return "Mantenha o animal no círculo";
    }

    //Ponto onde o raio que sai do centro da tela no ângulo do animal cruza a borda (com margem)
    private void desenhaSeta(Canvas canvas) {
        float margem = 40 * densidade;
        float meiaLargura = getWidth() / 2f - margem;
        float meiaAltura = getHeight() / 2f - margem;
        double rad = Math.toRadians(anguloIndicador);
        float dx = (float) Math.cos(rad);
        float dy = (float) Math.sin(rad);
        float tx = Math.abs(dx) > 1e-4 ? meiaLargura / Math.abs(dx) : Float.MAX_VALUE;
        float ty = Math.abs(dy) > 1e-4 ? meiaAltura / Math.abs(dy) : Float.MAX_VALUE;
        float t = Math.min(tx, ty);

        canvas.save();
        canvas.translate(getWidth() / 2f + dx * t, getHeight() / 2f + dy * t);
        canvas.rotate(anguloIndicador);
        canvas.drawPath(seta, pincelContornoSeta);
        canvas.drawPath(seta, pincelSeta);
        canvas.restore();
    }
}
