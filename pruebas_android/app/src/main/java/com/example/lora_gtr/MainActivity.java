package com.example.lora_gtr;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class MainActivity extends AppCompatActivity implements BluetoothService.ConnectionCallback {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSIONS = 2;
    private static final int REQUEST_CONNECT_DEVICE = 3;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothService bluetoothService;
    private LoRaConfigManager configManager;

    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    private String connectedDeviceName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializar Bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth no disponible", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Solicitar permisos
        checkPermissions();

        // Inicializar servicio Bluetooth
        bluetoothService = new BluetoothService(this, handler, this);
        configManager = new LoRaConfigManager(bluetoothService);

        // Setup UI
        setupTabs();
    }

    private void setupTabs() {
        tabLayout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.view_pager);

        // Configurar ViewPager con fragmentos
        // (implementar adapter con FilesFragment, ConfigFragment, StatusFragment)
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            String[] permissions = {
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };

            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
                    return;
                }
            }
        } else {
            // Android 11 o anterior
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_PERMISSIONS);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
    }

    public void connectToDevice() {
        Intent serverIntent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CONNECT_DEVICE && resultCode == RESULT_OK) {
            String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
            bluetoothService.connect(device);
        }
    }

    // Handler para mensajes del servicio Bluetooth
    private final Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case BluetoothService.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothService.STATE_CONNECTED:
                            setTitle("Conectado a " + connectedDeviceName);
                            break;
                        case BluetoothService.STATE_CONNECTING:
                            setTitle("Conectando...");
                            break;
                        case BluetoothService.STATE_NONE:
                            setTitle("No conectado");
                            break;
                    }
                    break;

                case BluetoothService.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    processReceivedData(readMessage);
                    break;

                case BluetoothService.MESSAGE_DEVICE_NAME:
                    connectedDeviceName = msg.getData().getString("device_name");
                    Toast.makeText(MainActivity.this,
                            "Conectado a " + connectedDeviceName,
                            Toast.LENGTH_SHORT).show();
                    break;

                case BluetoothService.MESSAGE_TOAST:
                    Toast.makeText(MainActivity.this,
                            msg.getData().getString("toast"),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
            return true;
        }
    });

    private void processReceivedData(String data) {
        // Procesar respuestas del ESP32
        // Parsear JSON, actualizar UI, etc.
    }

    // Callbacks de BluetoothService.ConnectionCallback
    @Override
    public void onConnected() {
        runOnUiThread(() -> {
            Toast.makeText(this, "Conexión establecida", Toast.LENGTH_SHORT).show();
            // Solicitar configuración inicial
            configManager.getConfig();
            configManager.listFiles();
        });
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(() -> {
            Toast.makeText(this, "Desconectado", Toast.LENGTH_SHORT).show();
            setTitle("No conectado");
        });
    }

    @Override
    public void onDataReceived(byte[] data) {
        // Procesar datos recibidos
        String message = new String(data);
        processReceivedData(message);
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            Toast.makeText(this, "Error: " + error, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothService != null) {
            bluetoothService.disconnect();
        }
    }

    public LoRaConfigManager getConfigManager() {
        return configManager;
    }

    public BluetoothService getBluetoothService() {
        return bluetoothService;
    }
}