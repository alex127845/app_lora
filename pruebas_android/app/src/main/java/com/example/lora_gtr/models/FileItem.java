package com.example.lora_gtr.models;
public class FileItem {
    private String filename;
    private long size;           // Tamaño en bytes
    private boolean isSelected;  // Para selección múltiple (opcional)

    // Constructor vacío
    public FileItem() {
        this.filename = "";
        this.size = 0;
        this.isSelected = false;
    }

    // Constructor con parámetros
    public FileItem(String filename, long size) {
        this.filename = filename;
        this.size = size;
        this.isSelected = false;
    }

    // Getters
    public String getFilename() {
        return filename;
    }

    public long getSize() {
        return size;
    }

    public boolean isSelected() {
        return isSelected;
    }

    // Setters
    public void setFilename(String filename) {
        this.filename = filename;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    // Obtener nombre sin ruta (si viene con /)
    public String getDisplayName() {
        if (filename.startsWith("/")) {
            return filename.substring(1);
        }
        return filename;
    }

    // Formatear tamaño en formato legible
    public String getFormattedSize() {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        }
    }

    // Obtener extensión del archivo
    public String getExtension() {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    // Obtener icono según tipo de archivo (código de recurso)
    public String getFileType() {
        String ext = getExtension();
        switch (ext) {
            case "txt":
                return "📄";
            case "pdf":
                return "📕";
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
                return "🖼️";
            case "mp3":
            case "wav":
                return "🎵";
            case "mp4":
            case "avi":
                return "🎬";
            case "zip":
            case "rar":
                return "📦";
            case "doc":
            case "docx":
                return "📘";
            case "xls":
            case "xlsx":
                return "📊";
            default:
                return "📄";
        }
    }

    @Override
    public String toString() {
        return filename + " (" + getFormattedSize() + ")";
    }

    // Para comparación
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        FileItem fileItem = (FileItem) obj;
        return filename.equals(fileItem.filename);
    }

    @Override
    public int hashCode() {
        return filename.hashCode();
    }
}