package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Cuerpo de {@code PATCH rest/v1/categoria}. Igual que {@link ActualizarPlatilloDto}:
 * campos objeto para que Gson omita lo que no cambió.
 */
public final class ActualizarCategoriaDto {

    @SerializedName("descripcion")
    private final String descripcion;

    @SerializedName("id_estado")
    private final Integer idEstado;

    private ActualizarCategoriaDto(String descripcion, Integer idEstado) {
        this.descripcion = descripcion;
        this.idEstado = idEstado;
    }

    public static ActualizarCategoriaDto soloDescripcion(String descripcion) {
        return new ActualizarCategoriaDto(descripcion, null);
    }

    public static ActualizarCategoriaDto soloEstado(int idEstado) {
        return new ActualizarCategoriaDto(null, idEstado);
    }
}
