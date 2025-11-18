package com.example.lora_gtr;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.example.lora_gtr.adapters.DeviceListAdapter;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Set;

public class ConnectionFragment extends Fragment {

    private static final String TAG = "ConnectionFragment";

    private ListView listViewDevices;
    private Button btnScan;
    private Button btnDisconnect;
    private MaterialCardView cardStatus;
    private TextView tvStatus;
    private TextView tvDeviceName;
    private TextView tvDeviceType;

    private DeviceListAdapter devicesAdapter;
    private ArrayList<BluetoothDevice> devicesList;

    private MainActivity mainActivity;
    private BluetoothAdapter bluetoothAdapter;

    private boolean isConnected = false;

    public ConnectionFragment() {
        // Constructor vacío requerido
        Log.d(TAG, "🏗️ Constructor llamado");
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "🚀 onCreate llamado");

        mainActivity = (MainActivity) getActivity();
        if (mainActivity != null) {
            bluetoothAdapter = mainActivity.getBluetoothAdapter();
            Log.d(TAG, "✅ MainActivity y BluetoothAdapter obtenidos");
        } else {
            Log.e(TAG, "❌ mainActivity es NULL!");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(TAG, "📱 onCreateView llamado");

        View view = inflater.inflate(R.layout.fragment_connection, container, false);

        initViews(view);
        setupListeners();
        loadPairedDevices();
        updateUI();

        Log.d(TAG, "✅ onCreateView completado");
        return view;
    }

    private void initViews(View view) {
        Log.d(TAG, "🔧 initViews llamado");

        listViewDevices = view.findViewById(R.id.list_devices);
        btnScan = view.findViewById(R.id.btn_scan);
        btnDisconnect = view.findViewById(R.id.btn_disconnect);
        cardStatus = view.findViewById(R.id.card_status);
        tvStatus = view.findViewById(R.id.tv_status);
        tvDeviceName = view.findViewById(R.id.tv_device_name);
        tvDeviceType = view.findViewById(R.id.tv_device_type);

        devicesList = new ArrayList<>();
        devicesAdapter = new DeviceListAdapter(requireContext(), devicesList);
        listViewDevices.setAdapter(devicesAdapter);

        Log.d(TAG, "✅ Vistas inicializadas");
    }

