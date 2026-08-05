package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Cuerpo de {@code POST rest/v1/rpc/avanzar_estado_pedido}. Son los argumentos de la única
 * vía de escritura del estado de un pedido (Plan Fase 3, §2.5), con esos nombres exactos:
 * {@code p_id_pedido} y {@code p_id_estado_pedido}.
 *
 * <p>El RPC devuelve {@code void}: un 204 es éxito y no hay cuerpo de respuesta que leer.
 * Los errores de rol o de estado vienen en {@code {"message": "..."}} escritos para el
 * usuario.</p>
 */
public final class AvanzarEstadoPedidoDto {

    @SerializedName("p_id_pedido")
    private final int pIdPedido;

    @SerializedName("p_id_estado_pedido")
    private final int pIdEstadoPedido;

    public AvanzarEstadoPedidoDto(int pIdPedido, int pIdEstadoPedido) {
        this.pIdPedido = pIdPedido;
        this.pIdEstadoPedido = pIdEstadoPedido;
    }

    public int getIdServidor() {
        return pIdPedido;
    }

    public int getIdEstadoPedido() {
        return pIdEstadoPedido;
    }
}