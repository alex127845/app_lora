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
import android.util.Log;
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

    private static final String TAG = "MainActivity";

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

        Log.d(TAG, "🚀 onCreate iniciado");

        // Configurar ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("LoRa Gateway Controller");
            getSupportActionBar().setElevation(4);
        }

        // Inicializar Bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Log.e(TAG, "❌ BluetoothAdapter es null");
            Toast.makeText(this, "❌ Bluetooth no disponible en este dispositivo", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Log.d(TAG, "✅ BluetoothAdapter inicializado");

        // Solicitar permisos
        checkPermissions();

        // Inicializar servicio BLE
        Log.d(TAG, "🔧 Inicializando BLEService...");
        bluetoothService = new BLEService(this, handler, this);
        configManager = new LoRaConfigManager(bluetoothService);
        Log.d(TAG, "✅ BLEService y ConfigManager inicializados");

        // Setup Bottom Navigation
        setupBottomNavigation();

        // Cargar fragment inicial solo si es primera creación
        if (savedInstanceState == null) {
            Log.d(TAG, "📱 Cargando ConnectionFragment inicial");
            loadFragment(TAG_CONNECTION);
        }

        Log.d(TAG, "✅ onCreate completado");
    }

    /**
     * Configurar Bottom Navigation
     */
    private void setupBottomNavigation() {
        bottomNavigationView = findViewById(R.id.bottonNavigationView);

        // Configurar listener
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            Log.d(TAG, "🔘 Bottom nav seleccionado: " + itemId);
            Log.d(TAG, "   isConnected = " + isConnected);

            if (itemId == R.id.conn) {
                loadFragment(TAG_CONNECTION);
                return true;
            } else if (itemId == R.id.file) {
                if (!isConnected) {
                    Log.w(TAG, "⚠️  Intento de acceder a File sin conexión");
                    Toast.makeText(this, "⚠️ Conecta un dispositivo primero", Toast.LENGTH_SHORT).show();
                    return false;
                }
                Log.d(TAG, "✅ Navegando a FileFragment");
                loadFragment(TAG_FILE);
                return true;
            } else if (itemId == R.id.setting) {
                if (!isConnected) {
                    Log.w(TAG, "⚠️  Intento de acceder a Setting sin conexión");
                    Toast.makeText(this, "⚠️ Conecta un dispositivo primero", Toast.LENGTH_SHORT).show();
                    return false;
                }
                Log.d(TAG, "✅ Navegando a SettingFragment");
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
        Log.d(TAG, "📄 loadFragment(" + tag + ")");

        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment fragment = fragmentManager.findFragmentByTag(tag);

        // Si el fragment no existe, crearlo
        if (fragment == null) {
            Log.d(TAG, "   Fragment no existe, creándolo...");
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
                    Log.e(TAG, "❌ Tag desconocido: " + tag);
                    return;
            }
        } else {
            Log.d(TAG, "   Fragment ya existe");
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
            Log.d(TAG, "   Mostrando fragment existente");
            transaction.show(fragment);
        } else {
            Log.d(TAG, "   Agregando nuevo fragment");
            transaction.add(R.id.fragment_container, fragment, tag);
        }

        transaction.commit();
        Log.d(TAG, "✅ Transaction committed");
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
                Log.d(TAG, "⚠️  Solicitando permisos de Bluetooth");
                ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
            } else {
                Log.d(TAG, "✅ Todos los permisos otorgados");
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
                Log.e(TAG, "❌ Permisos denegados");
                Toast.makeText(this,
                        "⚠️ Se necesitan permisos para usar Bluetooth",
                        Toast.LENGTH_LONG).show();
            } else {
                Log.d(TAG, "✅ Permisos otorgados");
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
        Log.d(TAG, "🔌 connectToDevice llamado");
        Log.d(TAG, "   Dispositivo: " + device.getName());

        if (bluetoothService != null) {
            bluetoothService.connect(device);
        } else {
            Log.e(TAG, "❌ bluetoothService es null!");
        }
    }

    public void disconnectDevice() {
        Log.d(TAG, "🔌 disconnectDevice llamado");

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
            Log.d(TAG, "📩 Handler recibió mensaje: " + msg.what);

            switch (msg.what) {
                case BLEService.MESSAGE_STATE_CHANGE:
                    Log.d(TAG, "🔄 MESSAGE_STATE_CHANGE recibido, state = " + msg.arg1);
                    handleStateChange(msg.arg1);
                    break;

                case BLEService.MESSAGE_READ:
                    Log.d(TAG, "📖 MESSAGE_READ recibido");
                    byte[] readBuf = (byte[]) msg.obj;
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    processReceivedData(readMessage);
                    break;

                case BLEService.MESSAGE_DEVICE_NAME:
                    Log.d(TAG, "📱 MESSAGE_DEVICE_NAME recibido");
                    connectedDeviceName = msg.getData().getString("device_name");
                    Log.d(TAG, "   Nombre del dispositivo: " + connectedDeviceName);
                    Toast.makeText(MainActivity.this,
                            "✅ Conectado a " + connectedDeviceName,
                            Toast.LENGTH_SHORT).show();
                    detectDeviceType(connectedDeviceName);
                    break;

                case BLEService.MESSAGE_TOAST:
                    Log.d(TAG, "💬 MESSAGE_TOAST recibido");
                    Toast.makeText(MainActivity.this,
                            msg.getData().getString("toast"),
                            Toast.LENGTH_SHORT).show();
                    break;

                default:
                    Log.w(TAG, "⚠️  Mensaje desconocido: " + msg.what);
            }
            return true;
        }
    });

    private void handleStateChange(int state) {
        Log.d(TAG, "📊 handleStateChange llamado con state = " + state);

        switch (state) {
            case BLEService.STATE_CONNECTED:
                Log.d(TAG, "🟢 STATE_CONNECTED");
                isConnected = true;
                Log.d(TAG, "✅ isConnected ahora es TRUE");

                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle("🟢 Conectado a " + connectedDeviceName);
                }
                break;

            case BLEService.STATE_CONNECTING:
                Log.d(TAG, "🟡 STATE_CONNECTING");
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle("🟡 Conectando...");
                }
                break;

            case BLEService.STATE_NONE:
                Log.d(TAG, "🔴 STATE_NONE");
                isConnected = false;
                currentMode = MODE_NONE;
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle("🔴 No conectado");
                }
                break;

            default:
                Log.w(TAG, "⚠️  Estado desconocido: " + state);
        }

        Log.d(TAG, "Estado final: isConnected = " + isConnected);
    }

    private void detectDeviceType(String deviceName) {
        Log.d(TAG, "🔍 detectDeviceType llamado");
        Log.d(TAG, "   deviceName = " + deviceName);

        if (deviceName == null) {
            Log.w(TAG, "⚠️  deviceName es null");
            return;
        }

        if (deviceName.toUpperCase().contains("TX")) {
            Log.d(TAG, "✅ Dispositivo TX detectado");
            currentMode = MODE_TRANSMITTER;
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("📡 TX: " + deviceName);
            }
            Toast.makeText(this, "📡 Modo TRANSMISOR activado", Toast.LENGTH_SHORT).show();

        } else if (deviceName.toUpperCase().contains("RX")) {
            Log.d(TAG, "✅ Dispositivo RX detectado");
            currentMode = MODE_RECEIVER;
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle("📥 RX: " + deviceName);
            }
            Toast.makeText(this, "📥 Modo RECEPTOR activado", Toast.LENGTH_SHORT).show();
        } else {
            Log.w(TAG, "⚠️  Tipo de dispositivo desconocido");
            currentMode = MODE_NONE;
            Toast.makeText(this, "⚠️ Dispositivo desconocido", Toast.LENGTH_SHORT).show();
        }

        Log.d(TAG, "currentMode final = " + currentMode);
        Log.d(TAG, "🔔 Llamando notifyFragmentsModeChanged()");
        notifyFragmentsModeChanged();
    }

    private void notifyFragmentsModeChanged() {
        Log.d(TAG, "🔔 notifyFragmentsModeChanged llamado");
        Log.d(TAG, "   currentMode = " + currentMode);
        Log.d(TAG, "   isConnected = " + isConnected);

        FragmentManager fm = getSupportFragmentManager();

        FileFragment fileFragment = (FileFragment) fm.findFragmentByTag(TAG_FILE);
        if (fileFragment != null && fileFragment.isAdded()) {
            Log.d(TAG, "✅ Notificando a FileFragment");
            fileFragment.onModeChanged(currentMode);
        } else {
            Log.d(TAG, "⏭️  FileFragment no disponible (normal si no se ha navegado)");
        }

        SettingFragment settingFragment = (SettingFragment) fm.findFragmentByTag(TAG_SETTING);
        if (settingFragment != null && settingFragment.isAdded()) {
            Log.d(TAG, "✅ Notificando a SettingFragment");
            settingFragment.onModeChanged(currentMode);
        } else {
            Log.d(TAG, "⏭️  SettingFragment no disponible (normal si no se ha navegado)");
        }

        ConnectionFragment connectionFragment = (ConnectionFragment) fm.findFragmentByTag(TAG_CONNECTION);
        if (connectionFragment != null && connectionFragment.isAdded()) {
            Log.d(TAG, "✅ Notificando a ConnectionFragment");
            connectionFragment.onConnectionStateChanged(true);
        } else {
            Log.e(TAG, "❌ ConnectionFragment no disponible! (ESTO ES UN PROBLEMA)");
        }

        Log.d(TAG, "✅ notifyFragmentsModeChanged completado");
    }

    private void notifyFragmentsDisconnected() {
        Log.d(TAG, "🔔 notifyFragmentsDisconnected llamado");

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
        Log.d(TAG, "📥 processReceivedData: " + data);

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
        Log.d(TAG, "📞 onConnected() callback");
        runOnUiThread(() -> {
            Toast.makeText(this, "✅ Conexión establecida", Toast.LENGTH_SHORT).show();

            new Handler().postDelayed(() -> {
                if (configManager != null) {
                    Log.d(TAG, "📡 Solicitando config y archivos");
                    configManager.getConfig();
                    configManager.listFiles();
                }
            }, 500);
        });
    }

    @Override
    public void onDisconnected() {
        Log.d(TAG, "📞 onDisconnected() callback");
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
        Log.e(TAG, "📞 onError() callback: " + error);
        runOnUiThread(() -> {
            Toast.makeText(this, "❌ Error: " + error, Toast.LENGTH_LONG).show();
        });
    }

    public BLEService getBluetoothService() {
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
        Log.d(TAG, "💥 onDestroy llamado");
        if (bluetoothService != null) {
            bluetoothService.disconnect();
        }
    }
}