package com.example.proyectofinalrestaurante.domain;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.domain.model.Categoria;
import com.example.proyectofinalrestaurante.domain.model.ImagenPlatillo;
import com.example.proyectofinalrestaurante.domain.model.Platillo;

import java.util.List;
import java.util.Locale;

/**
 * Espejo en el cliente de las reglas que impone el servidor sobre el menú (Plan Fase 2a).
 *
 * <p><b>Esto es para la interfaz, no es la seguridad.</b> Quien realmente impide borrar un
 * platillo o duplicar un nombre son los triggers y los índices únicos de Postgres, que
 * siguen valiendo aunque alguien modifique el APK. Acá se replican para no ofrecer una
 * acción que el servidor va a rechazar, y para avisar antes de gastar un viaje de red —
 * mismo patrón que {@link ReglasEmpleado} con {@code proteger_admins()}.</p>
 *
 * <p>Que la app sea <b>más</b> estricta que el servidor es seguro; al revés es el problema.</p>
 */
public final class ReglasMenu {

    /** Límite del bucket `platillos`, fijado en el servidor: 2 MB por archivo. */
    public static final int TAMANIO_MAXIMO_IMAGEN_BYTES = 2 * 1024 * 1024;

    private ReglasMenu() {
    }

    // ------------------------------------------------------------------ categorías

    /**
     * Una categoría solo se puede borrar si no tiene <b>ningún</b> platillo colgando,
     * ni siquiera desactivado: el trigger {@code trg_categoria_no_borrar_con_platillos}
     * cuenta las filas, no los activos.
     */
    public static boolean puedeBorrarse(@Nullable Categoria categoria) {
        return categoria != null && categoria.getCantidadPlatillos() == 0;
    }

    // ------------------------------------------------------------------ unicidad

    /**
     * Normaliza un nombre igual que los índices únicos del servidor, que están definidos
     * sobre {@code lower(btrim(...))}.
     *
     * <p>Es deliberado que "Baleada", "baleada" y {@code "Baleada "} sean el mismo nombre:
     * para un mesero lo son, y tres filas así vuelven inútil el buscador.</p>
     */
    public static String normalizarNombre(@Nullable String nombre) {
        return nombre == null ? "" : nombre.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * ¿Ya existe otro platillo con ese nombre? {@code idPlatilloActual} se excluye para
     * que editar un platillo sin cambiarle el nombre no se detecte como duplicado.
     */
    public static boolean existeOtroPlatilloLlamado(@Nullable List<Platillo> platillos,
                                                    @Nullable String nombre,
                                                    int idPlatilloActual) {
        if (platillos == null) {
            return false;
        }
        String buscado = normalizarNombre(nombre);
        for (Platillo platillo : platillos) {
            if (platillo.getIdPlatillo() != idPlatilloActual
                    && normalizarNombre(platillo.getNombre()).equals(buscado)) {
                return true;
            }
        }
        return false;
    }

    /** Equivalente para categorías, contra el índice {@code uq_categoria_descripcion}. */
    public static boolean existeOtraCategoriaLlamada(@Nullable List<Categoria> categorias,
                                                     @Nullable String descripcion,
                                                     int idCategoriaActual) {
        if (categorias == null) {
            return false;
        }
        String buscada = normalizarNombre(descripcion);
        for (Categoria categoria : categorias) {
            if (categoria.getIdCategoria() != idCategoriaActual
                    && normalizarNombre(categoria.getDescripcion()).equals(buscada)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ imágenes

    /**
     * ¿La imagen entra en el límite del bucket?
     *
     * <p>Si no se verifica acá, Storage responde un 400 críptico después de haber subido
     * el archivo entero por una conexión lenta.</p>
     */
    public static boolean cabeEnElBucket(@Nullable ImagenPlatillo imagen) {
        return imagen != null && imagen.getTamanioEnBytes() > 0
                && imagen.getTamanioEnBytes() <= TAMANIO_MAXIMO_IMAGEN_BYTES;
    }

    /** El bucket solo acepta estos tres tipos; un GIF falla con 400 aunque la app lo deje pasar. */
    public static boolean tipoDeImagenPermitido(@Nullable String mimeType) {
        return ImagenPlatillo.MIME_JPEG.equals(mimeType)
                || ImagenPlatillo.MIME_PNG.equals(mimeType)
                || ImagenPlatillo.MIME_WEBP.equals(mimeType);
    }

    /** Atajo: la imagen se puede subir si pesa lo permitido y es de un tipo aceptado. */
    public static boolean puedeSubirse(@Nullable ImagenPlatillo imagen) {
        return cabeEnElBucket(imagen) && tipoDeImagenPermitido(imagen.getMimeType());
    }
}
