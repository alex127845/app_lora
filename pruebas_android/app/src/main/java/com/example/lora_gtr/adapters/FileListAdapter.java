package com.example.lora_gtr.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lora_gtr.MainActivity;
import com.example.lora_gtr.R;
import com.example.lora_gtr.models.FileItem;

import java.util.List;

public class FileListAdapter extends RecyclerView.Adapter<FileListAdapter.FileViewHolder> {

    private List<FileItem> fileList;
    private OnFileActionListener listener;
    private int currentMode;

    public interface OnFileActionListener {
        void onSendLoRa(FileItem file);
        void onDownload(FileItem file);
        void onDelete(FileItem file);
    }

    public FileListAdapter(List<FileItem> fileList, OnFileActionListener listener, int currentMode) {
        this.fileList = fileList;
        this.listener = listener;
        this.currentMode = currentMode;
    }

    public void setCurrentMode(int mode) {
        this.currentMode = mode;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        FileItem file = fileList.get(position);

        holder.tvFilename.setText(file.getDisplayName());
        holder.tvFilesize.setText(file.getFormattedSize());
        holder.tvFileIcon.setText(file.getFileType());

        // Configurar botones según modo
        if (currentMode == MainActivity.MODE_TRANSMITTER) {
            // Modo TX: Mostrar botón "Enviar LoRa"
            holder.btnSendLora.setVisibility(View.VISIBLE);
            holder.btnDownload.setVisibility(View.GONE);
        } else if (currentMode == MainActivity.MODE_RECEIVER) {
            // Modo RX: Mostrar botón "Descargar"
            holder.btnSendLora.setVisibility(View.GONE);
            holder.btnDownload.setVisibility(View.VISIBLE);
        } else {
            // Sin modo: Ocultar ambos
            holder.btnSendLora.setVisibility(View.GONE);
            holder.btnDownload.setVisibility(View.GONE);
        }

        // Listeners
        holder.btnSendLora.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSendLoRa(file);
            }
        });

        holder.btnDownload.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDownload(file);
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDelete(file);
            }
        });
    }

    @Override
    public int getItemCount() {
        return fileList.size();
    }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        TextView tvFileIcon;
        TextView tvFilename;
        TextView tvFilesize;
        Button btnSendLora;
        Button btnDownload;
        Button btnDelete;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFileIcon = itemView.findViewById(R.id.tv_file_icon);
            tvFilename = itemView.findViewById(R.id.tv_filename);
            tvFilesize = itemView.findViewById(R.id.tv_filesize);
            btnSendLora = itemView.findViewById(R.id.btn_send_lora);
            btnDownload = itemView.findViewById(R.id.btn_download);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}