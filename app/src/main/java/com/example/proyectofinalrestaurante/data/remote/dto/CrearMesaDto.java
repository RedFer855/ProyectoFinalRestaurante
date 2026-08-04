package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Cuerpo de {@code POST rest/v1/mesa}.
 *
 * <p>No lleva {@code id_estado}, {@code id_estado_mesa} ni {@code actualizado_en}:
 * {@code id_estado} tiene default 1 (Activo), {@code id_estado_mesa} tiene default 1 (Libre),
 * y {@code actualizado_en} lo pone el trigger. Mandarlos desde la app sería duplicar una
 * decisión que ya vive en el servidor.</p>
 */
public final class CrearMesaDto {

    @SerializedName("numero_mesa")
    private final int numeroMesa;

    @SerializedName("capacidad")
    private final int capacidad;

    @SerializedName("ubicacion")
    private final String ubicacion;

    public CrearMesaDto(int numeroMesa, int capacidad, String ubicacion) {
        this.numeroMesa = numeroMesa;
        this.capacidad = capacidad;
        this.ubicacion = ubicacion;
    }
}
