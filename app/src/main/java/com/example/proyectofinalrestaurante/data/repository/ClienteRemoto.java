package com.example.proyectofinalrestaurante.data.repository;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.data.outbox.ClasificadorDeError;
import com.example.proyectofinalrestaurante.data.remote.SupabaseClienteApi;
import com.example.proyectofinalrestaurante.data.remote.dto.ActualizarClienteDto;
import com.example.proyectofinalrestaurante.data.remote.dto.ClienteDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearClienteDto;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

import retrofit2.Response;

/**
 * Ejecutor de <b>todas</b> las llamadas de red del módulo Clientes (Fase 2d).
 *
 * <p>La UI <b>nunca</b> habla con la red. Este ejecutor queda como la cara remota que usa
 * el sincronizador para drenar el outbox y bajar el delta.</p>
 */
public final class ClienteRemoto {

    static final String SIN_CONEXION = "Sin conexión al servidor. Intentá de nuevo.";
    static final String SIN_PERMISO_RED = "La app no tiene permiso de red. Contactá al desarrollador.";
    static final String SIN_SESION = "Tu sesión venció. Volvé a iniciar sesión.";

    public static final int LIMITE_DELTA = 50;

    private final SupabaseClienteApi api;
    private final Supplier<String> proveedorToken;

    public ClienteRemoto(SupabaseClienteApi api, Supplier<String> proveedorToken) {
        this.api = api;
        this.proveedorToken = proveedorToken;
    }

    @Nullable
    private String bearer() {
        String token = proveedorToken.get();
        return token == null ? null : "Bearer " + token;
    }

    // ------------------------------------------------------------------ pull

    public ResultadoRed<List<ClienteDto>> listarClientes() {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            Response<List<ClienteDto>> respuesta = api.listarClientes(
                    bearer, "*", null, "nombre.asc", LIMITE_DELTA).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null) {
                return ResultadoRed.fallo(respuesta.code(),
                        "No se pudieron cargar los clientes.");
            }
            return ResultadoRed.exito(respuesta.body());
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    public ResultadoRed<List<ClienteDto>> listarClientesDesde(@Nullable String marcaAgua) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            String filtro = marcaAgua == null ? null : "gt." + marcaAgua;
            Response<List<ClienteDto>> respuesta = api.listarClientes(
                    bearer, "*", filtro, "actualizado_en.asc", LIMITE_DELTA).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null) {
                return ResultadoRed.fallo(respuesta.code(),
                        "No se pudieron cargar los clientes.");
            }
            return ResultadoRed.exito(respuesta.body());
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    // ------------------------------------------------------------------ escrituras

    public ResultadoRed<ClienteDto> crearCliente(String nombre, String apellido,
                                                  @Nullable String identidad,
                                                  @Nullable String telefono) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            CrearClienteDto cuerpo = new CrearClienteDto(nombre, apellido, identidad, telefono);
            Response<List<ClienteDto>> respuesta = api.crearCliente(
                    bearer, "return=representation", cuerpo).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null
                    || respuesta.body().isEmpty()) {
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

    public ResultadoRed<Void> actualizarCliente(int idServidor, String nombre, String apellido,
                                                 @Nullable String identidad,
                                                 @Nullable String telefono) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            ActualizarClienteDto cuerpo = ActualizarClienteDto.soloDatos(
                    nombre, apellido, identidad, telefono);
            Response<Void> respuesta = api.actualizarCliente(
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

    public ResultadoRed<Void> cambiarEstadoCliente(int idServidor, boolean activo) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            ActualizarClienteDto cuerpo = ActualizarClienteDto.soloEstado(activo);
            Response<Void> respuesta = api.actualizarCliente(
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

    public ResultadoRed<Void> borrarCliente(int idServidor) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            Response<Void> respuesta = api.borrarCliente(
                    bearer, "eq." + idServidor).execute();
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
