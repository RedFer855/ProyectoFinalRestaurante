package com.example.proyectofinalrestaurante.data.repository;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.data.outbox.ClasificadorDeError;
import com.example.proyectofinalrestaurante.data.remote.SupabaseEmpleadoApi;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarEmpleadoDto;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarPerfilDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearEmpleadoRequestDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearEmpleadoResponseDto;
import com.example.proyectofinalrestaurante.data.remote.dto.EmpleadoDto;
import com.example.proyectofinalrestaurante.data.remote.dto.ErrorApiDto;
import com.example.proyectofinalrestaurante.domain.model.NuevoEmpleado;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

import retrofit2.Response;

/**
 * Ejecutor de <b>todas</b> las llamadas de red del módulo Empleados.
 *
 * <p>Era {@code SupabaseEmpleadoRepository}, que la UI llamaba directo. Al migrar Empleados a
 * offline-first pasó a ser la cara remota que usa el sincronizador: la UI ahora lee de Room y
 * escribe local. Mismo rol que {@link MenuRemoto} en el Menú.</p>
 *
 * <p>Los fallos viajan en {@link ResultadoRed} con su código HTTP, porque el outbox necesita
 * distinguir un error permanente (400 del trigger → descartar) de uno transitorio (500 →
 * reintentar). El token entra por un {@link Supplier} inyectado, para poder testear sin
 * estado global.</p>
 */
public final class EmpleadoRemoto {

    static final String SIN_CONEXION = "Sin conexión al servidor. Intentá de nuevo.";
    static final String SIN_PERMISO_RED =
            "La app no tiene permiso de red. Contactá al desarrollador.";
    static final String SIN_SESION = "Tu sesión venció. Volvé a iniciar sesión.";

    /** Tamaño de página del sync delta; repetir hasta recibir menos que esto (§4.3). */
    public static final int LIMITE_DELTA = 50;

    private final SupabaseEmpleadoApi api;
    private final Supplier<String> proveedorToken;
    private final Gson gson = new Gson();

    public EmpleadoRemoto(SupabaseEmpleadoApi api, Supplier<String> proveedorToken) {
        this.api = api;
        this.proveedorToken = proveedorToken;
    }

    @Nullable
    private String bearer() {
        String token = proveedorToken.get();
        return token == null ? null : "Bearer " + token;
    }

    // ------------------------------------------------------------------ delta

    /**
     * Sync delta paginado. Una marca {@code null} (primera bajada) se traduce en "sin
     * filtro": Retrofit omite el query cuando el valor es {@code null}, así que la primera
     * sincronización baja la tabla entera.
     */
    public ResultadoRed<List<EmpleadoDto>> listarDesde(@Nullable String marcaAgua) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            String filtro = marcaAgua == null ? null : "gt." + marcaAgua;
            Response<List<EmpleadoDto>> respuesta = api.listarDesde(
                    bearer, "*", filtro, "actualizado_en.asc", LIMITE_DELTA).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null) {
                return ResultadoRed.fallo(respuesta.code(),
                        "No se pudo sincronizar la lista de empleados.");
            }
            return ResultadoRed.exito(respuesta.body());
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    // ------------------------------------------------------------------ alta (siempre online)

    /**
     * Alta de empleado. <b>Es la única operación del módulo que no pasa por el outbox</b>:
     * crea una cuenta en Supabase Auth vía Edge Function, así que encolarla obligaría a
     * guardar la contraseña temporal en el dispositivo y a reintentar un {@code POST} no
     * idempotente que crea cuentas. La UI la ofrece solo con conexión.
     */
    public ResultadoRed<CrearEmpleadoResponseDto> crear(NuevoEmpleado nuevo) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            CrearEmpleadoRequestDto cuerpo = new CrearEmpleadoRequestDto(
                    nuevo.getNombres(), nuevo.getApellidos(), nuevo.getIdentidad(),
                    nuevo.getTelefono(), nuevo.getCorreo(), nuevo.getRol(),
                    nuevo.getContraseniaTemporal());
            Response<CrearEmpleadoResponseDto> respuesta = api.crear(bearer, cuerpo).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null) {
                // Los mensajes de la Edge Function los escribimos nosotros y están en
                // lenguaje humano, así que sí se le muestran al usuario.
                return ResultadoRed.fallo(respuesta.code(),
                        mensajeDeError(respuesta, "No se pudo crear el empleado."));
            }
            return ResultadoRed.exito(respuesta.body());
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    // ------------------------------------------------------------------ escrituras del outbox

    /**
     * Datos personales. Toca dos tablas: `empleados` con los datos, y `perfiles` con el
     * nombre completo, que es lo que muestra el menú lateral de esa persona.
     */
    public ResultadoRed<Void> actualizarDatos(int idEmpleado, String idAuthUser,
                                              String nombres, String apellidos,
                                              String identidad, @Nullable String telefono,
                                              String correo) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            ActualizarEmpleadoDto cuerpo = new ActualizarEmpleadoDto(
                    nombres, apellidos, identidad, telefono, correo);
            Response<Void> respuesta = api.actualizarEmpleado(
                    bearer, "eq." + idEmpleado, cuerpo).execute();
            if (!respuesta.isSuccessful()) {
                return ResultadoRed.fallo(respuesta.code(),
                        mensajeDeError(respuesta, "No se pudieron guardar los cambios."));
            }

            Response<Void> perfil = api.actualizarPerfil(bearer, "eq." + idAuthUser,
                    ActualizarPerfilDto.soloNombre(nombres + " " + apellidos)).execute();
            if (!perfil.isSuccessful()) {
                return ResultadoRed.fallo(perfil.code(),
                        mensajeDeError(perfil, "No se pudieron guardar los cambios."));
            }
            return ResultadoRed.exito(null);
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    public ResultadoRed<Void> cambiarRol(String idAuthUser, String nuevoRol) {
        return actualizarPerfil(idAuthUser, ActualizarPerfilDto.soloRol(nuevoRol),
                "No se pudo cambiar el rol.");
    }

    public ResultadoRed<Void> cambiarEstado(String idAuthUser, boolean activo) {
        return actualizarPerfil(idAuthUser, ActualizarPerfilDto.soloEstado(activo),
                "No se pudo cambiar el estado.");
    }

    private ResultadoRed<Void> actualizarPerfil(String idAuthUser, ActualizarPerfilDto cuerpo,
                                                String mensajeSiFalla) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            Response<Void> respuesta =
                    api.actualizarPerfil(bearer, "eq." + idAuthUser, cuerpo).execute();
            if (!respuesta.isSuccessful()) {
                // El trigger proteger_admins() responde 400 con su propio mensaje
                // ("No se puede modificar a otro administrador"). Es información útil para
                // el usuario, no un detalle interno.
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

    // ------------------------------------------------------------------ errores

    /** Extrae el mensaje del cuerpo de error, o devuelve el genérico si no se puede. */
    private String mensajeDeError(Response<?> respuesta, String porDefecto) {
        if (respuesta.errorBody() == null) {
            return porDefecto;
        }
        try {
            String crudo = respuesta.errorBody().string();
            ErrorApiDto error = gson.fromJson(crudo, ErrorApiDto.class);
            if (error != null && error.getError() != null && !error.getError().isEmpty()) {
                return error.getError();
            }
            // PostgREST devuelve {"message": "..."} — el trigger escribe ahí su texto.
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
