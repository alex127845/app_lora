package com.example.lora_gtr;

import android.util.Log;

import com.example.lora_gtr.models.LoRaConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Gestor de configuración y comandos LoRa
 * Envía comandos al ESP32 por Bluetooth
 */
public class LoRaConfigManager {

    private static final String TAG = "LoRaConfigManager";

    private BluetoothService bluetoothService;

    // ==================== COMANDOS BLUETOOTH ====================
    // Comandos que el ESP32 entiende
    private static final String CMD_GET_CONFIG = "GET_CONFIG\n";
    private static final String CMD_SET_CONFIG = "SET_CONFIG:";
    private static final String CMD_GET_FILES = "GET_FILES\n";
    private static final String CMD_UPLOAD_FILE = "UPLOAD_FILE:";
    private static final String CMD_DOWNLOAD_FILE = "DOWNLOAD_FILE:";
    private static final String CMD_DELETE_FILE = "DELETE_FILE:";
    private static final String CMD_SEND_LORA = "SEND_LORA:";
    private static final String CMD_GET_STATUS = "GET_STATUS\n";

    // ==================== CONFIGURACIÓN ====================
    private static final int CHUNK_SIZE = 512; // Tamaño de chunks para enviar archivos

    /**
     * Constructor
     */
    public LoRaConfigManager(BluetoothService bluetoothService) {
        this.bluetoothService = bluetoothService;
    }

    // ==================== CONFIGURACIÓN LoRa ====================

    /**
     * Obtener configuración actual del dispositivo
     */
    public void getConfig() {
        if (!isConnected()) {
            Log.w(TAG, "No conectado, no se puede obtener configuración");
            return;
        }

        Log.d(TAG, "Solicitando configuración actual");
        bluetoothService.write(CMD_GET_CONFIG);
    }

    /**
     * Configurar parámetros LoRa
     * @param bandwidth Ancho de banda (125, 250, 500 kHz)
     * @param spreadingFactor Factor de dispersión (7, 9, 12)
     * @param codingRate Tasa de codificación (5, 7, 8 = 4/5, 4/7, 4/8)
     * @param ackInterval Intervalo de ACK (3, 5, 7, 10, 15)
     */
    public void setConfig(float bandwidth, int spreadingFactor, int codingRate, int ackInterval) {
        if (!isConnected()) {
            Log.w(TAG, "No conectado, no se puede configurar");
            return;
        }

        try {
            JSONObject config = new JSONObject();
            config.put("bw", (int) bandwidth);
            config.put("sf", spreadingFactor);
            config.put("cr", codingRate);
            config.put("ack", ackInterval);

            String command = CMD_SET_CONFIG + config.toString() + "\n";

            Log.d(TAG, "Enviando configuración: " + command);
            bluetoothService.write(command);

        } catch (JSONException e) {
            Log.e(TAG, "Error creando JSON de configuración", e);
        }
    }

    /**
     * Configurar usando objeto LoRaConfig
     */
    public void setConfig(LoRaConfig config) {
        if (!isConnected()) {
            Log.w(TAG, "No conectado, no se puede configurar");
            return;
        }

        if (!config.isValid()) {
            Log.e(TAG, "Configuración inválida");
            return;
        }

        String command = CMD_SET_CONFIG + config.toJson() + "\n";

        Log.d(TAG, "Enviando configuración: " + command);
        bluetoothService.write(command);
    }

    // ==================== GESTIÓN DE ARCHIVOS ====================

    /**
     * Listar archivos en el dispositivo
     */
    public void listFiles() {
        if (!isConnected()) {
            Log.w(TAG, "No conectado, no se puede listar archivos");
            return;
        }

        Log.d(TAG, "Solicitando lista de archivos");
        bluetoothService.write(CMD_GET_FILES);
    }

    /**
     * Solicitar descarga de un archivo desde el ESP32
     * @param filename Nombre del archivo (con o sin /)
     */
    public void downloadFile(String filename) {
        if (!isConnected()) {
            Log.w(TAG, "No conectado, no se puede descargar");
            return;
        }

        // Asegurar que el nombre empiece con /
        if (!filename.startsWith("/")) {
            filename = "/" + filename;
        }

        String command = CMD_DOWNLOAD_FILE + filename + "\n";

        Log.d(TAG, "Solicitando descarga: " + command);
        bluetoothService.write(command);
    }

    /**
     * Eliminar archivo del ESP32
     * @param filename Nombre del archivo
     */
    public void deleteFile(String filename) {
        if (!isConnected()) {
            Log.w(TAG, "No conectado, no se puede eliminar");
            return;
        }

        // Asegurar que el nombre empiece con /
        if (!filename.startsWith("/")) {
            filename = "/" + filename;
        }

        String command = CMD_DELETE_FILE + filename + "\n";

        Log.d(TAG, "Eliminando archivo: " + command);
        bluetoothService.write(command);
    }

