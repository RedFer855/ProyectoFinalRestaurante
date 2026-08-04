package com.example.proyectofinalrestaurante.data.remote.dto;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * Cuerpo del PATCH para editar un cliente (Fase 2d). Todos los campos son opcionales
 * (PATCH parcial): solo se mandan los que cambiaron.
 */
public final class ActualizarClienteDto {

    @SerializedName("nombre")
    @Nullable
    private final String nombre;

    @SerializedName("apellido")
    @Nullable
    private final String apellido;

    @SerializedName("identidad")
    @Nullable
    private final String identidad;

    @SerializedName("telefono")
    @Nullable
    private final String telefono;

    @SerializedName("id_estado")
    @Nullable
    private final Integer idEstado;

    private ActualizarClienteDto(@Nullable String nombre, @Nullable String apellido,
                                 @Nullable String identidad, @Nullable String telefono,
                                 @Nullable Integer idEstado) {
        this.nombre = nombre;
        this.apellido = apellido;
        this.identidad = identidad;
        this.telefono = telefono;
        this.idEstado = idEstado;
    }

    /** Solo datos (nombre, apellido, identidad, teléfono). */
    public static ActualizarClienteDto soloDatos(String nombre, String apellido,
                                                @Nullable String identidad,
                                                @Nullable String telefono) {
        return new ActualizarClienteDto(nombre, apellido, identidad, telefono, null);
    }

    /** Solo estado (baja/alta lógica). */
    public static ActualizarClienteDto soloEstado(boolean activo) {
        return new ActualizarClienteDto(null, null, null, null, activo ? 1 : 2);
    }
}
