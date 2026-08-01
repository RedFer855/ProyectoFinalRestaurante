package com.example.proyectofinalrestaurante.data.repository;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.data.remote.SupabaseMenuApi;
import com.example.proyectofinalrestaurante.data.remote.SupabaseStorageApi;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarCategoriaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarPlatilloDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CategoriaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearCategoriaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearPlatilloDto;
import com.example.proyectofinalrestaurante.data.remote.dto.PlatilloDto;
import com.example.proyectofinalrestaurante.domain.ReglasMenu;
import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.Categoria;
import com.example.proyectofinalrestaurante.domain.model.ImagenPlatillo;
import com.example.proyectofinalrestaurante.domain.model.NuevoPlatillo;
import com.example.proyectofinalrestaurante.domain.model.Platillo;
import com.example.proyectofinalrestaurante.domain.repository.MenuRepository;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Response;

/**
 * Implementación de {@link MenuRepository} contra Supabase (Data Layer, Plan Fase 2a).
 *
 * <p>El token de la sesión activa entra por un {@link Supplier} inyectado en vez de leer
 * {@code SesionActual} directo: así el repositorio se testea sin estado global y
 * {@code data} no depende de dónde vive la sesión — mismo criterio que
 * {@link SupabaseEmpleadoRepository}.</p>
 *
 * <p>Acá se orquestan los <b>dos</b> sistemas que puede tocar una operación del menú: la
 * base (PostgREST) y el bucket de Storage. Que puedan desincronizarse es el riesgo real de
 * este módulo, y por eso cada camino que sube un archivo tiene definido qué pasa si el
 * paso siguiente falla.</p>
 */
public class SupabaseMenuRepository implements MenuRepository {

    private static final String SIN_CONEXION = "Sin conexión al servidor. Intentá de nuevo.";
    private static final String SIN_PERMISO_RED = "La app no tiene permiso de red. Contactá al desarrollador.";
    private static final String SIN_SESION = "Tu sesión venció. Volvé a iniciar sesión.";
    private static final String NO_SE_PUDO_SUBIR = "No se pudo subir la foto. Intentá de nuevo.";
    private static final String IMAGEN_INVALIDA = "La foto no se puede subir: revisá que pese menos de 2 MB y sea JPG, PNG o WEBP.";

    /** Catálogo `estado_general` del servidor. La app solo usa estos dos valores. */
    private static final int ID_ESTADO_ACTIVO = 1;
    private static final int ID_ESTADO_INACTIVO = 2;

    private static final MediaType TIPO_JSON = MediaType.parse("application/json");
    /** Gson omite los nulos, así que el único modo de limpiar la columna es un JSON literal. */
    private static final String CUERPO_QUITAR_IMAGEN = "{\"ruta_imagen\":null}";

    private final SupabaseMenuApi api;
    private final SupabaseStorageApi storageApi;
    private final Supplier<String> proveedorToken;
    private final Gson gson = new Gson();

    public SupabaseMenuRepository(SupabaseMenuApi api, SupabaseStorageApi storageApi,
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

    // ------------------------------------------------------------------ lectura

    @Override
    public Result<List<Platillo>> listarPlatillos() {
        String bearer = bearer();
        if (bearer == null) {
            return Result.fail(SIN_SESION);
        }
        try {
            Response<List<PlatilloDto>> respuesta = api.listarPlatillos(bearer).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null) {
                return Result.fail("No se pudo cargar el menú.");
            }
            List<Platillo> platillos = new ArrayList<>();
            for (PlatilloDto dto : respuesta.body()) {
                platillos.add(aDominio(dto));
            }
            return Result.ok(platillos);
        } catch (IOException ex) {
            return Result.fail(SIN_CONEXION);
        } catch (SecurityException ex) {
            return Result.fail(SIN_PERMISO_RED);
        }
    }