    /**
     * Enviar archivo por LoRa (solo para TX)
     * @param filename Nombre del archivo a transmitir
     */
    public void sendFileViaLoRa(String filename) {
        if (!isConnected()) {
            Log.w(TAG, "No conectado, no se puede enviar por LoRa");
            return;
        }

        // Asegurar que el nombre empiece con /
        if (!filename.startsWith("/")) {
            filename = "/" + filename;
        }

        String command = CMD_SEND_LORA + filename + "\n";

        Log.d(TAG, "Enviando por LoRa: " + command);
        bluetoothService.write(command);
    }

    // ==================== SUBIDA DE ARCHIVOS ====================

    /**
     * Subir archivo al ESP32 por Bluetooth
     * @param localFile Archivo local a subir
     */
    public void uploadFile(File localFile) {
        if (!isConnected()) {
            Log.w(TAG, "No conectado, no se puede subir archivo");
            return;
        }

        if (!localFile.exists() || !localFile.isFile()) {
            Log.e(TAG, "Archivo no existe: " + localFile.getPath());
            return;
        }

        try {
            // Leer archivo completo
            byte[] fileData = readFileBytes(localFile);

            if (fileData == null) {
                Log.e(TAG, "Error leyendo archivo");
                return;
            }

            // Enviar metadata primero
            sendUploadMetadata(localFile.getName(), fileData.length);

            // Esperar un poco antes de enviar datos
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // Enviar datos en chunks
            sendFileInChunks(fileData);

        } catch (Exception e) {
            Log.e(TAG, "Error subiendo archivo", e);
        }
    }

    /**
     * Enviar metadata de archivo a subir
     */
    private void sendUploadMetadata(String filename, long fileSize) {
        try {
            JSONObject metadata = new JSONObject();
            metadata.put("filename", filename);
            metadata.put("size", fileSize);

            String command = CMD_UPLOAD_FILE + metadata.toString() + "\n";

            Log.d(TAG, "Enviando metadata: " + command);
            bluetoothService.write(command);

        } catch (JSONException e) {
            Log.e(TAG, "Error creando metadata JSON", e);
        }
    }

    /**
     * Enviar archivo en chunks por Bluetooth
     */
    private void sendFileInChunks(byte[] fileData) {
        int totalChunks = (int) Math.ceil((double) fileData.length / CHUNK_SIZE);

        Log.d(TAG, "Enviando archivo en " + totalChunks + " chunks");

        for (int i = 0; i < totalChunks; i++) {
            int start = i * CHUNK_SIZE;
            int end = Math.min(start + CHUNK_SIZE, fileData.length);
            int chunkSize = end - start;

            // Crear chunk con header
            byte[] chunk = new byte[chunkSize + 5];
            chunk[0] = 'C'; // Marker de chunk
            chunk[1] = (byte) (i >> 8);        // Número de chunk (high byte)
            chunk[2] = (byte) (i & 0xFF);      // Número de chunk (low byte)
            chunk[3] = (byte) (totalChunks >> 8);    // Total chunks (high byte)
            chunk[4] = (byte) (totalChunks & 0xFF);  // Total chunks (low byte)

            // Copiar datos
            System.arraycopy(fileData, start, chunk, 5, chunkSize);

            // Enviar chunk
            bluetoothService.write(chunk);

            Log.d(TAG, "Chunk " + (i + 1) + "/" + totalChunks + " enviado (" + chunkSize + " bytes)");

            // Pequeña pausa entre chunks
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        Log.d(TAG, "Archivo enviado completamente");
    }

    /**
     * Leer archivo como array de bytes
     */
    private byte[] readFileBytes(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();
            return data;

        } catch (IOException e) {
            Log.e(TAG, "Error leyendo archivo", e);
            return null;
        }
    }

    // ==================== ESTADO ====================

    /**
     * Obtener estado del dispositivo
     */
    public void getStatus() {
        if (!isConnected()) {
            Log.w(TAG, "No conectado, no se puede obtener estado");
            return;
        }

        Log.d(TAG, "Solicitando estado");
        bluetoothService.write(CMD_GET_STATUS);
    }

    /**
     * Verificar si está conectado
     */
    private boolean isConnected() {
        return bluetoothService != null &&
                bluetoothService.getState() == BluetoothService.STATE_CONNECTED;
    }

    // ==================== COMANDOS PERSONALIZADOS ====================

    /**
     * Enviar comando personalizado
     * @param command Comando a enviar
     */
    public void sendCustomCommand(String command) {
        if (!isConnected()) {
            Log.w(TAG, "No conectado, no se puede enviar comando");
            return;
        }

        if (!command.endsWith("\n")) {
            command += "\n";
        }

        Log.d(TAG, "Enviando comando personalizado: " + command);
        bluetoothService.write(command);
    }

    /**
     * Enviar datos raw (sin \n al final)
     */
    public void sendRawData(byte[] data) {
        if (!isConnected()) {
            Log.w(TAG, "No conectado, no se puede enviar datos");
            return;
        }

        Log.d(TAG, "Enviando " + data.length + " bytes raw");
        bluetoothService.write(data);
    }
}