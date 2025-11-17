package com.example.lora_gtr.models;
public class LoRaConfig {
    private float bandwidth;        // 125, 250, 500 kHz
    private int spreadingFactor;    // 7, 9, 12
    private int codingRate;         // 5, 7, 8 (representa 4/5, 4/7, 4/8)
    private int ackInterval;        // 3, 5, 7, 10, 15

    // Constructor vacío
    public LoRaConfig() {
        // Valores por defecto
        this.bandwidth = 125.0f;
        this.spreadingFactor = 9;
        this.codingRate = 7;
        this.ackInterval = 5;
    }

    // Constructor con parámetros
    public LoRaConfig(float bandwidth, int spreadingFactor, int codingRate, int ackInterval) {
        this.bandwidth = bandwidth;
        this.spreadingFactor = spreadingFactor;
        this.codingRate = codingRate;
        this.ackInterval = ackInterval;
    }

    // Getters
    public float getBandwidth() {
        return bandwidth;
    }

    public int getSpreadingFactor() {
        return spreadingFactor;
    }

    public int getCodingRate() {
        return codingRate;
    }

    public int getAckInterval() {
        return ackInterval;
    }

    // Setters
    public void setBandwidth(float bandwidth) {
        this.bandwidth = bandwidth;
    }

    public void setSpreadingFactor(int spreadingFactor) {
        this.spreadingFactor = spreadingFactor;
    }

    public void setCodingRate(int codingRate) {
        this.codingRate = codingRate;
    }

    public void setAckInterval(int ackInterval) {
        this.ackInterval = ackInterval;
    }

    // Convertir a JSON String para enviar por Bluetooth
    public String toJson() {
        return "{\"bw\":" + (int)bandwidth +
                ",\"sf\":" + spreadingFactor +
                ",\"cr\":" + codingRate +
                ",\"ack\":" + ackInterval + "}";
    }

    // Método para mostrar configuración como texto
    @Override
    public String toString() {
        return "BW: " + (int)bandwidth + " kHz | " +
                "SF: " + spreadingFactor + " | " +
                "CR: 4/" + codingRate + " | " +
                "ACK: " + ackInterval;
    }

    // Validar configuración
    public boolean isValid() {
        return (bandwidth == 125.0f || bandwidth == 250.0f || bandwidth == 500.0f) &&
                (spreadingFactor == 7 || spreadingFactor == 9 || spreadingFactor == 12) &&
                (codingRate == 5 || codingRate == 7 || codingRate == 8) &&
                (ackInterval >= 3 && ackInterval <= 15);
    }
}