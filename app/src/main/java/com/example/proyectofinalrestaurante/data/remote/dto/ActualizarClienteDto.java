package com.example.proyectofinalrestaurante.data.remote.dto;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * Cuerpo de {@code PATCH rest/v1/clientes}. Mismo truco que {@code ActualizarCategoriaDto}:
 * campos objeto para que Gson omita lo que no cambió, y una factory por operación.
 *
 * <p>{@code nombres}/{@code apellidos} en plural: nombres reales de columna, verificados
 * contra la base el 2026-08-01 (Parte A).</p>
 */
public final class ActualizarClienteDto {

    @SerializedName("nombres")
    private final String nombre;

    @SerializedName("apellidos")
    private final String apellido;

    @SerializedName("identidad")
    private final String identidad;

    @SerializedName("telefono")
    private final String telefono;

    @SerializedName("id_estado")
    private final Integer idEstado;

    private ActualizarClienteDto(String nombre, String apellido, String identidad,
                                 String telefono, Integer idEstado) {
        this.nombre = nombre;
        this.apellido = apellido;
        this.identidad = identidad;
        this.telefono = telefono;
        this.idEstado = idEstado;
    }

    /**
     * Datos del cliente, sin tocar el estado. {@code identidad}/{@code telefono} vacíos se
     * mandan como {@code ""} y no como {@code null}: un {@code null} se omitiría y el valor
     * viejo quedaría en la fila (mismo truco que {@code ActualizarMesaDto.soloDatos}).
     */
    public static ActualizarClienteDto soloDatos(String nombre, String apellido,
                                                 @Nullable String identidad,
                                                 @Nullable String telefono) {
        return new ActualizarClienteDto(nombre, apellido,
                identidad == null ? "" : identidad, telefono == null ? "" : telefono, null);
    }

    /** Dar de baja ({@code id_estado = 2}) o reactivar ({@code id_estado = 1}). */
    public static ActualizarClienteDto soloEstado(int idEstado) {
        return new ActualizarClienteDto(null, null, null, null, idEstado);
    }
}
