package com.example.lora_gtr;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothService {
    private static final String TAG = "BluetoothService";

    // UUID para SPP (Serial Port Profile)
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket socket;
    private ConnectedThread connectedThread;
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
    private String connectedDeviceName = "";  // ← AGREGADO

    public BluetoothService(Context context, Handler handler, ConnectionCallback callback) {
        this.handler = handler;
        this.callback = callback;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public synchronized void connect(BluetoothDevice device) {
        Log.d(TAG, "Conectando a: " + device.getName());

        connectedDeviceName = device.getName();  // ← AGREGADO

        if (state == STATE_CONNECTING && connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error cerrando socket", e);
            }
            socket = null;
        }

        new ConnectThread(device).start();
        setState(STATE_CONNECTING);
    }

    public synchronized void connected(BluetoothSocket socket) {
        Log.d(TAG, "Conexión establecida");

        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        connectedThread = new ConnectedThread(socket);
        connectedThread.start();

        setState(STATE_CONNECTED);

        // ← AGREGADO: Enviar nombre del dispositivo
        Message msg = handler.obtainMessage(MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString("device_name", connectedDeviceName);
        msg.setData(bundle);
        handler.sendMessage(msg);

        if (callback != null) callback.onConnected();
    }

    public synchronized void disconnect() {
        Log.d(TAG, "Desconectando...");

        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error cerrando socket", e);
            }
            socket = null;
        }

        setState(STATE_NONE);
        connectedDeviceName = "";  // ← AGREGADO
        if (callback != null) callback.onDisconnected();
    }

    public void write(byte[] data) {
        ConnectedThread r;
        synchronized (this) {
            if (state != STATE_CONNECTED) {
                Log.w(TAG, "No conectado, no se puede enviar");
                return;
            }
            r = connectedThread;
        }
        r.write(data);
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

    public String getConnectedDeviceName() {  // ← AGREGADO
        return connectedDeviceName;
    }

    // Thread para conectar
    private class ConnectThread extends Thread {
        private final BluetoothSocket tmpSocket;
        private final BluetoothDevice device;

        public ConnectThread(BluetoothDevice device) {
            this.device = device;
            BluetoothSocket tmp = null;

            try {
                tmp = device.createRfcommSocketToServiceRecord(SPP_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Error creando socket", e);
            } catch (SecurityException e) {  // ← AGREGADO para Android 12+
                Log.e(TAG, "Permiso denegado", e);
            }
            tmpSocket = tmp;
        }

        public void run() {
            try {
                bluetoothAdapter.cancelDiscovery();
            } catch (SecurityException e) {
                Log.e(TAG, "Permiso denegado al cancelar discovery", e);
            }

            try {
                tmpSocket.connect();
            } catch (IOException e) {
                Log.e(TAG, "Error conectando", e);
                try {
                    tmpSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "Error cerrando socket", e2);
                }

                if (callback != null) {
                    callback.onError("No se pudo conectar: " + e.getMessage());
                }
                setState(STATE_NONE);
                return;
            }

            socket = tmpSocket;
            connected(socket);
        }

        public void cancel() {
            try {
                tmpSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error cerrando socket", e);
            }
        }
    }

    // Thread para comunicación
    private class ConnectedThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public ConnectedThread(BluetoothSocket socket) {
            this.socket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error obteniendo streams", e);
            }

            inputStream = tmpIn;
            outputStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (state == STATE_CONNECTED) {
                try {
                    bytes = inputStream.read(buffer);

                    byte[] data = new byte[bytes];
                    System.arraycopy(buffer, 0, data, 0, bytes);

                    handler.obtainMessage(MESSAGE_READ, bytes, -1, data).sendToTarget();

                    if (callback != null) {
                        callback.onDataReceived(data);
                    }

                } catch (IOException e) {
                    Log.e(TAG, "Desconectado", e);
                    disconnect();
                    break;
                }
            }
        }

        public void write(byte[] buffer) {
            try {
                outputStream.write(buffer);
                outputStream.flush();  // ← AGREGADO para asegurar envío
                handler.obtainMessage(MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
                Log.d(TAG, "Enviado: " + new String(buffer));
            } catch (IOException e) {
                Log.e(TAG, "Error escribiendo", e);
                if (callback != null) {
                    callback.onError("Error enviando datos");
                }
            }
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error cerrando socket", e);
            }
        }
    }
}