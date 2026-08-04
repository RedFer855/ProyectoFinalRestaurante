package com.example.proyectofinalrestaurante.data.remote.dto;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * Cuerpo del POST para crear un cliente (Fase 2d).
 * Nombre y apellido obligatorios; identidad y teléfono opcionales.
 */
public final class CrearClienteDto {

    @SerializedName("nombre")
    private final String nombre;

    @SerializedName("apellido")
    private final String apellido;

    @SerializedName("identidad")
    @Nullable
    private final String identidad;

    @SerializedName("telefono")
    @Nullable
    private final String telefono;

    public CrearClienteDto(String nombre, String apellido,
                           @Nullable String identidad, @Nullable String telefono) {
        this.nombre = nombre;
        this.apellido = apellido;
        this.identidad = identidad;
        this.telefono = telefono;
    }
}
