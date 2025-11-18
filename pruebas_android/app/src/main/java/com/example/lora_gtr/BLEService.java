package com.example.lora_gtr;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.UUID;

public class BLEService {
    private static final String TAG = "BLEService";

    // UUIDs del servicio BLE (deben coincidir con el ESP32)
    private static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    private static final UUID CHARACTERISTIC_UUID_RX = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");
    private static final UUID CHARACTERISTIC_UUID_TX = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a9");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic rxCharacteristic;
    private BluetoothGattCharacteristic txCharacteristic;
    private Handler handler;

    // Estados de conexión
    public static final int STATE_NONE = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;
    private int state = STATE_NONE;

    // Mensajes para el Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    public interface ConnectionCallback {
        void onConnected();
        void onDisconnected();
        void onDataReceived(byte[] data);
        void onError(String error);
    }

    private ConnectionCallback callback;
    private String connectedDeviceName = "";
    private StringBuilder dataBuffer = new StringBuilder();

    public BLEService(Context context, Handler handler, ConnectionCallback callback) {
        this.context = context;
        this.handler = handler;
        this.callback = callback;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public synchronized void connect(BluetoothDevice device) {
        Log.d(TAG, "Conectando a: " + device.getName());
        connectedDeviceName = device.getName();

        setState(STATE_CONNECTING);

        // Conectar usando GATT
        bluetoothGatt = device.connectGatt(context, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Conectado a GATT server");
                Log.d(TAG, "Intentando descubrir servicios...");
                bluetoothGatt.discoverServices();

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Desconectado de GATT server");
                setState(STATE_NONE);
                if (callback != null) {
                    callback.onDisconnected();
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Servicios descubiertos");

                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    rxCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID_RX);
                    txCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID_TX);

                    if (txCharacteristic != null) {
                        // Habilitar notificaciones
                        gatt.setCharacteristicNotification(txCharacteristic, true);

                        BluetoothGattDescriptor descriptor = txCharacteristic.getDescriptor(CCCD_UUID);
                        if (descriptor != null) {
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            gatt.writeDescriptor(descriptor);
                        }

                        setState(STATE_CONNECTED);

                        // Enviar mensaje de dispositivo conectado
                        Message msg = handler.obtainMessage(MESSAGE_DEVICE_NAME);
                        Bundle bundle = new Bundle();
                        bundle.putString("device_name", connectedDeviceName);
                        msg.setData(bundle);
                        handler.sendMessage(msg);

                        if (callback != null) {
                            callback.onConnected();
                        }
                    } else {
                        Log.e(TAG, "Características no encontradas");
                        disconnect();
                    }
                } else {
                    Log.e(TAG, "Servicio no encontrado");
                    disconnect();
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (CHARACTERISTIC_UUID_TX.equals(characteristic.getUuid())) {
                byte[] data = characteristic.getValue();

                if (data != null && data.length > 0) {
                    // Acumular datos en buffer
                    String received = new String(data);
                    dataBuffer.append(received);

                    // Si termina en \n, procesar
                    if (received.endsWith("\n")) {
                        String completeMessage = dataBuffer.toString();
                        dataBuffer.setLength(0);

                        handler.obtainMessage(MESSAGE_READ, completeMessage.length(), -1,
                                completeMessage.getBytes()).sendToTarget();

                        if (callback != null) {
                            callback.onDataReceived(completeMessage.getBytes());
                        }
                    }
                }
            }
        }
    };

    public synchronized void disconnect() {
        Log.d(TAG, "Desconectando...");

        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }

        rxCharacteristic = null;
        txCharacteristic = null;
        connectedDeviceName = "";
        dataBuffer.setLength(0);

        setState(STATE_NONE);
    }

    public void write(byte[] data) {
        if (state != STATE_CONNECTED || rxCharacteristic == null) {
            Log.w(TAG, "No conectado, no se puede enviar");
            return;
        }

        rxCharacteristic.setValue(data);
        boolean success = bluetoothGatt.writeCharacteristic(rxCharacteristic);

        if (success) {
            handler.obtainMessage(MESSAGE_WRITE, -1, -1, data).sendToTarget();
            Log.d(TAG, "Enviado: " + new String(data));
        } else {
            Log.e(TAG, "Error escribiendo característica");
        }
    }

    public void write(String message) {
        write(message.getBytes());
    }

    private synchronized void setState(int state) {
        Log.d(TAG, "setState() " + this.state + " -> " + state);
        this.state = state;
        handler.obtainMessage(MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    public synchronized int getState() {
        return state;
    }

    public String getConnectedDeviceName() {
        return connectedDeviceName;
    }
}