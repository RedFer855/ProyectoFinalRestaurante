package com.example.proyectofinalrestaurante.data.repository;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.data.outbox.ClasificadorDeError;
import com.example.proyectofinalrestaurante.data.remote.SupabaseMenuApi;
import com.example.proyectofinalrestaurante.data.remote.SupabaseStorageApi;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarCategoriaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarPlatilloDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CategoriaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearCategoriaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearPlatilloDto;
import com.example.proyectofinalrestaurante.data.remote.dto.PlatilloDto;
import com.example.proyectofinalrestaurante.domain.ReglasMenu;
import com.example.proyectofinalrestaurante.domain.model.ImagenPlatillo;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Response;

/**
 * Ejecutor de <b>todas</b> las llamadas de red del módulo Menú (Plan Fase 2b, E5/E6).
 *
 * <p>En 2a esto era {@code SupabaseMenuRepository}, que hablaba con la red para leer y
 * escribir. Desde 2b la UI <b>nunca</b> habla con la red: lee de Room y escribe local
 * (optimista). Este ejecutor queda como la cara remota que usa el sincronizador para drenar
 * el outbox y bajar el delta.</p>
 *
 * <p>Conserva toda la compensación de 2a: si la foto se sube pero el paso siguiente falla,
 * el archivo se borra. El token de sesión entra por un {@link Supplier} inyectado, como
 * antes, para poder testear sin estado global.</p>
 *
 * <p>Los fallos viajan en {@link ResultadoRed} con su código HTTP: el outbox necesita saber
 * si un error es permanente (409 → descartar) o transitorio (500 → reintentar).</p>
 */
public final class MenuRemoto {

    static final String SIN_CONEXION = "Sin conexión al servidor. Intentá de nuevo.";
    static final String SIN_PERMISO_RED = "La app no tiene permiso de red. Contactá al desarrollador.";
    static final String SIN_SESION = "Tu sesión venció. Volvé a iniciar sesión.";
    private static final String NO_SE_PUDO_SUBIR = "No se pudo subir la foto. Intentá de nuevo.";
    private static final String IMAGEN_INVALIDA =
            "La foto no se puede subir: revisá que pese menos de 2 MB y sea JPG, PNG o WEBP.";

    /** Catálogo `estado_general` del servidor. La app solo usa estos dos valores. */
    private static final int ID_ESTADO_ACTIVO = 1;
    private static final int ID_ESTADO_INACTIVO = 2;

    private static final MediaType TIPO_JSON = MediaType.parse("application/json");
    /** Gson omite los nulos, así que el único modo de limpiar la columna es un JSON literal. */
    private static final String CUERPO_QUITAR_IMAGEN = "{\"ruta_imagen\":null}";

    /** Tamaño de página del sync delta; repetir hasta recibir menos que esto (§4.3). */
    public static final int LIMITE_DELTA = 50;

    private final SupabaseMenuApi api;
    private final SupabaseStorageApi storageApi;
    private final Supplier<String> proveedorToken;
    private final Gson gson = new Gson();

    public MenuRemoto(SupabaseMenuApi api, SupabaseStorageApi storageApi,
                      Supplier<String> proveedorToken) {
        this.api = api;
        this.storageApi = storageApi;
        this.proveedorToken = proveedorToken;
    }

    @Nullable
    private String bearer() {
        String token = proveedorToken.get();
        return token == null ? null : "Bearer " + token;
    }

    // ------------------------------------------------------------------ pull

