package com.example.lora_gtr;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.example.lora_gtr.adapters.DeviceListAdapter;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Set;

public class ConnectionFragment extends Fragment {

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
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainActivity = (MainActivity) getActivity();
        bluetoothAdapter = mainActivity.getBluetoothAdapter();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_connection, container, false);

        initViews(view);
        setupListeners();
        loadPairedDevices();
        updateUI();

        return view;
    }

    private void initViews(View view) {
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
    }

    private void setupListeners() {
        listViewDevices.setOnItemClickListener((parent, view, position, id) -> {
            if (isConnected) {
                Toast.makeText(requireContext(),
                        "Ya estás conectado. Desconecta primero.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            BluetoothDevice device = devicesList.get(position);
            connectToDevice(device);
        });

        btnScan.setOnClickListener(v -> {
            loadPairedDevices();
            Toast.makeText(requireContext(), "🔄 Lista actualizada", Toast.LENGTH_SHORT).show();
        });

        btnDisconnect.setOnClickListener(v -> {
            if (mainActivity != null) {
                mainActivity.disconnectDevice();
            }
        });
    }

    private void loadPairedDevices() {
        devicesList.clear();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(requireContext(),
                    "Por favor habilita Bluetooth",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        if (!pairedDevices.isEmpty()) {
            devicesList.addAll(pairedDevices);
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
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String deviceName = device.getName();
        if (deviceName == null) deviceName = "Desconocido";

        Toast.makeText(requireContext(),
                "🔵 Conectando a " + deviceName + "...",
                Toast.LENGTH_SHORT).show();

        if (mainActivity != null) {
            mainActivity.connectToDevice(device);
        }
    }

    private void updateUI() {
        if (isConnected) {
            cardStatus.setCardBackgroundColor(getResources().getColor(android.R.color.holo_green_light));
            tvStatus.setText("🟢 CONECTADO");

            String deviceName = mainActivity != null ? mainActivity.getConnectedDeviceName() : "Desconocido";
            tvDeviceName.setText(deviceName);

            int mode = mainActivity != null ? mainActivity.getCurrentMode() : MainActivity.MODE_NONE;
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
            cardStatus.setCardBackgroundColor(getResources().getColor(android.R.color.holo_red_light));
            tvStatus.setText("🔴 DESCONECTADO");
            tvDeviceName.setText("Sin dispositivo");
            tvDeviceType.setText("Selecciona un dispositivo");

            btnDisconnect.setVisibility(View.GONE);
            btnScan.setEnabled(true);
            listViewDevices.setEnabled(true);
            listViewDevices.setAlpha(1.0f);
        }
    }

    public void onConnectionStateChanged(boolean connected) {
        isConnected = connected;
        if (getView() != null) {
            requireActivity().runOnUiThread(this::updateUI);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mainActivity != null) {
            isConnected = mainActivity.isConnected();
            updateUI();
        }
        loadPairedDevices();
    }
}