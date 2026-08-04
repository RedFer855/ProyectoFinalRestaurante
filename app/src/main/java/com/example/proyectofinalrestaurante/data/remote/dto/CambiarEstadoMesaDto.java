package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Cuerpo de {@code POST rest/v1/rpc/cambiar_estado_mesa}.
 *
 * <p>El RPC devuelve {@code void} (204 sin cuerpo). El tipo de retorno en Retrofit es
 * {@code Call<Void>}.</p>
 */
public final class CambiarEstadoMesaDto {

    @SerializedName("p_id_mesa")
    private final int idMesa;

    @SerializedName("p_id_estado_mesa")
    private final int idEstadoMesa;

    public CambiarEstadoMesaDto(int idMesa, int idEstadoMesa) {
        this.idMesa = idMesa;
        this.idEstadoMesa = idEstadoMesa;
    }
}
