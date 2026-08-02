package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Cuerpo de {@code PATCH rest/v1/mesa}. Actualizaciones parciales, mismo truco que
 * {@link ActualizarPlatilloDto} y {@link ActualizarCategoriaDto}: todos los campos son
 * objetos y no primitivos a propósito, porque Gson omite los nulos y cada factory manda
 * <b>solo</b> lo que cambió sin pisar el resto.
 *
 * <p>El {@code id_estado} de acá es el que permite la <b>baja/alta lógica</b>
 * ({@code 1 | 2}, Plan Fase 2c, §3): el patrón del repo reusa el DTO de actualización con
 * una factory {@code soloEstado}, igual que {@code ActualizarPlatilloDto} con el platillo.
 * El estado operativo (libre/ocupada/reservada) <b>no</b> viaja por este PATCH: va por el
 * RPC {@code cambiar_estado_mesa}.</p>
 */
public final class ActualizarMesaDto {

    @SerializedName("numero_mesa")
    private final Integer numeroMesa;

    @SerializedName("capacidad")
    private final Integer capacidad;

    @SerializedName("ubicacion")
    private final String ubicacion;

    @SerializedName("id_estado")
    private final Integer idEstado;

    private ActualizarMesaDto(Integer numeroMesa, Integer capacidad, String ubicacion,
                              Integer idEstado) {
        this.numeroMesa = numeroMesa;
        this.capacidad = capacidad;
        this.ubicacion = ubicacion;
        this.idEstado = idEstado;
    }

    /**
     * Datos de la mesa (número, capacidad, ubicación), sin tocar el estado.
     *
     * <p>Una ubicación vacía se manda como {@code ""} y no como null, porque un null se
     * omitiría y la ubicación vieja quedaría en la fila.</p>
     */
    public static ActualizarMesaDto soloDatos(int numeroMesa, int capacidad, String ubicacion) {
        return new ActualizarMesaDto(numeroMesa, capacidad,
                ubicacion == null ? "" : ubicacion, null);
    }

    /** Dar de baja ({@code id_estado = 2}) o reactivar ({@code id_estado = 1}) sin tocar nada más. */
    public static ActualizarMesaDto soloEstado(int idEstado) {
        return new ActualizarMesaDto(null, null, null, idEstado);
    }
}
