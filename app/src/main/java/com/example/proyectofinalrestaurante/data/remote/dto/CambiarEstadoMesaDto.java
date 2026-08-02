package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Cuerpo de {@code POST rest/v1/rpc/cambiar_estado_mesa}. Son los argumentos del RPC
 * (Plan Fase 2c, §2.4), con esos nombres exactos: {@code p_id_mesa} y
 * {@code p_id_estado_mesa}.
 *
 * <p>El RPC devuelve {@code void}: un 204 es éxito y no hay cuerpo de respuesta que
 * leer (Plan Fase 2c, §5.2).</p>
 */
public final class CambiarEstadoMesaDto {

    @SerializedName("p_id_mesa")
    private final int pIdMesa;

    @SerializedName("p_id_estado_mesa")
    private final int pIdEstadoMesa;

    public CambiarEstadoMesaDto(int pIdMesa, int pIdEstadoMesa) {
        this.pIdMesa = pIdMesa;
        this.pIdEstadoMesa = pIdEstadoMesa;
    }
}
