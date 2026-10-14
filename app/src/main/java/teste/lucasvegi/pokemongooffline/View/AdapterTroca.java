package teste.lucasvegi.pokemongooffline.View;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.ImageView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.util.List;
import teste.lucasvegi.pokemongooffline.Util.BitmapUtil;

import teste.lucasvegi.pokemongooffline.R;

public class AdapterTroca extends BaseAdapter {

    private List<BluetoothDevice> devices;
    private Activity activity;

    public AdapterTroca(List<BluetoothDevice> _devices, Activity _activity) {
        try {
            this.devices = _devices;
            this.activity = _activity;
        }catch (Exception e){
            Log.e("LISTA_USUARIOS", "ERRO: " + e.getMessage());
        }
    }

    @Override
    public int getCount() {
        return devices.size();
    }

    @Override
    public Object getItem(int position) {
        return devices.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        try {

            View view = activity.getLayoutInflater().inflate(R.layout.lista_troca_personalizada, parent, false);
            BluetoothDevice dev = devices.get(position);

            Log.i("LISTA_USUARIOS", "Montando lista para " + dev.getName());
            TextView device_name = (TextView)
                    view.findViewById(R.id.troca_deviceName);
            device_name.setText(dev.getName());

            ImageView fotoUsuario = (ImageView) view.findViewById(R.id.troca_userImg);

            if(fotoUsuario != null) {
                // ATENÇÃO 2: Troque 'sua_foto_gigante' pelo nome do arquivo drawable que você está exibindo (ex: R.drawable.male_profile)
                Bitmap fotoOriginal = BitmapFactory.decodeResource(activity.getResources(), R.drawable.male_profile);

                // Redimensiona a foto para 150x150 pixels mantendo a qualidade.
                // Você pode alterar esses números (150) para deixar maior ou menor na lista.
                Bitmap fotoRedimensionada = Bitmap.createScaledBitmap(fotoOriginal, 150, 150, true);

                // Aplica a imagem redimensionada na tela
                fotoUsuario.setImageBitmap(fotoRedimensionada);
            }

            return view;

        }catch (Exception e){
            Log.e("LISTA_USUARIOS", "ERRO: " + e.getMessage());
            return null;
        }
    }

}
