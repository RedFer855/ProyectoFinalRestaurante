package com.example.proyectofinalrestaurante.ui.menu;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import com.example.proyectofinalrestaurante.domain.model.ImagenPlatillo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Convierte la foto que eligió el usuario en los bytes que se suben al bucket.
 *
 * <p>Vive en {@code ui} porque toca {@code android.graphics} y {@code ContentResolver};
 * {@code domain} solo recibe el resultado ya cocinado en un {@link ImagenPlatillo}.</p>
 *
 * <p>Comprimir en el dispositivo antes de subir no es una optimización opcional: el
 * objetivo real de este proyecto son teléfonos de 2 GB de RAM sobre 3G hondureño, y el
 * bucket rechaza cualquier archivo de más de 2 MB.</p>
 */
public final class CompresorDeImagen {

    /** Lado largo al que se redimensiona. Suficiente para una foto de menú a pantalla completa. */
    private static final int LADO_LARGO_OBJETIVO = 1024;
    private static final int CALIDAD_JPEG = 80;

    private CompresorDeImagen() {
    }

    /**
     * Lee, rota, redimensiona y comprime la foto apuntada por {@code uri}.
     *
     * <p>Devuelve {@code null} si el {@code Uri} no se puede leer o no es una imagen
     * decodificable, para que quien llama muestre un mensaje claro en vez de propagar una
     * excepción hasta la UI.</p>
     */
    @Nullable
    public static ImagenPlatillo comprimir(ContentResolver resolver, Uri uri) {
        try {
            int inSampleSize = calcularInSampleSize(resolver, uri);
            if (inSampleSize == 0) {
                return null;
            }

            Bitmap bitmap = decodificar(resolver, uri, inSampleSize);
            if (bitmap == null) {
                return null;
            }

            bitmap = rotarSegunExif(resolver, uri, bitmap);

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            // JPEG y no WEBP: Bitmap.CompressFormat.WEBP está deprecado desde API 30 y su
            // reemplazo (WEBP_LOSSY) no existe en API 24, que es el minSdk del proyecto.
            bitmap.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG, salida);
            bitmap.recycle();

            return new ImagenPlatillo(salida.toByteArray(), ImagenPlatillo.MIME_JPEG);
        } catch (IOException | SecurityException | OutOfMemoryError ex) {
            // El Uri puede haber dejado de ser accesible, o la foto ser demasiado grande
            // incluso para medirla. En los dos casos quien llama muestra el aviso.
            return null;
        }
    }

    /**
     * Mide la foto sin cargarla en memoria y calcula en cuánto hay que reducirla.
     *
     * <p>{@code inJustDecodeBounds} lee solo la cabecera. Sin este paso, decodificar un
     * JPEG de 12 MP entero en un teléfono de 2 GB es un {@code OutOfMemoryError}.</p>
     */
    private static int calcularInSampleSize(ContentResolver resolver, Uri uri) throws IOException {
        BitmapFactory.Options opciones = new BitmapFactory.Options();
        opciones.inJustDecodeBounds = true;

        try (InputStream entrada = resolver.openInputStream(uri)) {
            if (entrada == null) {
                return 0;
            }
            BitmapFactory.decodeStream(entrada, null, opciones);
        }

        int ladoLargo = Math.max(opciones.outWidth, opciones.outHeight);
        if (ladoLargo <= 0) {
            return 0;
        }

        int inSampleSize = 1;
        while (ladoLargo / (inSampleSize * 2) >= LADO_LARGO_OBJETIVO) {
            inSampleSize *= 2;
        }
        return inSampleSize;
    }

    @Nullable
    private static Bitmap decodificar(ContentResolver resolver, Uri uri, int inSampleSize)
            throws IOException {
        BitmapFactory.Options opciones = new BitmapFactory.Options();
        opciones.inSampleSize = inSampleSize;

        try (InputStream entrada = resolver.openInputStream(uri)) {
            return entrada == null ? null : BitmapFactory.decodeStream(entrada, null, opciones);
        }
    }

    /**
     * Aplica la orientación que declara el EXIF. Sin esto, las fotos sacadas con la cámara
     * aparecen acostadas: el sensor guarda los píxeles siempre igual y deja la rotación
     * anotada en los metadatos.
     */
    private static Bitmap rotarSegunExif(ContentResolver resolver, Uri uri, Bitmap bitmap)
            throws IOException {
        int orientacion;
        try (InputStream entrada = resolver.openInputStream(uri)) {
            if (entrada == null) {
                return bitmap;
            }
            orientacion = new ExifInterface(entrada).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        }

        Matrix matriz = new Matrix();
        switch (orientacion) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matriz.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matriz.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matriz.postRotate(270);
                break;
            default:
                return bitmap;
        }

        Bitmap rotado = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matriz, true);
        if (rotado != bitmap) {
            bitmap.recycle();
        }
        return rotado;
    }
}
