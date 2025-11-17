package com.example.lora_gtr;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.lora_gtr.models.LoRaConfig;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Fragment para configurar parámetros LoRa
 * Permite ajustar: Bandwidth, Spreading Factor, Coding Rate, ACK Interval
 */
public class SettingFragment extends Fragment {

    // UI Components
    private MaterialCardView cardCurrentConfig;
    private TextView tvCurrentConfig;
    private TextView tvConnectionWarning;

    private Spinner spinnerBandwidth;
    private Spinner spinnerSpreadingFactor;
    private Spinner spinnerCodingRate;
    private Spinner spinnerAckInterval;

    private Button btnApplyConfig;
    private Button btnResetConfig;

    // Variables
    private MainActivity mainActivity;
    private LoRaConfig currentConfig;
    private boolean isConnected = false;
    private int currentMode = MainActivity.MODE_NONE;

    // Valores de los spinners
    private String[] bandwidthValues = {"125", "250", "500"};
    private String[] spreadingFactorValues = {"7", "9", "12"};
    private String[] codingRateValues = {"4/5", "4/7", "4/8"};
    private String[] ackIntervalValues = {"3", "5", "7", "10", "15"};

    public SettingFragment() {
        // Constructor vacío requerido
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainActivity = (MainActivity) getActivity();
        currentConfig = new LoRaConfig(); // Config por defecto
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_setting, container, false);

        // Inicializar vistas
        initViews(view);

        // Configurar spinners
        setupSpinners();

        // Configurar listeners
        setupListeners();

        // Actualizar UI
        updateUI();

