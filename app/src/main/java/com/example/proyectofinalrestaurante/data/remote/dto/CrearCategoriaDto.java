package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** Cuerpo de {@code POST rest/v1/categoria}. El estado y la fecha los pone el servidor. */
public final class CrearCategoriaDto {

    @SerializedName("descripcion")
    private final String descripcion;

    public CrearCategoriaDto(String descripcion) {
        this.descripcion = descripcion;
    }
}
