package com.example.soa;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private Button botonIngresar;
    private TextView btErrorView;
    private TextView modalBg;

    String[] permissions= new String[]{
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
    };

    public static final int MULTIPLE_PERMISSIONS = 10;
    private final String DEVICE_NAME = "HC-05";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializar BluetoothAdapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Referencias a los elementos del layout
        botonIngresar = findViewById(R.id.botonIngresar);
        btErrorView = findViewById(R.id.btErrorView);
        modalBg = findViewById(R.id.modalBg);


        // Ocultar mensaje de error inicialmente
        btErrorView.setVisibility(TextView.INVISIBLE);
        modalBg.setVisibility(TextView.INVISIBLE);

        // Evento de clic en el botón Ingresar
        botonIngresar.setOnClickListener(v -> {
            if (bluetoothAdapter == null) {
                // Dispositivo no soporta Bluetooth
                mostrarError("El dispositivo no soporta Bluetooth");
            } else if (!bluetoothAdapter.isEnabled()) {
                // Bluetooth no está activado
                mostrarError("El Bluetooth no está activado");
            } else if (!isDeviceConnectedByName(DEVICE_NAME)) {
                // El dispositivo específico no está conectado
                mostrarError("No estás conectado al dispositivo Bluetooth con el nombre específico");
            } else {
                ocultarError();
                Toast.makeText(MainActivity.this, "Conectado correctamente al dispositivo con nombre: " + DEVICE_NAME, Toast.LENGTH_SHORT).show();
                // Aquí puedes agregar el código para la siguiente actividad o acción
            }
        });

        modalBg.setOnClickListener(v -> ocultarError());
    }

    private boolean isDeviceConnectedByName(String deviceName) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            return false; // Bluetooth no soportado o no habilitado
        }

        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
            if (device.getName().equals(deviceName)) {
                return true;
            }
        }
        return false; // Ningún dispositivo emparejado está conectado
    }

    // Función para mostrar el mensaje de error
    private void mostrarError(String mensaje) {
        btErrorView.setText(mensaje);
        btErrorView.setVisibility(TextView.VISIBLE);
        modalBg.setVisibility(TextView.VISIBLE);
    }

    // Función para ocultar el mensaje de error
    private void ocultarError() {
        btErrorView.setVisibility(TextView.INVISIBLE);
        modalBg.setVisibility(TextView.INVISIBLE);
    }


    //Metodo que chequea si estan habilitados los permisos
    private  boolean checkPermissions() {
        int result;
        List<String> listPermissionsNeeded = new ArrayList<>();

        //Se chequea si la version de Android es menor a la 6


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