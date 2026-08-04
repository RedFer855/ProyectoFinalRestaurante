package com.example.proyectofinalrestaurante.data.repository;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.data.outbox.ClasificadorDeError;
import com.example.proyectofinalrestaurante.data.remote.SupabaseMesaApi;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarMesaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CambiarEstadoMesaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearMesaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.MesaDto;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

import retrofit2.Response;

/**
 * Ejecutor de <b>todas</b> las llamadas de red del módulo Mesas (Fase 2c).
 *
 * <p>La UI <b>nunca</b> habla con la red: lee de Room y escribe local (optimista). Este
 * ejecutor queda como la cara remota que usa el sincronizador para drenar el outbox y bajar
 * el delta.</p>
 *
 * <p>Los fallos viajan en {@link ResultadoRed} con su código HTTP: el outbox necesita saber
 * si un error es permanente o transitorio.</p>
 */
public final class MesaRemoto {

    static final String SIN_CONEXION = "Sin conexión al servidor. Intentá de nuevo.";
    static final String SIN_PERMISO_RED = "La app no tiene permiso de red. Contactá al desarrollador.";
    static final String SIN_SESION = "Tu sesión venció. Volvé a iniciar sesión.";

    /** Tamaño de página del sync delta; repetir hasta recibir menos que esto. */
    public static final int LIMITE_DELTA = 50;

    private final SupabaseMesaApi api;
    private final Supplier<String> proveedorToken;

    public MesaRemoto(SupabaseMesaApi api, Supplier<String> proveedorToken) {
        this.api = api;
        this.proveedorToken = proveedorToken;
    }

    @Nullable
    private String bearer() {
        String token = proveedorToken.get();
        return token == null ? null : "Bearer " + token;
    }

    // ------------------------------------------------------------------ pull

    public ResultadoRed<List<MesaDto>> listarMesas() {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            Response<List<MesaDto>> respuesta = api.listarMesas(bearer).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null) {
                return ResultadoRed.fallo(respuesta.code(), "No se pudieron cargar las mesas.");
            }
            return ResultadoRed.exito(respuesta.body());
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    /**
     * Sync delta paginado (Fase 2b, §4.3). Una marca {@code null} (primera bajada) se
     * traduce en "sin filtro": la primera sincronización baja la tabla entera.
     */
    public ResultadoRed<List<MesaDto>> listarMesasDesde(@Nullable String marcaAgua) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            String filtro = marcaAgua == null ? null : "gt." + marcaAgua;
            Response<List<MesaDto>> respuesta = api.listarMesasDesde(
                    bearer, "*", filtro, "actualizado_en.asc", LIMITE_DELTA).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null) {
                return ResultadoRed.fallo(respuesta.code(), "No se pudieron cargar las mesas.");
            }
            return ResultadoRed.exito(respuesta.body());
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    // ------------------------------------------------------------------ escrituras

    public ResultadoRed<MesaDto> crearMesa(int numeroMesa, int capacidad,
                                           @Nullable String ubicacion) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            CrearMesaDto cuerpo = new CrearMesaDto(numeroMesa, capacidad, ubicacion);
            Response<List<MesaDto>> respuesta = api.crearMesa(bearer, cuerpo).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null || respuesta.body().isEmpty()) {
                return ResultadoRed.fallo(respuesta.code(),
                        mensajeDeError(respuesta, "No se pudo crear la mesa."));
            }
            return ResultadoRed.exito(respuesta.body().get(0));
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    public ResultadoRed<Void> actualizarMesa(int idServidor, int numeroMesa, int capacidad,
                                             @Nullable String ubicacion) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            ActualizarMesaDto cuerpo = ActualizarMesaDto.soloDatos(numeroMesa, capacidad, ubicacion);
            Response<Void> respuesta = api.actualizarMesa(
                    bearer, "eq." + idServidor, cuerpo).execute();
            if (!respuesta.isSuccessful()) {
                return ResultadoRed.fallo(respuesta.code(),
                        mensajeDeError(respuesta, "No se pudieron guardar los cambios."));
            }
            return ResultadoRed.exito(null);
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    /**
     * Cambia el estado operativo via RPC. El mesero solo puede hacer esto.
     * Devuelve void (204 sin cuerpo).
     */
    public ResultadoRed<Void> cambiarEstadoMesa(int idMesaServidor, int idEstadoMesa) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            CambiarEstadoMesaDto cuerpo = new CambiarEstadoMesaDto(idMesaServidor, idEstadoMesa);
            Response<Void> respuesta = api.cambiarEstadoMesa(bearer, cuerpo).execute();
            if (!respuesta.isSuccessful()) {
                return ResultadoRed.fallo(respuesta.code(),
                        mensajeDeError(respuesta, "No se pudo cambiar el estado de la mesa."));
            }
            return ResultadoRed.exito(null);
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    /** Activa o desactiva (baja/alta lógica). */
    public ResultadoRed<Void> cambiarBajaMesa(int idServidor, boolean activo) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            ActualizarMesaDto cuerpo = ActualizarMesaDto.soloEstado(
                    activo ? 1 : 2);
            Response<Void> respuesta = api.actualizarMesa(
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

    // ------------------------------------------------------------------ errores

    private static String mensajeDeError(Response<?> respuesta, String porDefecto) {
        try {
            String cuerpo = respuesta.errorBody() != null
                    ? respuesta.errorBody().string() : null;
            if (cuerpo != null && cuerpo.contains("\"message\"")) {
                int inicio = cuerpo.indexOf("\"message\"") + 10;
                int fin = cuerpo.indexOf("\"", inicio);
                if (fin > inicio) {
                    return cuerpo.substring(inicio, fin).trim();
                }
            }
        } catch (IOException ignorada) {
        }
        return porDefecto;
    }
}
