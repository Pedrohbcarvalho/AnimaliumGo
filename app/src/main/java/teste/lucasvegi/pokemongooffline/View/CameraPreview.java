package teste.lucasvegi.pokemongooffline.View;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;

/** A basic Camera preview class */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "CameraPreview";
    private SurfaceHolder mHolder;
    private Camera camera;


    public CameraPreview(Context context) {
        super(context);
        //this.camera = camera;

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        // deprecated setting, but required on Android versions prior to 3.0
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }


    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, acquire the camera and tell it where
        // to draw.
        camera = Camera.open();
        try {
            camera.setPreviewDisplay(holder);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // Surface will be destroyed when we return, so stop the preview.
        // Because the CameraDevice object is not a shared resource, it's very
        // important to release it when the activity is paused.
        camera.stopPreview();
        camera.release();
        camera = null;
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // Now that the size is known, set up the camera parameters and begin
        // the preview.

        /*
        REMOÇÃO para resolver problema de compatibilidade

        Camera.Parameters parameters = camera.getParameters();
        parameters.setPreviewSize(w, h);
        camera.setParameters(parameters);*/

        try{
            camera.setPreviewDisplay(holder); //novo. resolve problema de incompatibilidade
            camera.setDisplayOrientation(90); //orientação portrait
            camera.startPreview();
        }catch (Exception e){
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }

    }

    public void releaseCamera(){
        if (camera != null){
            camera.release();        // release the camera for other applications
            camera = null;
        }
    }

    // Usado pela VgiActivity: tira a foto com a mesma camera do preview, sem sair do app.
    // Retorna false se a camera ainda nao estiver pronta. A CapturaActivity nao usa este metodo.
    public boolean tirarFoto(Camera.PictureCallback callback) {
        if (camera == null) return false;

        try {
            // Grava no JPEG a mesma orientacao portrait usada no preview (90 graus)
            Camera.Parameters params = camera.getParameters();
            params.setRotation(90);
            camera.setParameters(params);
        } catch (Exception e) {
            Log.d(TAG, "Nao foi possivel definir a rotacao da foto: " + e.getMessage());
        }

        try {
            camera.takePicture(null, null, callback);
            return true;
        } catch (Exception e) {
            Log.d(TAG, "Erro ao tirar foto: " + e.getMessage());
            return false;
        }
    }

}