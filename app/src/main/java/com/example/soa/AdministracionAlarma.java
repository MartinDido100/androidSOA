package com.example.soa;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class AdministracionAlarma extends AppCompatActivity implements SensorEventListener {


    Button btnApagar;
    Button btnEncender;
    TextView txtEstado;
    TextView activationText;
    private Vibrator vibrator;


    Handler bluetoothIn;
    final int handlerState = 0; //used to identify handler message

    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private final StringBuilder recDataString = new StringBuilder();
    private MediaPlayer musicPlayer;
    private  MediaPlayer mediaPlayerAlarm;
    private MediaPlayer armed;
    private ConnectedThread mConnectedThread;

    // SPP UUID service  - Funciona en la mayoria de los dispositivos
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // String for MAC address del Hc05
    private static String address = null;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastShakeTime = 0;
    private static final int SHAKE_THRESHOLD_GRAVITY = 5;
    private static final int SHAKE_TIME_LAPSE = 5000;
    NotificationManager notificationManager;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_administracion_alarma);

        //Se definen los componentes del layout
        btnApagar=(Button)findViewById(R.id.btnApagar);
        btnEncender=(Button)findViewById(R.id.btnEncender);
        txtEstado=(TextView)findViewById(R.id.estadoAlarma);
        activationText=(TextView)findViewById(R.id.activationText);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        //Se crea el canal de notificaciones
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "alarma_channel",
                    "Alarma Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notificaciones para eventos de alarma");
            notificationManager.createNotificationChannel(channel);
        }

        //obtengo el adaptador del bluethoot
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        //defino el Handler de comunicacion entre el hilo Principal  el secundario.
        //El hilo secundario va a mostrar informacion al layout atraves utilizando indeirectamente a este handler
        bluetoothIn = Handler_Msg_Hilo_Principal();

        //defino los handlers para los botones Apagar y encender
        btnEncender.setOnClickListener(btnEncenderListener);
        btnApagar.setOnClickListener(btnApagarListener);

        // Inicializa el Sensor Manager
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // Inicializa el MediaPlayer con el archivo de sonido en res/raw
        musicPlayer = MediaPlayer.create(this, R.raw.musica);
        mediaPlayerAlarm = MediaPlayer.create(this, R.raw.alarma);
        armed = MediaPlayer.create(this, R.raw.armed);
    }

    @SuppressLint("MissingPermission")
    @Override
    //Cada vez que se detecta el evento OnResume se establece la comunicacion con el HC05, creando un
    //socketBluethoot
    public void onResume() {
        super.onResume();

        //Obtengo el parametro, aplicando un Bundle, que me indica la Mac Adress del HC05
        Intent intent=getIntent();
        Bundle extras=intent.getExtras();

        address= extras.getString("Direccion_Bluethoot");

        BluetoothDevice device = btAdapter.getRemoteDevice(address);

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        //se realiza la conexion del Bluethoot crea y se conectandose a atraves de un socket
        try
        {
            btSocket = createBluetoothSocket(device);
        }
        catch (IOException e)
        {
            Log.e("Error", "Socket creation failed");
        }
        // Establish the Bluetooth socket connection.
        try
        {
            btSocket.connect();
        }
        catch (IOException e)
        {
            try
            {
                btSocket.close();
            }
            catch (IOException e2)
            {
               Log.e("Error", "Socket creation failed");
            }
        }

        //Una establecida la conexion con el Hc05 se crea el hilo secundario, el cual va a recibir
        // los datos de Arduino atraves del bluethoot
        mConnectedThread = new ConnectedThread(btSocket);
        mConnectedThread.start();

        //I send a character when resuming.beginning transmission to check device is connected
        //If it is not an exception will be thrown in the write method and finish() will be called
        mConnectedThread.write("2");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Libera los recursos del MediaPlayer cuando la actividad se destruye
        if (musicPlayer != null) {
            musicPlayer.release();
            musicPlayer = null;
        }

        if (mediaPlayerAlarm != null) {
            mediaPlayerAlarm.release();
            mediaPlayerAlarm = null;
        }

        if (armed != null) {
            armed.release();
            armed = null;
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private void vibrate() {
        if (vibrator != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                long[] pattern = {0, 1000, 1000};
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            }
        }
    }

    @Override
    //Cuando se ejecuta el evento onPause se cierra el socket Bluethoot, para no recibiendo datos
    public void onPause()
    {
        super.onPause();
        sensorManager.unregisterListener(this);
        try
        {
            btSocket.close();
        } catch (IOException e2) {
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            try {
                detectShake(event);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    private void detectShake(SensorEvent event) throws IOException {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        // Calcula la fuerza del movimiento
        float gX = x / SensorManager.GRAVITY_EARTH;
        float gY = y / SensorManager.GRAVITY_EARTH;
        float gZ = z / SensorManager.GRAVITY_EARTH;

        // Magnitud del vector G
        float gForce = (float) Math.sqrt(gX * gX + gY * gY + gZ * gZ);

        if (gForce > SHAKE_THRESHOLD_GRAVITY) {
            final long now = System.currentTimeMillis();

            // Solo envía una señal si la sacudida ocurrió después del intervalo de tiempo especificado
            if (now - lastShakeTime > SHAKE_TIME_LAPSE) {
                lastShakeTime = now;

                if(txtEstado.getText().equals("Apagado")){
                    encenderAlarma();
                }else{
                    apagarAlarma();
                }

            }
        }
    }

    void sendNotification(String title, String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "alarma_channel")
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }

    void handleCurrentState(String state) {
        switch (state) {
            case "ON":
                activationText.setText("Alarma desactivada");
                btnApagar.setVisibility(View.INVISIBLE);
                btnEncender.setVisibility(View.VISIBLE);
                txtEstado.setText("Apagado");
                txtEstado.setTextColor(Color.RED);
                break;
            case "ARMED":
                activationText.setText("Alarma en escucha");
                btnApagar.setVisibility(View.VISIBLE);
                btnEncender.setVisibility(View.INVISIBLE);
                txtEstado.setText("Encendido");
                txtEstado.setTextColor(Color.GREEN);
                armed.start();
                break;
            case "ACTIVATED":
                vibrate();
                activationText.setText("Alarma sonando");
                btnApagar.setVisibility(View.VISIBLE);
                btnEncender.setVisibility(View.INVISIBLE);
                txtEstado.setText("Encendido");
                txtEstado.setTextColor(Color.GREEN);
                mediaPlayerAlarm.setLooping(true);
                break;
            case "ACTIVATEDR":
                vibrate();
                mediaPlayerAlarm.start();
                activationText.setText("Reed switch detectado");
                sendNotification("Reed switch activado", "Se detectó una apertura con el reed switch.");
                mediaPlayerAlarm.setLooping(true);
                break;
            case "ACTIVATEDM":
                vibrate();
                mediaPlayerAlarm.start();
                activationText.setText("Movimiento detectado");
                sendNotification("Movimiento detectado", "Se ha detectado movimiento.");
                mediaPlayerAlarm.setLooping(true);
                break;
            default:
                activationText.setText(state);
        }
    }

    //Metodo que crea el socket bluethoot
    @SuppressLint("MissingPermission")
    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {

        return  device.createRfcommSocketToServiceRecord(BTMODULEUUID);
    }

    //Handler que sirve que permite mostrar datos en el Layout al hilo secundario
    private Handler Handler_Msg_Hilo_Principal ()
    {
        return  new Handler(Looper.getMainLooper()) {
            public void handleMessage(@NonNull android.os.Message msg)
            {
                //si se recibio un msj del hilo secundario
                if (msg.what == handlerState)
                {
                    //voy concatenando el msj
                    String readMessage = (String) msg.obj;
                    recDataString.append(readMessage);
                    int endOfLineIndex = recDataString.indexOf("\n");

                    //si se recibio un msj completo
                    if (endOfLineIndex > 0)
                    {
                        Log.d("Mensaje Bluetooth Recibido: ",recDataString.substring(0, endOfLineIndex));
                        handleCurrentState(recDataString.substring(0, endOfLineIndex));
                        recDataString.delete(0, recDataString.length());
                    }
                }
            }
        };

    }

    private void encenderAlarma(){
        mConnectedThread.write("1");  // Send "1" via Bluetooth
        btnApagar.setVisibility(View.VISIBLE);
        btnEncender.setVisibility(View.INVISIBLE);
        txtEstado.setText("Encendido");
        txtEstado.setTextColor(Color.GREEN);
        armed.start();
    }

    //Listener del boton encender que envia  msj para enceder Led a Arduino atraves del Bluethoot
    private final View.OnClickListener btnEncenderListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            encenderAlarma();
        }
    };

    private void apagarAlarma() throws IOException {
        mConnectedThread.write("1");
        btnApagar.setVisibility(View.INVISIBLE);
        btnEncender.setVisibility(View.VISIBLE);
        txtEstado.setText("Apagado");
        txtEstado.setTextColor(Color.RED);
        if(mediaPlayerAlarm.isLooping()){
            mediaPlayerAlarm.setLooping(false);
            mediaPlayerAlarm.stop();
            mediaPlayerAlarm.prepare();
            musicPlayer.start();
        }
        vibrator.cancel();
    }

    //Listener del boton encender que envia  msj para Apagar Led a Arduino atraves del Bluethoot
    private final View.OnClickListener btnApagarListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                apagarAlarma();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    };

    //******************************************** Hilo secundario del Activity**************************************
    //*************************************** recibe los datos enviados por el HC05**********************************

    private class ConnectedThread extends Thread
    {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        //Constructor de la clase del hilo secundario
        public ConnectedThread(BluetoothSocket socket)
        {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try
            {
                //Create I/O streams for connection
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        //metodo run del hilo, que va a entrar en una espera activa para recibir los msjs del HC05
        public void run()
        {
            byte[] buffer = new byte[256];
            int bytes;


            //el hilo secundario se queda esperando mensajes del HC05
            while (true)
            {
                try
                {
                    //se leen los datos del Bluethoot
                    bytes = mmInStream.read(buffer);
                    String readMessage = new String(buffer, 0, bytes);

                    //se muestran en el layout de la activity, utilizando el handler del hilo
                    // principal antes mencionado
                    bluetoothIn.obtainMessage(handlerState, bytes, -1, readMessage).sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }


        //write method
        public void write(String input) {
            byte[] msgBuffer = input.getBytes();           //converts entered String into bytes
            try {
                mmOutStream.write(msgBuffer);                //write bytes over BT connection via outstream
            } catch (IOException e) {
                handleBluetoothError();
            }
        }

        private void handleBluetoothError() {
            Intent intent = new Intent(AdministracionAlarma.this, MainActivity.class);
            intent.putExtra("errorFlag", "Bluetooth connection failed");
            startActivity(intent);
            finish();
        }
    }

}