    public ResultadoRed<List<PlatilloDto>> listarPlatillos() {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            Response<List<PlatilloDto>> respuesta = api.listarPlatillos(bearer).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null) {
                return ResultadoRed.fallo(respuesta.code(), "No se pudo cargar el menú.");
            }
            return ResultadoRed.exito(respuesta.body());
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    public ResultadoRed<List<CategoriaDto>> listarCategorias() {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            Response<List<CategoriaDto>> respuesta = api.listarCategorias(bearer).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null) {
                return ResultadoRed.fallo(respuesta.code(), "No se pudieron cargar las categorías.");
            }
            return ResultadoRed.exito(respuesta.body());
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    /**
     * Sync delta paginado (Plan Fase 2b, §4.3). Una marca {@code null} (primera bajada) se
     * traduce en "sin filtro": Retrofit omite el query {@code actualizado_en} cuando el
     * valor es {@code null}, así que la primera sincronización baja la tabla entera.
     */
    public ResultadoRed<List<PlatilloDto>> listarPlatillosDesde(@Nullable String marcaAgua) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            String filtro = marcaAgua == null ? null : "gt." + marcaAgua;
            Response<List<PlatilloDto>> respuesta = api.listarPlatillosDesde(
                    bearer, "*", filtro, "actualizado_en.asc", LIMITE_DELTA).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null) {
                return ResultadoRed.fallo(respuesta.code(), "No se pudo cargar el menú.");
            }
            return ResultadoRed.exito(respuesta.body());
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    public ResultadoRed<List<CategoriaDto>> listarCategoriasDesde(@Nullable String marcaAgua) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            String filtro = marcaAgua == null ? null : "gt." + marcaAgua;
            Response<List<CategoriaDto>> respuesta = api.listarCategoriasDesde(
                    bearer, "*", filtro, "actualizado_en.asc", LIMITE_DELTA).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null) {
                return ResultadoRed.fallo(respuesta.code(), "No se pudieron cargar las categorías.");
            }
            return ResultadoRed.exito(respuesta.body());
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    // ------------------------------------------------------------------ platillos

    /**
     * Crea el platillo y, si viene imagen, la sube primero. Si el insert falla después de
     * subirla, borra el archivo recién subido: sin esa compensación, cada error dejaría
     * basura permanente en el bucket.
     *
     * <p>La ruta del archivo la genera acá (no el sincronizador): el {@code POST} tiene que
     * conocer la ruta que acaba de dejar la subida, y eso solo se sabe después de subir.</p>
     */
    public ResultadoRed<PlatilloDto> crearPlatillo(String nombre, String descripcion,
                                                   double precio, int idCategoriaServidor,
                                                   @Nullable ImagenPlatillo imagen) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }

        String ruta = null;
        if (imagen != null) {
            if (!ReglasMenu.puedeSubirse(imagen)) {
                return ResultadoRed.fallo(400, IMAGEN_INVALIDA);
            }
            ruta = rutaNueva(imagen);
            ResultadoRed<Void> subida = subirImagen(bearer, ruta, imagen);
            if (!subida.isExitoso()) {
                return ResultadoRed.fallo(subida.getCodigoHttp(), subida.getMensaje());
            }
        }

        try {
            CrearPlatilloDto cuerpo = new CrearPlatilloDto(
                    nombre, descripcion == null ? "" : descripcion, precio,
                    idCategoriaServidor, ruta);
            Response<List<PlatilloDto>> respuesta = api.crearPlatillo(bearer, cuerpo).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null || respuesta.body().isEmpty()) {
                borrarArchivo(bearer, ruta);
                return ResultadoRed.fallo(respuesta.code(),
                        mensajeDeError(respuesta, "No se pudo crear el platillo."));
            }
            return ResultadoRed.exito(respuesta.body().get(0));
        } catch (IOException ex) {
            // A propósito no se compensa acá: sin respuesta no se sabe si el insert entró.
            // Borrar la foto de un platillo que sí se creó es peor (imagen rota, visible)
            // que dejar un archivo huérfano (invisible, cubierto por P-023).
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    /**
     * Actualiza la fila {@code idServidor} con el estado completo del platillo. Si viene
     * {@code imagenNueva}, se sube a una ruta nueva y la {@code rutaVieja} se borra al
     * final; si {@code rutaVieja} es {@code null}, no se borra nada.
     *
     * @return el valor es la ruta de la foto nueva (o {@code null} si no cambió), para que
     *         el sincronizador la guarde en la fila local.
     */
    public ResultadoRed<String> actualizarPlatillo(int idServidor, String nombre, String descripcion,
                                                   double precio, int idCategoriaServidor,
                                                   boolean activo,
                                                   @Nullable ImagenPlatillo imagenNueva,
                                                   @Nullable String rutaVieja) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }

        String rutaNueva = null;
        if (imagenNueva != null) {
            if (!ReglasMenu.puedeSubirse(imagenNueva)) {
                return ResultadoRed.fallo(400, IMAGEN_INVALIDA);
            }
            // Ruta nueva en cada reemplazo, nunca sobrescribir: si la URL no cambia,
            // Glide sigue sirviendo la foto vieja desde su caché.
            rutaNueva = rutaNueva(imagenNueva);
            ResultadoRed<Void> subida = subirImagen(bearer, rutaNueva, imagenNueva);
            if (!subida.isExitoso()) {
                return ResultadoRed.fallo(subida.getCodigoHttp(), subida.getMensaje());
            }
        }

        try {
            ActualizarPlatilloDto cuerpo = ActualizarPlatilloDto.conTodo(
                    nombre, descripcion, precio, idCategoriaServidor, rutaNueva,
                    activo ? ID_ESTADO_ACTIVO : ID_ESTADO_INACTIVO);

            Response<Void> respuesta = api.actualizarPlatillo(
                    bearer, "eq." + idServidor, cuerpo).execute();
            if (!respuesta.isSuccessful()) {
                borrarArchivo(bearer, rutaNueva);
                return ResultadoRed.fallo(respuesta.code(),
                        mensajeDeError(respuesta, "No se pudieron guardar los cambios."));
            }

            // La vieja se borra al final y solo si la fila ya apunta a la nueva: si algo se
            // cae en el medio, sobra un archivo (barato) en vez de faltar la foto de un
            // platillo que sí existe (visible para el usuario).
            if (rutaNueva != null && rutaVieja != null && !rutaVieja.trim().isEmpty()) {
                borrarArchivo(bearer, rutaVieja);
            }
            return ResultadoRed.exito(rutaNueva);
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    /** Activa o desactiva el platillo sin tocar ningún otro campo (usa `soloEstado`). */
    public ResultadoRed<Void> cambiarEstadoPlatillo(int idServidor, boolean activo) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            ActualizarPlatilloDto cuerpo = ActualizarPlatilloDto.soloEstado(
                    activo ? ID_ESTADO_ACTIVO : ID_ESTADO_INACTIVO);
            Response<Void> respuesta = api.actualizarPlatillo(
                    bearer, "eq." + idServidor, cuerpo).execute();
            if (!respuesta.isSuccessful()) {
                return ResultadoRed.fallo(respuesta.code(),
                        mensajeDeError(respuesta, "No se pudo cambiar el estado."));
            }
            return ResultadoRed.exito(null);
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    /** Deja el platillo sin foto: limpia la ruta en la fila y borra el archivo del bucket. */
    public ResultadoRed<Void> quitarImagen(int idServidor, @Nullable String rutaParaBorrar) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            RequestBody cuerpo = RequestBody.create(TIPO_JSON, CUERPO_QUITAR_IMAGEN);
            Response<Void> respuesta = api.actualizarPlatilloConCuerpoCrudo(
                    bearer, "eq." + idServidor, cuerpo).execute();
            if (!respuesta.isSuccessful()) {
                return ResultadoRed.fallo(respuesta.code(),
                        mensajeDeError(respuesta, "No se pudo quitar la foto."));
            }
            // Primero la fila, después el archivo: si el borrado falla queda huérfano
            // (P-023), pero el platillo ya se ve correctamente sin foto.
            borrarArchivo(bearer, rutaParaBorrar);
            return ResultadoRed.exito(null);
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    // ------------------------------------------------------------------ categorías

    public ResultadoRed<CategoriaDto> crearCategoria(String descripcion) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            Response<List<CategoriaDto>> respuesta =
                    api.crearCategoria(bearer, new CrearCategoriaDto(descripcion)).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null || respuesta.body().isEmpty()) {
                return ResultadoRed.fallo(respuesta.code(),
                        mensajeDeError(respuesta, "No se pudo crear la categoría."));
            }
            return ResultadoRed.exito(respuesta.body().get(0));
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    public ResultadoRed<Void> renombrarCategoria(int idServidor, String descripcion) {
        return actualizarCategoria(idServidor, ActualizarCategoriaDto.soloDescripcion(descripcion),
                "No se pudo renombrar la categoría.");
    }

    public ResultadoRed<Void> cambiarEstadoCategoria(int idServidor, boolean activo) {
        return actualizarCategoria(idServidor, ActualizarCategoriaDto.soloEstado(
                        activo ? ID_ESTADO_ACTIVO : ID_ESTADO_INACTIVO),
                "No se pudo cambiar el estado de la categoría.");
    }

    /** Solo funciona si la categoría no tiene platillos; si los tiene, el servidor la rechaza. */
    public ResultadoRed<Void> borrarCategoria(int idServidor) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            Response<Void> respuesta = api.borrarCategoria(bearer, "eq." + idServidor).execute();
            if (!respuesta.isSuccessful()) {
                return ResultadoRed.fallo(respuesta.code(),
                        mensajeDeError(respuesta, "No se pudo borrar la categoría."));
            }
            return ResultadoRed.exito(null);
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    private ResultadoRed<Void> actualizarCategoria(int idServidor, ActualizarCategoriaDto cuerpo,
                                                   String mensajeSiFalla) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            Response<Void> respuesta = api.actualizarCategoria(
                    bearer, "eq." + idServidor, cuerpo).execute();
            if (!respuesta.isSuccessful()) {
                return ResultadoRed.fallo(respuesta.code(),
                        mensajeDeError(respuesta, mensajeSiFalla));
            }
            return ResultadoRed.exito(null);
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    // ------------------------------------------------------------------ Storage

    /** Nombre único dentro del bucket; la extensión sale del MIME, no del archivo elegido. */
    private String rutaNueva(ImagenPlatillo imagen) {
        return UUID.randomUUID() + "." + imagen.extensionDeArchivo();
    }

    private ResultadoRed<Void> subirImagen(String bearer, String ruta, ImagenPlatillo imagen) {
        try {
            RequestBody cuerpo = RequestBody.create(
                    MediaType.parse(imagen.getMimeType()), imagen.getBytes());
            Response<Void> respuesta = storageApi.subir(bearer, ruta, cuerpo).execute();
            if (!respuesta.isSuccessful()) {
                // Los errores de Storage vienen en inglés y con detalle interno
                // ("Payload too large"), así que no se le muestran al usuario.
                return ResultadoRed.fallo(respuesta.code(), NO_SE_PUDO_SUBIR);
            }
            return ResultadoRed.exito(null);
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    /**
     * Borra un archivo del bucket sin propagar el fallo: es siempre una limpieza, nunca la
     * operación que el usuario pidió. Si el borrado falla, el archivo queda huérfano
     * (P-023): no hay recolector de basura del bucket.
     */
    private void borrarArchivo(String bearer, @Nullable String ruta) {
        if (ruta == null || ruta.trim().isEmpty()) {
            return;
        }
        try {
            storageApi.borrar(bearer, ruta).execute();
        } catch (IOException | SecurityException ignorada) {
            // Queda huérfano; ver P-023.
        }
    }

    // ------------------------------------------------------------------ errores

    /** Extrae el mensaje del cuerpo de error, o devuelve el genérico si no se puede. */
    private String mensajeDeError(Response<?> respuesta, String porDefecto) {
        if (respuesta.errorBody() == null) {
            return porDefecto;
        }
        try {
            String crudo = respuesta.errorBody().string();
            // PostgREST devuelve {"message": "..."} — los triggers del menú escriben ahí
            // su texto ("Los platillos no se borran, se desactivan…"), que sí es para el
            // usuario porque lo redactamos nosotros.
            MensajePostgrest postgrest = gson.fromJson(crudo, MensajePostgrest.class);
            if (postgrest != null && postgrest.message != null && !postgrest.message.isEmpty()) {
                return postgrest.message;
            }
        } catch (IOException | RuntimeException ignorada) {
            // Cuerpo ilegible o que no es JSON: se cae al mensaje genérico.
        }
        return porDefecto;
    }

    /** Forma del error de PostgREST; el mensaje del trigger llega en `message`. */
    private static final class MensajePostgrest {
        String message;
    }
}
