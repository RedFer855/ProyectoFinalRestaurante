package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Cuerpo de {@code PATCH rest/v1/mesa}. Actualizaciones parciales.
 *
 * <p>Todos los campos son objetos y no primitivos a propósito: Gson omite los nulos, así
 * que cada factory manda <b>solo</b> lo que cambió y no pisa el resto.</p>
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

    /** Activar o desactivar sin tocar ningún otro campo. */
    public static ActualizarMesaDto soloEstado(int idEstado) {
        return new ActualizarMesaDto(null, null, null, idEstado);
    }

    /** Datos de la mesa sin tocar el estado. */
    public static ActualizarMesaDto soloDatos(int numeroMesa, int capacidad, String ubicacion) {
        return new ActualizarMesaDto(numeroMesa, capacidad, ubicacion, null);
    }

    /** Estado completo de la fila (para outbox). */
    public static ActualizarMesaDto conTodo(int numeroMesa, int capacidad, String ubicacion,
                                            int idEstado) {
        return new ActualizarMesaDto(numeroMesa, capacidad, ubicacion, idEstado);
    }
}