    private void setupListeners() {
        Log.d(TAG, "🎯 setupListeners llamado");

        listViewDevices.setOnItemClickListener((parent, view, position, id) -> {
            Log.d(TAG, "🖱️ Item de lista tocado, posición: " + position);
            Log.d(TAG, "   isConnected = " + isConnected);

            if (isConnected) {
                Log.w(TAG, "⚠️  Ya conectado, mostrando mensaje");
                Toast.makeText(requireContext(),
                        "Ya estás conectado. Desconecta primero.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            BluetoothDevice device = devicesList.get(position);
            Log.d(TAG, "✅ Dispositivo seleccionado: " + device.getName());
            connectToDevice(device);
        });

        btnScan.setOnClickListener(v -> {
            Log.d(TAG, "🔄 Botón Scan tocado");
            loadPairedDevices();
            Toast.makeText(requireContext(), "🔄 Lista actualizada", Toast.LENGTH_SHORT).show();
        });

        btnDisconnect.setOnClickListener(v -> {
            Log.d(TAG, "🔌 Botón Disconnect tocado");
            if (mainActivity != null) {
                mainActivity.disconnectDevice();
            } else {
                Log.e(TAG, "❌ mainActivity es NULL!");
            }
        });

        Log.d(TAG, "✅ Listeners configurados");
    }

    private void loadPairedDevices() {
        Log.d(TAG, "📋 loadPairedDevices llamado");

        devicesList.clear();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.e(TAG, "❌ BluetoothAdapter null o deshabilitado");
            Toast.makeText(requireContext(),
                    "Por favor habilita Bluetooth",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "❌ Permiso BLUETOOTH_CONNECT no otorgado");
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        Log.d(TAG, "📱 Dispositivos emparejados encontrados: " + pairedDevices.size());

        if (!pairedDevices.isEmpty()) {
            devicesList.addAll(pairedDevices);

            // Listar dispositivos encontrados
            for (BluetoothDevice device : devicesList) {
                Log.d(TAG, "   - " + device.getName() + " (" + device.getAddress() + ")");
            }
        }

        devicesAdapter.notifyDataSetChanged();

        if (devicesList.isEmpty()) {
            Toast.makeText(requireContext(),
                    "No hay dispositivos emparejados",
                    Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(),
                    "✅ " + devicesList.size() + " dispositivos",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void connectToDevice(BluetoothDevice device) {
        Log.d(TAG, "🔌 connectToDevice llamado");
        Log.d(TAG, "   Dispositivo: " + device.getName());
        Log.d(TAG, "   MAC: " + device.getAddress());

        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "❌ Permiso BLUETOOTH_CONNECT denegado");
            Toast.makeText(requireContext(),
                    "⚠️ Permiso de Bluetooth necesario",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String deviceName = device.getName();
        if (deviceName == null) {
            deviceName = "Desconocido";
            Log.w(TAG, "⚠️  Nombre de dispositivo es null");
        }

        Toast.makeText(requireContext(),
                "🔵 Conectando a " + deviceName + "...",
                Toast.LENGTH_SHORT).show();

        if (mainActivity != null) {
            Log.d(TAG, "✅ Llamando mainActivity.connectToDevice()");
            mainActivity.connectToDevice(device);
        } else {
            Log.e(TAG, "❌ mainActivity es NULL!");
            Toast.makeText(requireContext(),
                    "❌ Error: MainActivity no disponible",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUI() {
        Log.d(TAG, "🎨 updateUI llamado");
        Log.d(TAG, "   isConnected = " + isConnected);

        if (isConnected) {
            Log.d(TAG, "✅ Actualizando UI para estado CONECTADO");

            cardStatus.setCardBackgroundColor(getResources().getColor(android.R.color.holo_green_light));
            tvStatus.setText("🟢 CONECTADO");

            String deviceName = mainActivity != null ? mainActivity.getConnectedDeviceName() : "Desconocido";
            tvDeviceName.setText(deviceName);

            int mode = mainActivity != null ? mainActivity.getCurrentMode() : MainActivity.MODE_NONE;
            Log.d(TAG, "   Modo actual: " + mode);

            if (mode == MainActivity.MODE_TRANSMITTER) {
                tvDeviceType.setText("📡 Transmisor (TX)");
                tvDeviceType.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
            } else if (mode == MainActivity.MODE_RECEIVER) {
                tvDeviceType.setText("📥 Receptor (RX)");
                tvDeviceType.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
            }

            btnDisconnect.setVisibility(View.VISIBLE);
            btnScan.setEnabled(false);
            listViewDevices.setEnabled(false);
            listViewDevices.setAlpha(0.5f);

        } else {
            Log.d(TAG, "⚪ Actualizando UI para estado DESCONECTADO");

            cardStatus.setCardBackgroundColor(getResources().getColor(android.R.color.holo_red_light));
            tvStatus.setText("🔴 DESCONECTADO");
            tvDeviceName.setText("Sin dispositivo");
            tvDeviceType.setText("Selecciona un dispositivo");

            btnDisconnect.setVisibility(View.GONE);
            btnScan.setEnabled(true);
            listViewDevices.setEnabled(true);
            listViewDevices.setAlpha(1.0f);
        }

        Log.d(TAG, "✅ UI actualizada");
    }

    public void onConnectionStateChanged(boolean connected) {
        Log.d(TAG, "📲 onConnectionStateChanged llamado");
        Log.d(TAG, "   connected = " + connected);
        Log.d(TAG, "   isConnected ANTES = " + isConnected);

        isConnected = connected;

        Log.d(TAG, "   isConnected DESPUÉS = " + isConnected);

        if (getView() != null) {
            Log.d(TAG, "✅ getView() != null, actualizando UI");
            requireActivity().runOnUiThread(this::updateUI);
        } else {
            Log.e(TAG, "❌ getView() es NULL!");
        }
    }

    private void checkPermissions() {
        Log.d(TAG, "🔐 checkPermissions llamado");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean hasConnect = ActivityCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;

            boolean hasScan = ActivityCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;

            Log.d(TAG, "BLUETOOTH_CONNECT: " + hasConnect);
            Log.d(TAG, "BLUETOOTH_SCAN: " + hasScan);

            if (!hasConnect || !hasScan) {
                Toast.makeText(requireContext(),
                        "⚠️ Faltan permisos de Bluetooth",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "♻️ onResume llamado");

        if (mainActivity != null) {
            isConnected = mainActivity.isConnected();
            Log.d(TAG, "   isConnected sincronizado desde MainActivity: " + isConnected);
            updateUI();
        } else {
            Log.e(TAG, "❌ mainActivity es NULL en onResume");
        }

        loadPairedDevices();
        checkPermissions();

        Log.d(TAG, "✅ onResume completado");
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "⏸️ onPause llamado");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "💥 onDestroy llamado");
    }
}