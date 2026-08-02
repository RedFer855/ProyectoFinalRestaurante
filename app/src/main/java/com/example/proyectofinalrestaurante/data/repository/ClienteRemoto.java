package com.example.proyectofinalrestaurante.data.repository;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.data.outbox.ClasificadorDeError;
import com.example.proyectofinalrestaurante.data.remote.SupabaseClienteApi;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarClienteDto;
import com.example.proyectofinalrestaurante.data.remote.dto.BuscarOCrearClienteDto;
import com.example.proyectofinalrestaurante.data.remote.dto.ClienteDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearClienteDto;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

import retrofit2.Response;

/**
 * Ejecutor de <b>todas</b> las llamadas de red del módulo Clientes (Plan Fase 2d, E4). Mismo
 * rol que {@link MesaRemoto} en Mesas.
 */
public final class ClienteRemoto {

    static final String SIN_CONEXION = "Sin conexión al servidor. Intentá de nuevo.";
    static final String SIN_PERMISO_RED =
            "La app no tiene permiso de red. Contactá al desarrollador.";
    static final String SIN_SESION = "Tu sesión venció. Volvé a iniciar sesión.";

    /** Catálogo `estado_general`: baja/alta lógica del cliente. */
    public static final int ID_ESTADO_ACTIVO = 1;
    public static final int ID_ESTADO_BAJA = 2;

    /** Tamaño de página del sync delta; repetir hasta recibir menos que esto (§4.3). */
    public static final int LIMITE_DELTA = 50;

    private final SupabaseClienteApi api;
    private final Supplier<String> proveedorToken;
    private final Gson gson = new Gson();

    public ClienteRemoto(SupabaseClienteApi api, Supplier<String> proveedorToken) {
        this.api = api;
        this.proveedorToken = proveedorToken;
    }

    @Nullable
    private String bearer() {
        String token = proveedorToken.get();
        return token == null ? null : "Bearer " + token;
    }

    // ------------------------------------------------------------------ delta

    public ResultadoRed<List<ClienteDto>> listarClientesDesde(@Nullable String marcaAgua) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            String filtro = marcaAgua == null ? null : "gt." + marcaAgua;
            Response<List<ClienteDto>> respuesta = api.listarClientesDesde(
                    bearer, "*", filtro, "actualizado_en.asc", LIMITE_DELTA).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null) {
                return ResultadoRed.fallo(respuesta.code(),
                        "No se pudo sincronizar la lista de clientes.");
            }
            return ResultadoRed.exito(respuesta.body());
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    // ------------------------------------------------------------------ escrituras del outbox

    public ResultadoRed<ClienteDto> crear(String nombre, String apellido,
                                          @Nullable String identidad, @Nullable String telefono) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            CrearClienteDto cuerpo = new CrearClienteDto(nombre, apellido, identidad, telefono);
            Response<List<ClienteDto>> respuesta = api.crearCliente(bearer, cuerpo).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null || respuesta.body().isEmpty()) {
                return ResultadoRed.fallo(respuesta.code(),
                        mensajeDeError(respuesta, "No se pudo crear el cliente."));
            }
            return ResultadoRed.exito(respuesta.body().get(0));
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    public ResultadoRed<Void> actualizarDatos(int idServidor, String nombre, String apellido,
                                              @Nullable String identidad,
                                              @Nullable String telefono) {
        return patch(idServidor, ActualizarClienteDto.soloDatos(nombre, apellido, identidad, telefono),
                "No se pudieron guardar los cambios del cliente.");
    }

    public ResultadoRed<Void> cambiarBaja(int idServidor, boolean activo) {
        return patch(idServidor,
                ActualizarClienteDto.soloEstado(activo ? ID_ESTADO_ACTIVO : ID_ESTADO_BAJA),
                "No se pudo cambiar el estado del cliente.");
    }

    private ResultadoRed<Void> patch(int idServidor, ActualizarClienteDto cuerpo,
                                     String mensajeSiFalla) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            Response<Void> respuesta =
                    api.actualizarCliente(bearer, "eq." + idServidor, cuerpo).execute();
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

    /** Solo funciona si el cliente no tiene pedidos; si los tiene, el trigger lo rechaza. */
    public ResultadoRed<Void> borrar(int idServidor) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            Response<Void> respuesta = api.borrarCliente(bearer, "eq." + idServidor).execute();
            if (!respuesta.isSuccessful()) {
                return ResultadoRed.fallo(respuesta.code(),
                        mensajeDeError(respuesta, "No se pudo borrar el cliente."));
            }
            return ResultadoRed.exito(null);
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    // ------------------------------------------------------------------ RPC buscar_o_crear_cliente

    /**
     * <b>Exige conexión</b> (Plan Fase 2d, §5.1): el id lo genera el servidor y esta llamada
     * no pasa por Room ni por el outbox. Expuesta para que la Fase 4 (Pedidos) la use.
     */
    public ResultadoRed<Integer> buscarOCrear(String nombre, String apellido,
                                              @Nullable String identidad,
                                              @Nullable String telefono) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            BuscarOCrearClienteDto cuerpo =
                    new BuscarOCrearClienteDto(nombre, apellido, identidad, telefono);
            Response<Integer> respuesta = api.buscarOCrearCliente(bearer, cuerpo).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null) {
                return ResultadoRed.fallo(respuesta.code(),
                        mensajeDeError(respuesta, "No se pudo registrar el cliente."));
            }
            return ResultadoRed.exito(respuesta.body());
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
