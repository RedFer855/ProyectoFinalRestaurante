package com.example.proyectofinalrestaurante.domain.model;

/**
 * Imagen de un platillo lista para subir: los bytes ya comprimidos y su tipo MIME.
 *
 * <p>Transporta {@code byte[]} y no un {@code Bitmap} ni un {@code Uri} porque
 * {@code domain} no puede importar nada de {@code android.*}. Medir, rotar según el EXIF,
 * redimensionar y comprimir es trabajo de {@code ui}; acá llega el resultado.</p>
 *
 * <p>El arreglo se copia al entrar y al salir: sin eso, quien construyó el objeto podría
 * seguir modificando los bytes después, y esta clase dejaría de ser inmutable.</p>
 */
public final class ImagenPlatillo {

    public static final String MIME_JPEG = "image/jpeg";
    public static final String MIME_PNG = "image/png";
    public static final String MIME_WEBP = "image/webp";

    private final byte[] bytes;
    private final String mimeType;

    public ImagenPlatillo(byte[] bytes, String mimeType) {
        this.bytes = bytes == null ? new byte[0] : bytes.clone();
        this.mimeType = mimeType;
    }

    public byte[] getBytes() {
        return bytes.clone();
    }

    public String getMimeType() {
        return mimeType;
    }

    public int getTamanioEnBytes() {
        return bytes.length;
    }

    /**
     * Extensión que le corresponde al archivo dentro del bucket.
     *
     * <p>Se deriva del MIME y no del nombre del archivo original: el usuario elige la foto
     * con el selector del sistema, que puede entregar un {@code content://} sin nombre útil.</p>
     */
    public String extensionDeArchivo() {
        if (MIME_PNG.equals(mimeType)) {
            return "png";
        }
        if (MIME_WEBP.equals(mimeType)) {
            return "webp";
        }
        return "jpg";
    }
}
