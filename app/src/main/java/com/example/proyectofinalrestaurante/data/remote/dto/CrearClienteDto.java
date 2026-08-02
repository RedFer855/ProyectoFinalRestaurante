package com.example.proyectofinalrestaurante.data.remote.dto;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * Cuerpo de {@code POST rest/v1/clientes}. {@code identidad} y {@code telefono} nulos se
 * omiten (Gson no serializa nulos), que es lo que se quiere para la venta de mostrador
 * (ADR-006). No lleva {@code id_estado}: el servidor lo pone en Activo por default.
 *
 * <p>{@code nombres}/{@code apellidos} en plural: nombres reales de columna, verificados
 * contra la base el 2026-08-01 (Parte A).</p>
 */
public final class CrearClienteDto {

    @SerializedName("nombres")
    private final String nombre;

    @SerializedName("apellidos")
    private final String apellido;

    @SerializedName("identidad")
    @Nullable
    private final String identidad;

    @SerializedName("telefono")
    @Nullable
    private final String telefono;

    public CrearClienteDto(String nombre, String apellido, @Nullable String identidad,
                           @Nullable String telefono) {
        this.nombre = nombre;
        this.apellido = apellido;
        this.identidad = identidad;
        this.telefono = telefono;
    }
}
