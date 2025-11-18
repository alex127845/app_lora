package com.example.lora_gtr;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity implements BLEService.ConnectionCallback {

    // Constantes para permisos y requests
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSIONS = 2;

    // Modos de operación
    public static final int MODE_NONE = 0;
    public static final int MODE_TRANSMITTER = 1;
    public static final int MODE_RECEIVER = 2;

    // Componentes Bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private BLEService bluetoothService;
    private LoRaConfigManager configManager;

    // UI Components
    private BottomNavigationView bottomNavigationView;

    // Estado
    private String connectedDeviceName = "";
    private int currentMode = MODE_NONE;
    private boolean isConnected = false;

    // Tags para fragments
    private static final String TAG_CONNECTION = "ConnectionFragment";
    private static final String TAG_FILE = "FileFragment";
    private static final String TAG_SETTING = "SettingFragment";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Configurar ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("LoRa Gateway Controller");
            getSupportActionBar().setElevation(4);
        }

        // Inicializar Bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "❌ Bluetooth no disponible en este dispositivo", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Solicitar permisos
        checkPermissions();

        // Inicializar servicio BLE
        bluetoothService = new BLEService(this, handler, this);
        configManager = new LoRaConfigManager(bluetoothService);

        // Setup Bottom Navigation
        setupBottomNavigation();

        // Cargar fragment inicial solo si es primera creación
        if (savedInstanceState == null) {
            loadFragment(TAG_CONNECTION);
        }
    }

    /**
     * Configurar Bottom Navigation
     */
    private void setupBottomNavigation() {
        bottomNavigationView = findViewById(R.id.bottonNavigationView);

        // Configurar listener
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.conn) {
                loadFragment(TAG_CONNECTION);
                return true;
            } else if (itemId == R.id.file) {
                if (!isConnected) {
                    Toast.makeText(this, "⚠️ Conecta un dispositivo primero", Toast.LENGTH_SHORT).show();
                    return false;
                }
                loadFragment(TAG_FILE);
                return true;
            } else if (itemId == R.id.setting) {
                if (!isConnected) {
                    Toast.makeText(this, "⚠️ Conecta un dispositivo primero", Toast.LENGTH_SHORT).show();
                    return false;
                }
                loadFragment(TAG_SETTING);
                return true;
            }

            return false;
        });
    }

    /**
     * Cargar fragment en el contenedor
     */
    private void loadFragment(String tag) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment fragment = fragmentManager.findFragmentByTag(tag);

        // Si el fragment no existe, crearlo
        if (fragment == null) {
            switch (tag) {
                case TAG_CONNECTION:
                    fragment = new ConnectionFragment();
                    break;
                case TAG_FILE:
                    fragment = new FileFragment();
                    break;
                case TAG_SETTING:
                    fragment = new SettingFragment();
                    break;
                default:
                    return;
            }
        }

        // Ocultar todos los fragments
        FragmentTransaction transaction = fragmentManager.beginTransaction();

        for (Fragment frag : fragmentManager.getFragments()) {
            if (frag != null) {
                transaction.hide(frag);
            }
        }

        // Mostrar o agregar el fragment actual
        if (fragment.isAdded()) {
            transaction.show(fragment);
        } else {
            transaction.add(R.id.fragment_container, fragment, tag);
        }

        transaction.commit();
    }

    /**
     * Verificar y solicitar permisos
     */
    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] permissions = {
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };

            boolean allGranted = true;
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (!allGranted) {
                ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_PERMISSIONS);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (!allGranted) {
                Toast.makeText(this,
                        "⚠️ Se necesitan permisos para usar Bluetooth",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED) {
                startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "✅ Bluetooth habilitado", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "❌ Bluetooth necesario para funcionar", Toast.LENGTH_LONG).show();
            }
        }
    }

    public void connectToDevice(BluetoothDevice device) {
        if (bluetoothService != null) {
            bluetoothService.connect(device);
        }
    }

    public void disconnectDevice() {
        if (bluetoothService != null) {
            bluetoothService.disconnect();
        }

        currentMode = MODE_NONE;
        connectedDeviceName = "";
        isConnected = false;

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("LoRa Gateway Controller");
        }

        notifyFragmentsDisconnected();
        bottomNavigationView.setSelectedItemId(R.id.conn);
    }

    private final Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case BLEService.MESSAGE_STATE_CHANGE:  // ← CORREGIDO
                    handleStateChange(msg.arg1);
                    break;

                case BLEService.MESSAGE_READ:  // ← CORREGIDO
                    byte[] readBuf = (byte[]) msg.obj;
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    processReceivedData(readMessage);
                    break;

                case BLEService.MESSAGE_DEVICE_NAME:  // ← CORREGIDO
                    connectedDeviceName = msg.getData().getString("device_name");
                    Toast.makeText(MainActivity.this,
                            "✅ Conectado a " + connectedDeviceName,
                            Toast.LENGTH_SHORT).show();
                    detectDeviceType(connectedDeviceName);
                    break;

                case BLEService.MESSAGE_TOAST:  // ← CORREGIDO
                    Toast.makeText(MainActivity.this,
                            msg.getData().getString("toast"),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
            return true;
        }
    });

    private void handleStateChange(int state) {
        switch (state) {
            case BLEService.STATE_CONNECTED:  // ← CORREGIDO
                isConnected = true;
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle("🟢 Conectado a " + connectedDeviceName);
                }
                break;

            case BLEService.STATE_CONNECTING:  // ← CORREGIDO
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle("🟡 Conectando...");
                }
                break;

            case BLEService.STATE_NONE:  // ← CORREGIDO
                isConnected = false;
                currentMode = MODE_NONE;
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle("🔴 No conectado");
                }
                break;
        }
    }

    private void detectDeviceType(String deviceName) {
        if (deviceName == null) return;

        if (deviceName.toUpperCase().contains("TX")) {
            currentMode = MODE_TRANSMITTER;
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("📡 TX: " + deviceName);
            }
            Toast.makeText(this, "📡 Modo TRANSMISOR activado", Toast.LENGTH_SHORT).show();

        } else if (deviceName.toUpperCase().contains("RX")) {
            currentMode = MODE_RECEIVER;
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("📥 RX: " + deviceName);
            }
            Toast.makeText(this, "📥 Modo RECEPTOR activado", Toast.LENGTH_SHORT).show();
        } else {
            currentMode = MODE_NONE;
            Toast.makeText(this, "⚠️ Dispositivo desconocido", Toast.LENGTH_SHORT).show();
        }

        notifyFragmentsModeChanged();
    }

    private void notifyFragmentsModeChanged() {
        FragmentManager fm = getSupportFragmentManager();

        FileFragment fileFragment = (FileFragment) fm.findFragmentByTag(TAG_FILE);
        if (fileFragment != null && fileFragment.isAdded()) {
            fileFragment.onModeChanged(currentMode);
        }

        SettingFragment settingFragment = (SettingFragment) fm.findFragmentByTag(TAG_SETTING);
        if (settingFragment != null && settingFragment.isAdded()) {
            settingFragment.onModeChanged(currentMode);
        }

        ConnectionFragment connectionFragment = (ConnectionFragment) fm.findFragmentByTag(TAG_CONNECTION);
        if (connectionFragment != null && connectionFragment.isAdded()) {
            connectionFragment.onConnectionStateChanged(true);
        }
    }

    private void notifyFragmentsDisconnected() {
        FragmentManager fm = getSupportFragmentManager();

        FileFragment fileFragment = (FileFragment) fm.findFragmentByTag(TAG_FILE);
        if (fileFragment != null && fileFragment.isAdded()) {
            fileFragment.onModeChanged(MODE_NONE);
        }

        SettingFragment settingFragment = (SettingFragment) fm.findFragmentByTag(TAG_SETTING);
        if (settingFragment != null && settingFragment.isAdded()) {
            settingFragment.onModeChanged(MODE_NONE);
        }

        ConnectionFragment connectionFragment = (ConnectionFragment) fm.findFragmentByTag(TAG_CONNECTION);
        if (connectionFragment != null && connectionFragment.isAdded()) {
            connectionFragment.onConnectionStateChanged(false);
        }
    }

    private void processReceivedData(String data) {
        android.util.Log.d("MainActivity", "Datos recibidos: " + data);

        FragmentManager fm = getSupportFragmentManager();

        if (data.startsWith("[FILES_START]") || data.startsWith("[FILES_END]") || data.contains(",")) {
            FileFragment fileFragment = (FileFragment) fm.findFragmentByTag(TAG_FILE);
            if (fileFragment != null && fileFragment.isAdded()) {
                fileFragment.onDataReceived(data);
            }
        } else if (data.startsWith("{") && data.contains("\"bw\"")) {
            SettingFragment settingFragment = (SettingFragment) fm.findFragmentByTag(TAG_SETTING);
            if (settingFragment != null && settingFragment.isAdded()) {
                settingFragment.onConfigReceived(data);
            }
        } else if (data.startsWith("[FILE_START:")) {
            FileFragment fileFragment = (FileFragment) fm.findFragmentByTag(TAG_FILE);
            if (fileFragment != null && fileFragment.isAdded()) {
                fileFragment.onFileDownloadStart(data);
            }
        } else if (data.equals("[FILE_END]")) {
            FileFragment fileFragment = (FileFragment) fm.findFragmentByTag(TAG_FILE);
            if (fileFragment != null && fileFragment.isAdded()) {
                fileFragment.onFileDownloadEnd();
            }
        }
    }

    @Override
    public void onConnected() {
        runOnUiThread(() -> {
            Toast.makeText(this, "✅ Conexión establecida", Toast.LENGTH_SHORT).show();

            new Handler().postDelayed(() -> {
                if (configManager != null) {
                    configManager.getConfig();
                    configManager.listFiles();
                }
            }, 500);
        });
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(() -> {
            Toast.makeText(this, "🔴 Desconectado", Toast.LENGTH_SHORT).show();
            currentMode = MODE_NONE;
            isConnected = false;
            connectedDeviceName = "";

            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("LoRa Gateway Controller");
            }

            notifyFragmentsDisconnected();
        });
    }

    @Override
    public void onDataReceived(byte[] data) {
        String message = new String(data);
        processReceivedData(message);
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            Toast.makeText(this, "❌ Error: " + error, Toast.LENGTH_LONG).show();
        });
    }

    public BLEService getBluetoothService() {  // ← CORREGIDO tipo de retorno
        return bluetoothService;
    }

    public LoRaConfigManager getConfigManager() {
        return configManager;
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    public int getCurrentMode() {
        return currentMode;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public String getConnectedDeviceName() {
        return connectedDeviceName;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_disconnect) {
            if (isConnected) {
                disconnectDevice();
            } else {
                Toast.makeText(this, "No hay dispositivo conectado", Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (id == R.id.action_refresh) {
            if (isConnected && configManager != null) {
                configManager.getConfig();
                configManager.listFiles();
                Toast.makeText(this, "🔄 Actualizando...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Conecta un dispositivo primero", Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (id == R.id.action_about) {
            showAboutDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showAboutDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("LoRa Gateway Controller")
                .setMessage("Versión 1.0\n\n" +
                        "Control de dispositivos Heltec V3 por Bluetooth\n\n" +
                        "• Transmisión de archivos por LoRa\n" +
                        "• Configuración de parámetros\n" +
                        "• Gestión de archivos")
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothService != null) {
            bluetoothService.disconnect();
        }
    }
}