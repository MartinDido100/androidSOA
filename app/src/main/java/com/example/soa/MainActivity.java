package com.example.soa;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.Manifest;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private Button botonIngresar;
    private TextView modalBg;
    private ProgressBar progressBar;
    private boolean estaBonded = false;
    String direccionBluethoot;


    String[] permissions= new String[]{
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.VIBRATE,
            Manifest.permission.POST_NOTIFICATIONS
    };

    public static final int MULTIPLE_PERMISSIONS = 10;
    private final String DEVICE_NAME = "HC-05";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

            checkPermissions();
            Intent intent = getIntent();
            String errorFlag = intent.getStringExtra("errorFlag");
            if (errorFlag != null && errorFlag.equals("Bluetooth connection failed")) {
                Toast.makeText(this, "Silentwave no conectado", Toast.LENGTH_LONG).show();
            }

            // Inicializar BluetoothAdapter
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            // Referencias a los elementos del layout
            botonIngresar = findViewById(R.id.botonIngresar);
            modalBg = findViewById(R.id.modalBg);
            progressBar = findViewById(R.id.progressBar);


            // Ocultar mensaje de error inicialmente
            modalBg.setVisibility(TextView.INVISIBLE);

            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
            filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
            filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);


            // Evento de clic en el botón Ingresar
            botonIngresar.setOnClickListener(v -> {
                if (bluetoothAdapter == null) {
                    // Dispositivo no soporta Bluetooth
                    Toast.makeText(this, "El dispositivo no soporta Bluetooth", Toast.LENGTH_LONG).show();
                } else if (!bluetoothAdapter.isEnabled()) {
                    // Bluetooth no está activado
                    Toast.makeText(this, "El Bluetooth no está activado", Toast.LENGTH_LONG).show();
                } else if (!estaBonded && !isDeviceConnectedByName(DEVICE_NAME)) {
                    Toast.makeText(this, "El dispositivo Silentwave no está vinculado", Toast.LENGTH_LONG).show();
                }else {
                    showLoader();
                    Intent newIntent = new Intent(MainActivity.this, AdministracionAlarma.class);
                    newIntent.putExtra("Direccion_Bluethoot", direccionBluethoot);
                    startActivity(newIntent);
                    finish();
                }
            });

            this.registerReceiver(broadcastReceiver, filter);
    }

    @SuppressLint("MissingPermission")
    private boolean isDeviceConnectedByName(String deviceName) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            return false; // Bluetooth no soportado o no habilitado
        }

        for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
            if (device.getName().equals(deviceName)) {
                direccionBluethoot = device.getAddress();
                return true;
            }
        }
        return false; // Ningún dispositivo emparejado está conectado
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        BluetoothDevice device;
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){
                direccionBluethoot = device.getAddress();
                estaBonded = true;
            }
        }
    };

    public void showLoader() {
        modalBg.setVisibility(TextView.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
    }

    public void hideLoader() {
        progressBar.setVisibility(View.GONE);
    }

    //Metodo que chequea si estan habilitados los permisos
    private  boolean checkPermissions() {
        int result;
        List<String> listPermissionsNeeded = new ArrayList<>();

        for (String p:permissions) {
            result = ContextCompat.checkSelfPermission(this,p);
            if (result != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(p);
            }
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]),MULTIPLE_PERMISSIONS );
            return false;
        }
        return true;
    }

}