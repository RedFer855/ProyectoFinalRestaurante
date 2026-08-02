package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Cuerpo de {@code POST rest/v1/mesa}.
 *
 * <p>No lleva {@code id_estado_mesa}, {@code id_estado} ni {@code actualizado_en}: el
 * primero tiene default 1 (Libre), el segundo default 1 (Activo) y el tercero lo pone el
 * trigger {@code trg_mesa_actualizado_en}. Mandarlos desde la app sería duplicar decisiones
 * que ya viven en el servidor (Plan Fase 2c, §2.2).</p>
 *
 * <p>{@code ubicacion} nula se omite (Gson no serializa nulos), que es lo que se quiere
 * para una mesa sin ubicación.</p>
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