    @Override
    public Result<List<Categoria>> listarCategorias() {
        String bearer = bearer();
        if (bearer == null) {
            return Result.fail(SIN_SESION);
        }
        try {
            Response<List<CategoriaDto>> respuesta = api.listarCategorias(bearer).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null) {
                return Result.fail("No se pudieron cargar las categorías.");
            }
            List<Categoria> categorias = new ArrayList<>();
            for (CategoriaDto dto : respuesta.body()) {
                categorias.add(aDominio(dto));
            }
            return Result.ok(categorias);
        } catch (IOException ex) {
            return Result.fail(SIN_CONEXION);
        } catch (SecurityException ex) {
            return Result.fail(SIN_PERMISO_RED);
        }
    }

    // ------------------------------------------------------------------ platillos

    @Override
    public Result<Platillo> crearPlatillo(NuevoPlatillo nuevo, @Nullable ImagenPlatillo imagen) {
        String bearer = bearer();
        if (bearer == null) {
            return Result.fail(SIN_SESION);
        }

        String ruta = null;
        if (imagen != null) {
            if (!ReglasMenu.puedeSubirse(imagen)) {
                return Result.fail(IMAGEN_INVALIDA);
            }
            ruta = rutaNueva(imagen);
            Result<Void> subida = subirImagen(bearer, ruta, imagen);
            if (!subida.isSuccess()) {
                // La foto no llegó: no se inserta nada. Un platillo con una ruta que no
                // existe se vería como una imagen rota en la lista.
                return Result.fail(subida.getError());
            }
        }

        try {
            CrearPlatilloDto cuerpo = new CrearPlatilloDto(
                    nuevo.getNombre(), nuevo.getDescripcion(), nuevo.getPrecio(),
                    nuevo.getIdCategoria(), ruta);

            Response<List<PlatilloDto>> respuesta = api.crearPlatillo(bearer, cuerpo).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null || respuesta.body().isEmpty()) {
                // El servidor rechazó el insert: se borra el archivo que ya se había
                // subido. Sin esta compensación, cada nombre duplicado o precio inválido
                // dejaría basura permanente en el bucket.
                borrarArchivo(bearer, ruta);
                return Result.fail(mensajeDeError(respuesta, "No se pudo crear el platillo."));
            }
            return Result.ok(aDominio(respuesta.body().get(0)));
        } catch (IOException ex) {
            // A propósito no se compensa acá: sin respuesta no se sabe si el insert entró.
            // Borrar la foto de un platillo que sí se creó es peor (imagen rota, visible)
            // que dejar un archivo huérfano (invisible, cubierto por P-023).
            return Result.fail(SIN_CONEXION);
        } catch (SecurityException ex) {
            return Result.fail(SIN_PERMISO_RED);
        }
    }

    @Override
    public Result<Void> actualizarPlatillo(Platillo platillo, @Nullable ImagenPlatillo imagenNueva) {
        String bearer = bearer();
        if (bearer == null) {
            return Result.fail(SIN_SESION);
        }

        String rutaNueva = null;
        if (imagenNueva != null) {
            if (!ReglasMenu.puedeSubirse(imagenNueva)) {
                return Result.fail(IMAGEN_INVALIDA);
            }
            // Ruta nueva en cada reemplazo, nunca sobrescribir: si la URL no cambia,
            // Glide sigue sirviendo la foto vieja desde su caché.
            rutaNueva = rutaNueva(imagenNueva);
            Result<Void> subida = subirImagen(bearer, rutaNueva, imagenNueva);
            if (!subida.isSuccess()) {
                return Result.fail(subida.getError());
            }
        }

        try {
            ActualizarPlatilloDto cuerpo = rutaNueva == null
                    ? ActualizarPlatilloDto.soloDatos(platillo.getNombre(),
                            platillo.getDescripcion(), platillo.getPrecio(),
                            platillo.getIdCategoria())
                    : ActualizarPlatilloDto.conImagen(platillo.getNombre(),
                            platillo.getDescripcion(), platillo.getPrecio(),
                            platillo.getIdCategoria(), rutaNueva);

            Response<Void> respuesta = api.actualizarPlatillo(
                    bearer, "eq." + platillo.getIdPlatillo(), cuerpo).execute();
            if (!respuesta.isSuccessful()) {
                borrarArchivo(bearer, rutaNueva);
                return Result.fail(mensajeDeError(respuesta, "No se pudieron guardar los cambios."));
            }

            // La vieja se borra al final y solo si la fila ya apunta a la nueva: si algo se
            // cae en el medio, sobra un archivo (barato) en vez de faltar la foto de un
            // platillo que sí existe (visible para el usuario).
            if (rutaNueva != null && platillo.tieneImagen()) {
                borrarArchivo(bearer, platillo.getRutaImagen());
            }
            return Result.ok(null);
        } catch (IOException ex) {
            return Result.fail(SIN_CONEXION);
        } catch (SecurityException ex) {
            return Result.fail(SIN_PERMISO_RED);
        }
    }

    @Override
    public Result<Void> quitarImagen(Platillo platillo) {
        String bearer = bearer();
        if (bearer == null) {
            return Result.fail(SIN_SESION);
        }
        try {
            RequestBody cuerpo = RequestBody.create(TIPO_JSON, CUERPO_QUITAR_IMAGEN);
            Response<Void> respuesta = api.actualizarPlatilloConCuerpoCrudo(
                    bearer, "eq." + platillo.getIdPlatillo(), cuerpo).execute();
            if (!respuesta.isSuccessful()) {
                return Result.fail(mensajeDeError(respuesta, "No se pudo quitar la foto."));
            }
            // Primero la fila, después el archivo: si el borrado falla queda huérfano
            // (P-023), pero el platillo ya se ve correctamente sin foto.
            borrarArchivo(bearer, platillo.getRutaImagen());
            return Result.ok(null);
        } catch (IOException ex) {
            return Result.fail(SIN_CONEXION);
        } catch (SecurityException ex) {
            return Result.fail(SIN_PERMISO_RED);
        }
    }

    @Override
    public Result<Void> cambiarEstadoPlatillo(int idPlatillo, boolean activo) {
        String bearer = bearer();
        if (bearer == null) {
            return Result.fail(SIN_SESION);
        }
        try {
            ActualizarPlatilloDto cuerpo = ActualizarPlatilloDto.soloEstado(
                    activo ? ID_ESTADO_ACTIVO : ID_ESTADO_INACTIVO);
            Response<Void> respuesta = api.actualizarPlatillo(
                    bearer, "eq." + idPlatillo, cuerpo).execute();
            if (!respuesta.isSuccessful()) {
                return Result.fail(mensajeDeError(respuesta, "No se pudo cambiar el estado del platillo."));
            }
            return Result.ok(null);
        } catch (IOException ex) {
            return Result.fail(SIN_CONEXION);
        } catch (SecurityException ex) {
            return Result.fail(SIN_PERMISO_RED);
        }
    }

    // ------------------------------------------------------------------ categorías

    @Override
    public Result<Categoria> crearCategoria(String descripcion) {
        String bearer = bearer();
        if (bearer == null) {
            return Result.fail(SIN_SESION);
        }
        try {
            Response<List<CategoriaDto>> respuesta =
                    api.crearCategoria(bearer, new CrearCategoriaDto(descripcion)).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null || respuesta.body().isEmpty()) {
                return Result.fail(mensajeDeError(respuesta, "No se pudo crear la categoría."));
            }
            return Result.ok(aDominio(respuesta.body().get(0)));
        } catch (IOException ex) {
            return Result.fail(SIN_CONEXION);
        } catch (SecurityException ex) {
            return Result.fail(SIN_PERMISO_RED);
        }
    }

    @Override
    public Result<Void> renombrarCategoria(int idCategoria, String descripcion) {
        return actualizarCategoria(idCategoria, ActualizarCategoriaDto.soloDescripcion(descripcion),
                "No se pudo renombrar la categoría.");
    }

    @Override
    public Result<Void> cambiarEstadoCategoria(int idCategoria, boolean activo) {
        return actualizarCategoria(idCategoria, ActualizarCategoriaDto.soloEstado(
                        activo ? ID_ESTADO_ACTIVO : ID_ESTADO_INACTIVO),
                "No se pudo cambiar el estado de la categoría.");
    }

    @Override
    public Result<Void> borrarCategoria(int idCategoria) {
        String bearer = bearer();
        if (bearer == null) {
            return Result.fail(SIN_SESION);
        }
        try {
            Response<Void> respuesta = api.borrarCategoria(bearer, "eq." + idCategoria).execute();
            if (!respuesta.isSuccessful()) {
                // El trigger trg_categoria_no_borrar_con_platillos responde con su propio
                // mensaje, escrito por nosotros en lenguaje humano.
                return Result.fail(mensajeDeError(respuesta, "No se pudo borrar la categoría."));
            }
            return Result.ok(null);
        } catch (IOException ex) {
            return Result.fail(SIN_CONEXION);
        } catch (SecurityException ex) {
            return Result.fail(SIN_PERMISO_RED);
        }
    }

    private Result<Void> actualizarCategoria(int idCategoria, ActualizarCategoriaDto cuerpo,
                                             String mensajeSiFalla) {
        String bearer = bearer();
        if (bearer == null) {
            return Result.fail(SIN_SESION);
        }
        try {
            Response<Void> respuesta = api.actualizarCategoria(
                    bearer, "eq." + idCategoria, cuerpo).execute();
            if (!respuesta.isSuccessful()) {
                return Result.fail(mensajeDeError(respuesta, mensajeSiFalla));
            }
            return Result.ok(null);
        } catch (IOException ex) {
            return Result.fail(SIN_CONEXION);
        } catch (SecurityException ex) {
            return Result.fail(SIN_PERMISO_RED);
        }
    }

    // ------------------------------------------------------------------ Storage

    /** Nombre único dentro del bucket; la extensión sale del MIME, no del archivo elegido. */
    private String rutaNueva(ImagenPlatillo imagen) {
        return UUID.randomUUID() + "." + imagen.extensionDeArchivo();
    }

    private Result<Void> subirImagen(String bearer, String ruta, ImagenPlatillo imagen) {
        try {
            RequestBody cuerpo = RequestBody.create(
                    MediaType.parse(imagen.getMimeType()), imagen.getBytes());
            Response<Void> respuesta = storageApi.subir(bearer, ruta, cuerpo).execute();
            if (!respuesta.isSuccessful()) {
                // Los errores de Storage vienen en inglés y con detalle interno
                // ("Payload too large"), así que no se le muestran al usuario.
                return Result.fail(NO_SE_PUDO_SUBIR);
            }
            return Result.ok(null);
        } catch (IOException ex) {
            return Result.fail(SIN_CONEXION);
        } catch (SecurityException ex) {
            return Result.fail(SIN_PERMISO_RED);
        }
    }

    /**
     * Borra un archivo del bucket sin propagar el fallo: es siempre una limpieza, nunca la
     * operación que el usuario pidió.
     *
     * <p>Si el borrado falla, el archivo queda huérfano y nadie lo recoge: no hay
     * recolector de basura del bucket. Está registrado como <b>P-023</b>.</p>
     */
    private void borrarArchivo(String bearer, @Nullable String ruta) {
        if (ruta == null || ruta.trim().isEmpty()) {
            return;
        }
        try {
            storageApi.borrar(bearer, ruta).execute();
        } catch (IOException | SecurityException ignorada) {
            // Queda huérfano; ver P-023. Fallar acá confundiría al usuario con un error
            // sobre una operación que, para él, ya terminó bien.
        }
    }

    // ------------------------------------------------------------------ mapeo y errores

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

    /**
     * El estado se deriva de {@code id_estado} y no de la columna {@code activo}: esa
     * última solo existe en la vista, y este mismo DTO se usa para leer la respuesta de un
     * INSERT sobre la tabla, donde llegaría siempre en false.
     */
    private Platillo aDominio(PlatilloDto dto) {
        return new Platillo(
                dto.getIdPlatillo(), dto.getNombre(), dto.getDescripcion(), dto.getPrecio(),
                dto.getIdCategoria(), dto.getNombreCategoria(), dto.getRutaImagen(),
                dto.getIdEstado() == ID_ESTADO_ACTIVO);
    }

    private Categoria aDominio(CategoriaDto dto) {
        return new Categoria(
                dto.getIdCategoria(), dto.getDescripcion(),
                dto.getIdEstado() == ID_ESTADO_ACTIVO,
                dto.getCantidadPlatillos(), dto.getCantidadPlatillosActivos());
    }
}
