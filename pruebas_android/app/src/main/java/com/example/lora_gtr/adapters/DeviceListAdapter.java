package com.example.lora_gtr.adapters;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.lora_gtr.R;

import java.util.List;

/**
 * Adapter personalizado para mostrar dispositivos Bluetooth
 * Muestra nombre, dirección MAC e icono
 */
public class DeviceListAdapter extends ArrayAdapter<BluetoothDevice> {

    private Context context;
    private List<BluetoothDevice> devices;

    public DeviceListAdapter(@NonNull Context context, @NonNull List<BluetoothDevice> devices) {
        super(context, 0, devices);
        this.context = context;
        this.devices = devices;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_device, parent, false);
            holder = new ViewHolder();
            holder.ivDeviceIcon = convertView.findViewById(R.id.iv_device_icon);
            holder.tvDeviceName = convertView.findViewById(R.id.tv_device_name);
            holder.tvDeviceAddress = convertView.findViewById(R.id.tv_device_address);
            holder.tvDeviceType = convertView.findViewById(R.id.tv_device_type);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        BluetoothDevice device = devices.get(position);

        // Obtener nombre del dispositivo
        String deviceName = "Desconocido";
        String deviceAddress = "00:00:00:00:00:00";

        try {
            if (device.getName() != null && !device.getName().isEmpty()) {
                deviceName = device.getName();
            }
            deviceAddress = device.getAddress();
        } catch (SecurityException e) {
            // Permisos no otorgados
        }

        // Configurar nombre
        holder.tvDeviceName.setText(deviceName);
        holder.tvDeviceAddress.setText(deviceAddress);

        // Detectar tipo de dispositivo y configurar UI
        if (deviceName.toUpperCase().contains("LORA-TX") || deviceName.toUpperCase().contains("TX")) {
            // Dispositivo TX (Transmisor)
            holder.ivDeviceIcon.setImageResource(R.drawable.ic_lora_tx);
            holder.tvDeviceType.setText("📡 Transmisor");
            holder.tvDeviceType.setTextColor(context.getResources().getColor(android.R.color.holo_orange_dark));
            holder.tvDeviceType.setVisibility(View.VISIBLE);

            // Fondo destacado para LoRa
            convertView.setBackgroundResource(R.drawable.device_item_lora_background);

        } else if (deviceName.toUpperCase().contains("LORA-RX") || deviceName.toUpperCase().contains("RX")) {
            // Dispositivo RX (Receptor)
            holder.ivDeviceIcon.setImageResource(R.drawable.ic_lora_rx);
            holder.tvDeviceType.setText("📥 Receptor");
            holder.tvDeviceType.setTextColor(context.getResources().getColor(android.R.color.holo_blue_dark));
            holder.tvDeviceType.setVisibility(View.VISIBLE);

            // Fondo destacado para LoRa
            convertView.setBackgroundResource(R.drawable.device_item_lora_background);

        } else if (deviceName.toUpperCase().contains("LORA") || deviceName.toUpperCase().contains("HELTEC")) {
            // Dispositivo LoRa genérico
            holder.ivDeviceIcon.setImageResource(R.drawable.ic_lora_generic);
            holder.tvDeviceType.setText("📡 LoRa");
            holder.tvDeviceType.setTextColor(context.getResources().getColor(android.R.color.holo_green_dark));
            holder.tvDeviceType.setVisibility(View.VISIBLE);

            // Fondo destacado para LoRa
            convertView.setBackgroundResource(R.drawable.device_item_lora_background);

        } else {
            // Dispositivo Bluetooth genérico
            holder.ivDeviceIcon.setImageResource(R.drawable.ic_bluetooth_device);
            holder.tvDeviceType.setVisibility(View.GONE);

            // Fondo normal
            convertView.setBackgroundResource(R.drawable.device_item_background);
        }

        return convertView;
    }

    /**
     * ViewHolder para optimizar rendimiento
     */
    static class ViewHolder {
        ImageView ivDeviceIcon;
        TextView tvDeviceName;
        TextView tvDeviceAddress;
        TextView tvDeviceType;
    }
}
