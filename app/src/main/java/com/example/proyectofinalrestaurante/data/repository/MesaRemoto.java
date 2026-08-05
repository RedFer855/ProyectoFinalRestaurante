package com.example.proyectofinalrestaurante.data.repository;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.data.outbox.ClasificadorDeError;
import com.example.proyectofinalrestaurante.data.remote.SupabaseMesaApi;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarMesaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CambiarEstadoMesaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearMesaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.MesaDto;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

import retrofit2.Response;

/**
 * Ejecutor de <b>todas</b> las llamadas de red del módulo Mesas (Plan Fase 2c, E4).
 *
 * <p>Mismo rol que {@link MenuRemoto} y {@link EmpleadoRemoto}: la UI nunca la toca
 * directamente, solo {@code SincronizadorMesas}. Los fallos viajan en {@link ResultadoRed}
 * con su código HTTP para que el outbox sepa si reintentar (5xx/sin red) o descartar
 * (409/400 del trigger o del RPC).</p>
 */
public final class MesaRemoto {

    static final String SIN_CONEXION = "Sin conexión al servidor. Intentá de nuevo.";
    static final String SIN_PERMISO_RED =
            "La app no tiene permiso de red. Contactá al desarrollador.";
    static final String SIN_SESION = "Tu sesión venció. Volvé a iniciar sesión.";

    /** Catálogo `estado_general`: baja/alta lógica de la mesa (Plan Fase 2c, §2.1). */
    public static final int ID_ESTADO_ACTIVO = 1;
    public static final int ID_ESTADO_BAJA = 2;

    /** Tamaño de página del sync delta; repetir hasta recibir menos que esto (§4.3). */
    public static final int LIMITE_DELTA = 50;

    private final SupabaseMesaApi api;
    private final Supplier<String> proveedorToken;
    private final Gson gson = new Gson();

    public MesaRemoto(SupabaseMesaApi api, Supplier<String> proveedorToken) {
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
     * Sync delta paginado (§4.3). Una marca {@code null} baja la vista completa: la primera
     * sincronización. La marca queda fija durante toda la pasada; lo que avanza entre
     * páginas es {@code desplazamiento} (P-029, 2026-08-04).
     */
    public ResultadoRed<List<MesaDto>> listarMesasDesde(@Nullable String marcaAgua,
                                                        int desplazamiento) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            String filtro = marcaAgua == null ? null : "gt." + marcaAgua;
            // El id desempata: sin un orden total, dos pedidos con el mismo offset pueden
            // devolver filas distintas y perderse alguna entre páginas.
            Response<List<MesaDto>> respuesta = api.listarMesasDesde(
                    bearer, "*", filtro, "actualizado_en.asc,id_mesa.asc", LIMITE_DELTA,
                    desplazamiento).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null) {
                return ResultadoRed.fallo(respuesta.code(), "No se pudo sincronizar el salón.");
            }
            return ResultadoRed.exito(respuesta.body());
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    // ------------------------------------------------------------------ escrituras

    public ResultadoRed<MesaDto> crear(int numeroMesa, int capacidad, @Nullable String ubicacion) {
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

    public ResultadoRed<Void> actualizarDatos(int idServidor, int numeroMesa, int capacidad,
                                              @Nullable String ubicacion) {
        return patch(idServidor, ActualizarMesaDto.soloDatos(numeroMesa, capacidad, ubicacion),
                "No se pudieron guardar los cambios de la mesa.");
    }

    /** Baja ({@code id_estado = 2}) o alta ({@code id_estado = 1}) lógica. Nunca se borra. */
    public ResultadoRed<Void> cambiarBaja(int idServidor, boolean activo) {
        return patch(idServidor,
                ActualizarMesaDto.soloEstado(activo ? ID_ESTADO_ACTIVO : ID_ESTADO_BAJA),
                "No se pudo cambiar el estado de la mesa.");
    }

    private ResultadoRed<Void> patch(int idServidor, ActualizarMesaDto cuerpo,
                                     String mensajeSiFalla) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            Response<Void> respuesta =
                    api.actualizarMesa(bearer, "eq." + idServidor, cuerpo).execute();
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

    /**
     * RPC {@code cambiar_estado_mesa}: la única vía de escritura del mesero (Plan Fase 2c,
     * §2.4). Devuelve {@code void}; un 204 es éxito.
     */
    public ResultadoRed<Void> cambiarEstado(int idServidor, int idEstadoMesa) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            Response<Void> respuesta = api.cambiarEstadoMesa(
                    bearer, new CambiarEstadoMesaDto(idServidor, idEstadoMesa)).execute();
            if (!respuesta.isSuccessful()) {
                // Los errores del RPC (rol_actual(), estado inexistente, mesa de baja) están
                // en {"message": "..."} y escritos para el usuario (Plan Fase 2c, §5.3).
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

    // ------------------------------------------------------------------ errores

    private String mensajeDeError(Response<?> respuesta, String porDefecto) {
        if (respuesta.errorBody() == null) {
            return porDefecto;
        }
        try {
            String crudo = respuesta.errorBody().string();
            MensajePostgrest postgrest = gson.fromJson(crudo, MensajePostgrest.class);
            if (postgrest != null && postgrest.message != null && !postgrest.message.isEmpty()) {
                return postgrest.message;
            }
        } catch (IOException | RuntimeException ignorada) {
            // Cuerpo ilegible o que no es JSON: se cae al mensaje genérico.
        }
        return porDefecto;
    }

    private static final class MensajePostgrest {
        String message;
    }
}
