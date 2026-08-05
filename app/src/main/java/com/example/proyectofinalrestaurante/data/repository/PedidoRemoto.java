package com.example.proyectofinalrestaurante.data.repository;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.data.outbox.ClasificadorDeError;
import com.example.proyectofinalrestaurante.data.remote.SupabasePedidoApi;
import com.example.proyectofinalrestaurante.data.remote.dto.AvanzarEstadoPedidoDto;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearPedidoDto;
import com.example.proyectofinalrestaurante.data.remote.dto.PedidoDto;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

import retrofit2.Response;

/**
 * Ejecutor de <b>todas</b> las llamadas de red del módulo Pedidos (Plan Fase 3, E4).
 *
 * <p>Mismo rol que {@link MenuRemoto} y {@link MesaRemoto}: la UI nunca la toca
 * directamente, solo {@code SincronizadorPedidos}. Los fallos viajan en {@link ResultadoRed}
 * con su código HTTP para que el outbox sepa si reintentar (5xx/sin red) o descartar
 * (400/422 del RPC).</p>
 */
public final class PedidoRemoto {

    static final String SIN_CONEXION = "Sin conexión al servidor. Intentá de nuevo.";
    static final String SIN_PERMISO_RED =
            "La app no tiene permiso de red. Contactá al desarrollador.";
    static final String SIN_SESION = "Tu sesión venció. Volvé a iniciar sesión.";

    /** Tamaño de página del sync delta; repetir hasta recibir menos que esto (§4.3). */
    public static final int LIMITE_DELTA = 50;

    private final SupabasePedidoApi api;
    private final Supplier<String> proveedorToken;
    private final Gson gson = new Gson();

    public PedidoRemoto(SupabasePedidoApi api, Supplier<String> proveedorToken) {
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
     * Sync delta paginado (§4.3). {@code marcaAgua} {@code null} se traduce en "sin filtro"
     * —pero el {@code SincronizadorPedidos} nunca la deja {@code null}: en el arranque en
     * frío usa el piso de 48 h (Plan Fase 3, §4.4), así que esta llamada solo recibe
     * {@code null} si un caller la usara a pelo.
     */
    public ResultadoRed<List<PedidoDto>> listarPedidosDesde(@Nullable String marcaAgua,
                                                            int desplazamiento) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            String filtro = marcaAgua == null ? null : "gt." + marcaAgua;
            // El id desempata: sin un orden total, dos pedidos con el mismo offset pueden
            // devolver filas distintas y perderse alguna entre páginas.
            Response<List<PedidoDto>> respuesta = api.listarPedidosDesde(
                    bearer, "*", filtro, "actualizado_en.asc,id_pedido.asc",
                    LIMITE_DELTA, desplazamiento).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null) {
                return ResultadoRed.fallo(respuesta.code(),
                        "No se pudieron cargar los pedidos.");
            }
            return ResultadoRed.exito(respuesta.body());
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    // ------------------------------------------------------------------ escrituras

    /**
     * RPC {@code crear_pedido} (Plan Fase 3b, §5.3): sube cabecera + líneas en un solo
     * {@code JSONB}. Devuelve el {@code id_pedido}; el mismo id si la clave de idempotencia
     * ya se usó (B2). Un {@code raise exception} del RPC (rol inválido, carrito vacío, >50
     * líneas, platillo inexistente o inactivo) llega como <b>400</b> → permanente en el
     * {@code ClasificadorDeError}: el outbox lo descarta en vez de reintentar tres veces.
     */
    public ResultadoRed<CrearPedidoDto> crearPedido(String payloadJson) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            Response<CrearPedidoDto> respuesta = api.crearPedido(bearer, payloadJson).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null) {
                return ResultadoRed.fallo(respuesta.code(),
                        mensajeDeError(respuesta, "No se pudo tomar el pedido."));
            }
            return ResultadoRed.exito(respuesta.body());
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    /**
     * RPC {@code avanzar_estado_pedido}: la única vía de escritura del estado (Plan Fase 3,
     * §2.5). Devuelve {@code void}; un 204 es éxito. Los errores del RPC (rol_actual(),
     * pedido cerrado, transición inválida) están en {@code {"message": "..."}} y escritos
     * para el usuario.
     */
    public ResultadoRed<Void> avanzarEstado(int idServidor, int idEstadoPedido) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            Response<Void> respuesta = api.avanzarEstado(
                    bearer, new AvanzarEstadoPedidoDto(idServidor, idEstadoPedido)).execute();
            if (!respuesta.isSuccessful()) {
                return ResultadoRed.fallo(respuesta.code(),
                        mensajeDeError(respuesta, "No se pudo cambiar el estado del pedido."));
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