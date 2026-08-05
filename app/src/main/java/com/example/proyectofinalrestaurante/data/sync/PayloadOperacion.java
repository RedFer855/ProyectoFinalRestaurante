package com.example.proyectofinalrestaurante.data.sync;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

/**
 * Cuerpo JSON del outbox (Plan Fase 2b, E5): qué datos lleva cada operación pendiente.
 *
 * <p>La mayoría de las operaciones no necesitan payload: el sincronizador lee los datos de la
 * fila local (fuente de verdad). Solo dos necesitan datos que <b>no</b> están en la fila:</p>
 *
 * <ul>
 *   <li>{@code QUITAR_IMAGEN_PLATILLO}: la ruta vieja en el bucket (la fila local ya la
 *       dejó en {@code null} al quitar la foto optimistamente).</li>
 *   <li>{@code BORRAR_CATEGORIA}: el {@code id_servidor} (la fila local ya se borró).</li>
 * </ul>
 */
public final class PayloadOperacion {

    private static final Gson GSON = new Gson();

    private PayloadOperacion() {
    }

    public static String quitarImagen(@Nullable String rutaVieja) {
        QuitarImagen payload = new QuitarImagen();
        payload.rutaVieja = rutaVieja;
        return GSON.toJson(payload);
    }

    public static String borrarCategoria(int idServidor) {
        BorrarCategoria payload = new BorrarCategoria();
        payload.idServidor = idServidor;
        return GSON.toJson(payload);
    }

    @Nullable
    public static String rutaViejaDe(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        QuitarImagen payload = GSON.fromJson(json, QuitarImagen.class);
        return payload == null ? null : payload.rutaVieja;
    }

    @Nullable
    public static Integer idServidorDe(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        BorrarCategoria payload = GSON.fromJson(json, BorrarCategoria.class);
        return payload == null ? null : payload.idServidor;
    }

    /** Payload de {@code AVANZAR_ESTADO_PEDIDO}: el estado al que el usuario lo llevó. */
    public static String avanzarEstado(int idEstadoPedido) {
        AvanzarEstadoPedido payload = new AvanzarEstadoPedido();
        payload.idEstadoPedido = idEstadoPedido;
        return GSON.toJson(payload);
    }

    @Nullable
    public static Integer idEstadoPedidoDe(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        AvanzarEstadoPedido payload = GSON.fromJson(json, AvanzarEstadoPedido.class);
        return payload == null ? null : payload.idEstadoPedido;
    }

    private static final class QuitarImagen {
        @SerializedName("ruta_vieja")
        String rutaVieja;
    }

    private static final class BorrarCategoria {
        @SerializedName("id_servidor")
        Integer idServidor;
    }

    private static final class AvanzarEstadoPedido {
        @SerializedName("id_estado_pedido")
        Integer idEstadoPedido;
    }
}
