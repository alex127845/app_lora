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
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity implements BluetoothService.ConnectionCallback {

    // Constantes para permisos y requests
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSIONS = 2;

    // Modos de operación
    public static final int MODE_NONE = 0;
    public static final int MODE_TRANSMITTER = 1;  // TX
    public static final int MODE_RECEIVER = 2;     // RX

    // Componentes Bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothService bluetoothService;
    private LoRaConfigManager configManager;

    // UI Components
    private BottomNavigationView bottomNavigationView;

    // Estado
    private String connectedDeviceName = "";
    private int currentMode = MODE_NONE;
    private boolean isConnected = false;

    // Fragmentos
    private ConnectionFragment connectionFragment;
    private FileFragment fileFragment;
    private SettingFragment settingFragment;
    private Fragment activeFragment;

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

        // Inicializar servicio Bluetooth
        bluetoothService = new BluetoothService(this, handler, this);
        configManager = new LoRaConfigManager(bluetoothService);

        // Setup Bottom Navigation
        setupBottomNavigation();

        // Cargar fragment inicial (Connection)
        if (savedInstanceState == null) {
            loadFragment(connectionFragment);
            bottomNavigationView.setSelectedItemId(R.id.conn);
        }
    }

    /**
     * Configurar Bottom Navigation
     */
    private void setupBottomNavigation() {
        bottomNavigationView = findViewById(R.id.bottonNavigationView);

        // Crear fragmentos
        connectionFragment = new ConnectionFragment();
        fileFragment = new FileFragment();
        settingFragment = new SettingFragment();

        // Configurar listener
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.conn) {
                loadFragment(connectionFragment);
                return true;
            } else if (itemId == R.id.file) {
                if (!isConnected) {
                    Toast.makeText(this, "⚠️ Conecta un dispositivo primero", Toast.LENGTH_SHORT).show();
                    return false;
                }
                loadFragment(fileFragment);
                return true;
            } else if (itemId == R.id.setting) {
                if (!isConnected) {
                    Toast.makeText(this, "⚠️ Conecta un dispositivo primero", Toast.LENGTH_SHORT).show();
                    return false;
                }
                loadFragment(settingFragment);
                return true;
            }

            return false;
        });
    }

    /**
     * Cargar fragment en el contenedor
     */
    private void loadFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        if (activeFragment != null) {
            transaction.hide(activeFragment);
        }

        if (fragment.isAdded()) {
            transaction.show(fragment);
        } else {
            transaction.add(R.id.fragment_container, fragment);
        }

        transaction.commit();
        activeFragment = fragment;
    }

    /**
     * Verificar y solicitar permisos
     */
    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
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
            // Android 11 o anterior
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

        // Verificar si Bluetooth está habilitado
        if (!bluetoothAdapter.isEnabled()) {
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

    /**
     * Conectar a un dispositivo Bluetooth
     */
    public void connectToDevice(BluetoothDevice device) {
        if (bluetoothService != null) {
            bluetoothService.connect(device);
        }
    }

    /**
     * Desconectar del dispositivo actual
     */
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

        // Volver al fragment de conexión
        bottomNavigationView.setSelectedItemId(R.id.conn);
    }

    /**
     * Handler para mensajes del servicio Bluetooth
     */
    private final Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case BluetoothService.MESSAGE_STATE_CHANGE:
                    handleStateChange(msg.arg1);
                    break;

                case BluetoothService.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    processReceivedData(readMessage);
                    break;

                case BluetoothService.MESSAGE_DEVICE_NAME:
                    connectedDeviceName = msg.getData().getString("device_name");
                    Toast.makeText(MainActivity.this,
                            "✅ Conectado a " + connectedDeviceName,
                            Toast.LENGTH_SHORT).show();
                    detectDeviceType(connectedDeviceName);
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

    /**
     * Manejar cambios de estado de conexión
     */
    private void handleStateChange(int state) {
        switch (state) {
            case BluetoothService.STATE_CONNECTED:
                isConnected = true;
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle("🟢 Conectado a " + connectedDeviceName);
                }
                break;

            case BluetoothService.STATE_CONNECTING:
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle("🟡 Conectando...");
                }
                break;

            case BluetoothService.STATE_NONE:
                isConnected = false;
                currentMode = MODE_NONE;
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle("🔴 No conectado");
                }
                break;
        }
    }

    /**
     * Detectar tipo de dispositivo (TX o RX) automáticamente
     */
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

        // Notificar a los fragmentos del cambio de modo
        notifyFragmentsModeChanged();
    }

    /**
     * Notificar a los fragmentos que el modo cambió
     */
    private void notifyFragmentsModeChanged() {
        if (fileFragment != null && fileFragment.isAdded()) {
            fileFragment.onModeChanged(currentMode);
        }
        if (settingFragment != null && settingFragment.isAdded()) {
            settingFragment.onModeChanged(currentMode);
        }
        if (connectionFragment != null && connectionFragment.isAdded()) {
            connectionFragment.onConnectionStateChanged(true);
        }
    }

    /**
     * Notificar desconexión a los fragmentos
     */
    private void notifyFragmentsDisconnected() {
        if (fileFragment != null && fileFragment.isAdded()) {
            fileFragment.onModeChanged(MODE_NONE);
        }
        if (settingFragment != null && settingFragment.isAdded()) {
            settingFragment.onModeChanged(MODE_NONE);
        }
        if (connectionFragment != null && connectionFragment.isAdded()) {
            connectionFragment.onConnectionStateChanged(false);
        }
    }

    /**
     * Procesar datos recibidos desde el ESP32
     */
    private void processReceivedData(String data) {
        android.util.Log.d("MainActivity", "Datos recibidos: " + data);

        if (data.startsWith("[FILES_START]") || data.startsWith("[FILES_END]") || data.contains(",")) {
            if (fileFragment != null && fileFragment.isAdded()) {
                fileFragment.onDataReceived(data);
            }
        } else if (data.startsWith("{") && data.contains("\"bw\"")) {
            if (settingFragment != null && settingFragment.isAdded()) {
                settingFragment.onConfigReceived(data);
            }
        } else if (data.startsWith("[FILE_START:")) {
            if (fileFragment != null && fileFragment.isAdded()) {
                fileFragment.onFileDownloadStart(data);
            }
        } else if (data.equals("[FILE_END]")) {
            if (fileFragment != null && fileFragment.isAdded()) {
                fileFragment.onFileDownloadEnd();
            }
        }
    }

    // ==================== Callbacks de BluetoothService ====================

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

    // ==================== Getters públicos ====================

    public BluetoothService getBluetoothService() {
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

    // ==================== Menú ====================

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