        return view;
    }

    /**
     * Inicializar vistas
     */
    private void initViews(View view) {
        cardCurrentConfig = view.findViewById(R.id.card_current_config);
        tvCurrentConfig = view.findViewById(R.id.tv_current_config);
        tvConnectionWarning = view.findViewById(R.id.tv_connection_warning);

        spinnerBandwidth = view.findViewById(R.id.spinner_bandwidth);
        spinnerSpreadingFactor = view.findViewById(R.id.spinner_sf);
        spinnerCodingRate = view.findViewById(R.id.spinner_cr);
        spinnerAckInterval = view.findViewById(R.id.spinner_ack);

        btnApplyConfig = view.findViewById(R.id.btn_apply_config);
        btnResetConfig = view.findViewById(R.id.btn_reset_config);
    }

    /**
     * Configurar spinners con adaptadores
     */
    private void setupSpinners() {
        // Bandwidth Spinner
        ArrayAdapter<String> bwAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                bandwidthValues
        );
        bwAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBandwidth.setAdapter(bwAdapter);

        // Spreading Factor Spinner
        ArrayAdapter<String> sfAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                spreadingFactorValues
        );
        sfAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSpreadingFactor.setAdapter(sfAdapter);

        // Coding Rate Spinner
        ArrayAdapter<String> crAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                codingRateValues
        );
        crAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCodingRate.setAdapter(crAdapter);

        // ACK Interval Spinner
        ArrayAdapter<String> ackAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                ackIntervalValues
        );
        ackAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAckInterval.setAdapter(ackAdapter);

        // Establecer valores por defecto
        setSpinnersToCurrentConfig();
    }

    /**
     * Configurar listeners
     */
    private void setupListeners() {
        // Botón Aplicar Configuración
        btnApplyConfig.setOnClickListener(v -> applyConfiguration());

        // Botón Reset (volver a config actual)
        btnResetConfig.setOnClickListener(v -> {
            setSpinnersToCurrentConfig();
            Toast.makeText(requireContext(), "↩️ Configuración restablecida", Toast.LENGTH_SHORT).show();
        });

        // Listeners de cambio en spinners (opcional, para validación)
        spinnerBandwidth.setOnItemSelectedListener(new SpinnerChangeListener());
        spinnerSpreadingFactor.setOnItemSelectedListener(new SpinnerChangeListener());
        spinnerCodingRate.setOnItemSelectedListener(new SpinnerChangeListener());
        spinnerAckInterval.setOnItemSelectedListener(new SpinnerChangeListener());
    }

    /**
     * Listener para detectar cambios en spinners
     */
    private class SpinnerChangeListener implements AdapterView.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            // Podrías agregar validación en tiempo real aquí
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            // No hacer nada
        }
    }

    /**
     * Aplicar configuración seleccionada
     */
    private void applyConfiguration() {
        if (!isConnected) {
            Toast.makeText(requireContext(),
                    "⚠️ Conecta un dispositivo primero",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Obtener valores de los spinners
        float bandwidth = Float.parseFloat(bandwidthValues[spinnerBandwidth.getSelectedItemPosition()]);
        int spreadingFactor = Integer.parseInt(spreadingFactorValues[spinnerSpreadingFactor.getSelectedItemPosition()]);

        // Coding Rate: convertir "4/X" a X
        String crString = codingRateValues[spinnerCodingRate.getSelectedItemPosition()];
        int codingRate = Integer.parseInt(crString.split("/")[1]);

        int ackInterval = Integer.parseInt(ackIntervalValues[spinnerAckInterval.getSelectedItemPosition()]);

        // Crear nueva configuración
        LoRaConfig newConfig = new LoRaConfig(bandwidth, spreadingFactor, codingRate, ackInterval);

        // Validar
        if (!newConfig.isValid()) {
            Toast.makeText(requireContext(),
                    "❌ Configuración inválida",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Enviar al dispositivo
        if (mainActivity != null && mainActivity.getConfigManager() != null) {
            mainActivity.getConfigManager().setConfig(newConfig);

            Toast.makeText(requireContext(),
                    "✅ Configuración enviada\n" + newConfig.toString(),
                    Toast.LENGTH_LONG).show();

            // Actualizar config actual (se confirmará cuando el ESP32 responda)
            currentConfig = newConfig;
            updateCurrentConfigDisplay();
        }
    }

    /**
     * Establecer spinners según configuración actual
     */
    private void setSpinnersToCurrentConfig() {
        // Bandwidth
        int bwIndex = getIndexInArray(bandwidthValues, String.valueOf((int)currentConfig.getBandwidth()));
        spinnerBandwidth.setSelection(bwIndex);

        // Spreading Factor
        int sfIndex = getIndexInArray(spreadingFactorValues, String.valueOf(currentConfig.getSpreadingFactor()));
        spinnerSpreadingFactor.setSelection(sfIndex);

        // Coding Rate
        String crValue = "4/" + currentConfig.getCodingRate();
        int crIndex = getIndexInArray(codingRateValues, crValue);
        spinnerCodingRate.setSelection(crIndex);

        // ACK Interval
        int ackIndex = getIndexInArray(ackIntervalValues, String.valueOf(currentConfig.getAckInterval()));
        spinnerAckInterval.setSelection(ackIndex);
    }

    /**
     * Obtener índice de un valor en un array
     */
    private int getIndexInArray(String[] array, String value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(value)) {
                return i;
            }
        }
        return 0; // Default
    }

    /**
     * Actualizar display de configuración actual
     */
    private void updateCurrentConfigDisplay() {
        String configText = "📻 Configuración Actual:\n\n" +
                "• Bandwidth: " + (int)currentConfig.getBandwidth() + " kHz\n" +
                "• Spreading Factor: " + currentConfig.getSpreadingFactor() + "\n" +
                "• Coding Rate: 4/" + currentConfig.getCodingRate() + "\n" +
                "• ACK Interval: " + currentConfig.getAckInterval() + " paquetes";

        tvCurrentConfig.setText(configText);
    }

    /**
     * Actualizar UI según estado de conexión
     */
    private void updateUI() {
        if (mainActivity != null) {
            isConnected = mainActivity.isConnected();
            currentMode = mainActivity.getCurrentMode();
        }

        if (isConnected) {
            // Habilitado
            tvConnectionWarning.setVisibility(View.GONE);

            spinnerBandwidth.setEnabled(true);
            spinnerSpreadingFactor.setEnabled(true);
            spinnerCodingRate.setEnabled(true);
            spinnerAckInterval.setEnabled(true);

            btnApplyConfig.setEnabled(true);
            btnResetConfig.setEnabled(true);

            spinnerBandwidth.setAlpha(1.0f);
            spinnerSpreadingFactor.setAlpha(1.0f);
            spinnerCodingRate.setAlpha(1.0f);
            spinnerAckInterval.setAlpha(1.0f);

            // Mostrar tipo de dispositivo
            String deviceType = "";
            if (currentMode == MainActivity.MODE_TRANSMITTER) {
                deviceType = " (TX - Transmisor)";
            } else if (currentMode == MainActivity.MODE_RECEIVER) {
                deviceType = " (RX - Receptor)";
            }

            updateCurrentConfigDisplay();

        } else {
            // Deshabilitado
            tvConnectionWarning.setVisibility(View.VISIBLE);

            spinnerBandwidth.setEnabled(false);
            spinnerSpreadingFactor.setEnabled(false);
            spinnerCodingRate.setEnabled(false);
            spinnerAckInterval.setEnabled(false);

            btnApplyConfig.setEnabled(false);
            btnResetConfig.setEnabled(false);

            spinnerBandwidth.setAlpha(0.5f);
            spinnerSpreadingFactor.setAlpha(0.5f);
            spinnerCodingRate.setAlpha(0.5f);
            spinnerAckInterval.setAlpha(0.5f);

            tvCurrentConfig.setText("⚠️ Conecta un dispositivo para ver y modificar la configuración");
        }
    }

    /**
     * Llamado desde MainActivity cuando cambia el modo
     */
    public void onModeChanged(int mode) {
        currentMode = mode;
        isConnected = (mode != MainActivity.MODE_NONE);

        if (getView() != null) {
            requireActivity().runOnUiThread(this::updateUI);
        }
    }

    /**
     * Llamado cuando se recibe configuración del ESP32
     */
    public void onConfigReceived(String jsonData) {
        try {
            JSONObject json = new JSONObject(jsonData);

            float bw = (float) json.getInt("bw");
            int sf = json.getInt("sf");
            int cr = json.getInt("cr");
            int ack = json.getInt("ack");

            currentConfig = new LoRaConfig(bw, sf, cr, ack);

            requireActivity().runOnUiThread(() -> {
                setSpinnersToCurrentConfig();
                updateCurrentConfigDisplay();
                Toast.makeText(requireContext(),
                        "✅ Configuración recibida",
                        Toast.LENGTH_SHORT).show();
            });

        } catch (JSONException e) {
            android.util.Log.e("SettingFragment", "Error parseando JSON de config", e);
            Toast.makeText(requireContext(),
                    "❌ Error al recibir configuración",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUI();

        // Solicitar configuración actual si está conectado
        if (isConnected && mainActivity != null && mainActivity.getConfigManager() != null) {
            mainActivity.getConfigManager().getConfig();
        }
    }
}