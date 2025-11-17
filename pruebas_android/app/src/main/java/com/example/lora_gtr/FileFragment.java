package com.example.lora_gtr;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lora_gtr.adapters.FileListAdapter;
import com.example.lora_gtr.models.FileItem;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Fragment para gestión de archivos
 * Permite subir, descargar, enviar por LoRa y eliminar archivos
 */
public class FileFragment extends Fragment implements FileListAdapter.OnFileActionListener {

    // UI Components
    private RecyclerView recyclerViewFiles;
    private FloatingActionButton fabUpload;
    private TextView tvEmptyMessage;
    private TextView tvConnectionWarning;
    private ProgressBar progressBar;
    private TextView tvProgressText;

    // Data
    private FileListAdapter fileAdapter;
    private List<FileItem> fileList;
    private MainActivity mainActivity;

    // Estado
    private boolean isConnected = false;
    private int currentMode = MainActivity.MODE_NONE;
    private boolean isDownloading = false;
    private String currentDownloadingFile = "";
    private StringBuilder downloadBuffer;
    private long expectedFileSize = 0;

    // File picker launcher
    private ActivityResultLauncher<Intent> filePickerLauncher;

    public FileFragment() {
        // Constructor vacío requerido
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainActivity = (MainActivity) getActivity();
        fileList = new ArrayList<>();

        // Configurar file picker
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            uploadFileFromUri(uri);
                        }
                    }
                }
        );
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_file, container, false);

        // Inicializar vistas
        initViews(view);

        // Configurar RecyclerView
        setupRecyclerView();

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
        recyclerViewFiles = view.findViewById(R.id.recycler_files);
        fabUpload = view.findViewById(R.id.fab_upload);
        tvEmptyMessage = view.findViewById(R.id.tv_empty_message);
        tvConnectionWarning = view.findViewById(R.id.tv_connection_warning);
        progressBar = view.findViewById(R.id.progress_bar);
        tvProgressText = view.findViewById(R.id.tv_progress_text);
    }

    /**
     * Configurar RecyclerView
     */
    private void setupRecyclerView() {
        recyclerViewFiles.setLayoutManager(new LinearLayoutManager(requireContext()));
        fileAdapter = new FileListAdapter(fileList, this, currentMode);
        recyclerViewFiles.setAdapter(fileAdapter);
    }

    /**
     * Configurar listeners
     */
    private void setupListeners() {
        fabUpload.setOnClickListener(v -> openFilePicker());
    }

    /**
     * Abrir selector de archivos
     */
    private void openFilePicker() {
        if (!isConnected) {
            Toast.makeText(requireContext(),
                    "⚠️ Conecta un dispositivo primero",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            filePickerLauncher.launch(Intent.createChooser(intent, "Selecciona un archivo"));
        } catch (Exception e) {
            Toast.makeText(requireContext(),
                    "❌ Error abriendo selector de archivos",
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Subir archivo desde URI
     */
    private void uploadFileFromUri(Uri uri) {
        try {
            InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Toast.makeText(requireContext(), "❌ Error leyendo archivo", Toast.LENGTH_SHORT).show();
                return;
            }

            // Obtener nombre del archivo
            String filename = getFileNameFromUri(uri);

            // Crear archivo temporal
            File tempFile = new File(requireContext().getCacheDir(), filename);
            FileOutputStream outputStream = new FileOutputStream(tempFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }

            outputStream.close();
            inputStream.close();

            // Mostrar confirmación
            String message = "📤 Subir archivo:\n\n" +
                    "📄 " + filename + "\n" +
                    "📊 " + formatFileSize(totalBytes) + "\n\n" +
                    "¿Continuar?";

            new AlertDialog.Builder(requireContext())
                    .setTitle("Subir Archivo")
                    .setMessage(message)
                    .setPositiveButton("✅ Subir", (dialog, which) -> {
                        uploadFile(tempFile);
                    })
                    .setNegativeButton("❌ Cancelar", null)
                    .show();

        } catch (IOException e) {
            Toast.makeText(requireContext(),
                    "❌ Error procesando archivo: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Subir archivo al ESP32
     */
    private void uploadFile(File file) {
        if (!isConnected || mainActivity == null) {
            Toast.makeText(requireContext(), "⚠️ No conectado", Toast.LENGTH_SHORT).show();
            return;
        }

        showProgress(true, "Subiendo " + file.getName() + "...");

        // Subir en un hilo separado
        new Thread(() -> {
            try {
                mainActivity.getConfigManager().uploadFile(file);

                requireActivity().runOnUiThread(() -> {
                    showProgress(false, "");
                    Toast.makeText(requireContext(),
                            "✅ Archivo subido: " + file.getName(),
                            Toast.LENGTH_SHORT).show();

                    // Actualizar lista
                    refreshFileList();
                });

            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    showProgress(false, "");
                    Toast.makeText(requireContext(),
                            "❌ Error subiendo: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /**
     * Obtener nombre de archivo desde URI
     */
    private String getFileNameFromUri(Uri uri) {
        String filename = "archivo_" + System.currentTimeMillis();

        android.database.Cursor cursor = requireContext().getContentResolver()
                .query(uri, null, null, null, null);

        if (cursor != null) {
            int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                filename = cursor.getString(nameIndex);
            }
            cursor.close();
        }

        return filename;
    }

    /**
     * Formatear tamaño de archivo
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }

    /**
     * Actualizar lista de archivos
     */
    private void refreshFileList() {
        if (!isConnected || mainActivity == null) {
            return;
        }

        mainActivity.getConfigManager().listFiles();
    }

    /**
     * Mostrar/ocultar progreso
     */
    private void showProgress(boolean show, String message) {
        requireActivity().runOnUiThread(() -> {
            if (show) {
                progressBar.setVisibility(View.VISIBLE);
                tvProgressText.setVisibility(View.VISIBLE);
                tvProgressText.setText(message);
                fabUpload.setEnabled(false);
                recyclerViewFiles.setAlpha(0.5f);
            } else {
                progressBar.setVisibility(View.GONE);
                tvProgressText.setVisibility(View.GONE);
                fabUpload.setEnabled(true);
                recyclerViewFiles.setAlpha(1.0f);
            }
        });
    }

    /**
     * Actualizar UI según estado
     */
    private void updateUI() {
        if (mainActivity != null) {
            isConnected = mainActivity.isConnected();
            currentMode = mainActivity.getCurrentMode();
        }

        if (isConnected) {
            tvConnectionWarning.setVisibility(View.GONE);
            fabUpload.show();

            // Mostrar FAB solo para TX
            if (currentMode == MainActivity.MODE_TRANSMITTER) {
                fabUpload.setVisibility(View.VISIBLE);
            } else {
                fabUpload.setVisibility(View.GONE);
            }

        } else {
            tvConnectionWarning.setVisibility(View.VISIBLE);
            fabUpload.hide();
        }

        // Actualizar adapter con el modo actual
        fileAdapter.setCurrentMode(currentMode);

        // Mostrar mensaje si está vacío
        if (fileList.isEmpty()) {
            tvEmptyMessage.setVisibility(View.VISIBLE);
            recyclerViewFiles.setVisibility(View.GONE);
        } else {
            tvEmptyMessage.setVisibility(View.GONE);
            recyclerViewFiles.setVisibility(View.VISIBLE);
        }
    }

    // ==================== Callbacks de FileListAdapter ====================

    @Override
    public void onSendLoRa(FileItem file) {
        if (currentMode != MainActivity.MODE_TRANSMITTER) {
            Toast.makeText(requireContext(),
                    "⚠️ Solo disponible en modo TX",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("📡 Transmitir por LoRa")
                .setMessage("¿Enviar '" + file.getFilename() + "' por LoRa?\n\n" +
                        "Tamaño: " + file.getFormattedSize())
                .setPositiveButton("📡 Enviar", (dialog, which) -> {
                    if (mainActivity != null) {
                        mainActivity.getConfigManager().sendFileViaLoRa(file.getFilename());
                        Toast.makeText(requireContext(),
                                "📡 Transmitiendo " + file.getFilename() + "...",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    public void onDownload(FileItem file) {
        new AlertDialog.Builder(requireContext())
                .setTitle("📥 Descargar Archivo")
                .setMessage("¿Descargar '" + file.getFilename() + "'?\n\n" +
                        "Tamaño: " + file.getFormattedSize() + "\n" +
                        "Se guardará en: Descargas/")
                .setPositiveButton("📥 Descargar", (dialog, which) -> {
                    downloadFile(file);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    public void onDelete(FileItem file) {
        new AlertDialog.Builder(requireContext())
                .setTitle("🗑️ Eliminar Archivo")
                .setMessage("¿Eliminar '" + file.getFilename() + "'?\n\n" +
                        "Esta acción no se puede deshacer.")
                .setPositiveButton("🗑️ Eliminar", (dialog, which) -> {
                    if (mainActivity != null) {
                        mainActivity.getConfigManager().deleteFile(file.getFilename());
                        Toast.makeText(requireContext(),
                                "🗑️ Eliminando " + file.getFilename() + "...",
                                Toast.LENGTH_SHORT).show();

                        // Actualizar lista después de 500ms
                        new android.os.Handler().postDelayed(() -> refreshFileList(), 500);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /**
     * Descargar archivo del ESP32
     */
    private void downloadFile(FileItem file) {
        if (!isConnected || mainActivity == null) {
            Toast.makeText(requireContext(), "⚠️ No conectado", Toast.LENGTH_SHORT).show();
            return;
        }

        isDownloading = true;
        currentDownloadingFile = file.getFilename();
        expectedFileSize = file.getSize();
        downloadBuffer = new StringBuilder();

        showProgress(true, "Descargando " + file.getFilename() + "...");

        mainActivity.getConfigManager().downloadFile(file.getFilename());
    }

    // ==================== Procesamiento de datos recibidos ====================

    /**
     * Llamado cuando se reciben datos del ESP32
     */
    public void onDataReceived(String data) {
        requireActivity().runOnUiThread(() -> {
            // Lista de archivos
            if (data.equals("[FILES_START]")) {
                fileList.clear();
                return;
            }

            if (data.equals("[FILES_END]")) {
                fileAdapter.notifyDataSetChanged();
                updateUI();
                return;
            }

            // Parsear archivo: "nombre.txt,1234"
            if (data.contains(",") && !data.startsWith("[")) {
                String[] parts = data.split(",");
                if (parts.length == 2) {
                    try {
                        String filename = parts[0].trim();
                        long size = Long.parseLong(parts[1].trim());

                        FileItem fileItem = new FileItem(filename, size);
                        fileList.add(fileItem);
                    } catch (NumberFormatException e) {
                        android.util.Log.e("FileFragment", "Error parseando archivo: " + data);
                    }
                }
            }
        });
    }

    /**
     * Inicio de descarga de archivo
     */
    public void onFileDownloadStart(String data) {
        // Formato: [FILE_START:filename.txt:1234]
        try {
            String content = data.substring(12, data.length() - 1);
            String[] parts = content.split(":");

            if (parts.length >= 2) {
                currentDownloadingFile = parts[0];
                expectedFileSize = Long.parseLong(parts[1]);
                downloadBuffer = new StringBuilder();
                isDownloading = true;

                requireActivity().runOnUiThread(() -> {
                    showProgress(true, "Descargando " + currentDownloadingFile + "...");
                });
            }
        } catch (Exception e) {
            android.util.Log.e("FileFragment", "Error parseando FILE_START", e);
        }
    }

    /**
     * Fin de descarga de archivo
     */
    public void onFileDownloadEnd() {
        if (!isDownloading) return;

        isDownloading = false;

        // Guardar archivo en Descargas
        saveDownloadedFile(currentDownloadingFile, downloadBuffer.toString().getBytes());

        requireActivity().runOnUiThread(() -> {
            showProgress(false, "");
            Toast.makeText(requireContext(),
                    "✅ Descarga completa: " + currentDownloadingFile,
                    Toast.LENGTH_SHORT).show();
        });

        downloadBuffer = null;
        currentDownloadingFile = "";
    }

    /**
     * Guardar archivo descargado
     */
    private void saveDownloadedFile(String filename, byte[] data) {
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);

            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs();
            }

            File file = new File(downloadsDir, filename);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(data);
            fos.close();

            android.util.Log.d("FileFragment", "Archivo guardado: " + file.getAbsolutePath());

        } catch (IOException e) {
            android.util.Log.e("FileFragment", "Error guardando archivo", e);
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(),
                        "❌ Error guardando archivo: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            });
        }
    }

    /**
     * Llamado desde MainActivity cuando cambia el modo
     */
    public void onModeChanged(int mode) {
        currentMode = mode;
        isConnected = (mode != MainActivity.MODE_NONE);

        if (getView() != null) {
            requireActivity().runOnUiThread(() -> {
                updateUI();
                if (isConnected) {
                    refreshFileList();
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUI();

        // Actualizar lista si está conectado
        if (isConnected) {
            refreshFileList();
        }
    }
}