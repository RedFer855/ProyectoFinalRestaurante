package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Fila de {@code public.vista_mesas}. En la base todo es {@code snake_case}; el mapeo
 * a Java es explícito con {@code @SerializedName}.
 *
 * <p>Este DTO se usa tanto para el sync delta como para la respuesta del INSERT con
 * {@code Prefer: return=representation}. Las columnas de la vista que no están en la tabla
 * ({@code estado_mesa}, {@code activo}) vienen nulas en la respuesta del INSERT.</p>
 */
public final class MesaDto {

    @SerializedName("id_mesa")
    private int idMesa;

    @SerializedName("numero_mesa")
    private int numeroMesa;

    @SerializedName("capacidad")
    private int capacidad;

    @SerializedName("ubicacion")
    private String ubicacion;

    @SerializedName("id_estado_mesa")
    private int idEstadoMesa;

    /** Solo viene de la vista; es null en la respuesta de un INSERT sobre la tabla. */
    @SerializedName("estado_mesa")
    private String estadoMesa;

    @SerializedName("id_estado")
    private int idEstado;

    @SerializedName("activo")
    private boolean activo;

    @SerializedName("actualizado_en")
    private String actualizadoEn;

    public int getIdMesa() {
        return idMesa;
    }

    public int getNumeroMesa() {
        return numeroMesa;
    }

    public int getCapacidad() {
        return capacidad;
    }

    public String getUbicacion() {
        return ubicacion;
    }

    public int getIdEstadoMesa() {
        return idEstadoMesa;
    }

    public String getEstadoMesa() {
        return estadoMesa;
    }

    public int getIdEstado() {
        return idEstado;
    }

    public boolean isActivo() {
        return activo;
    }

    public String getActualizadoEn() {
        return actualizadoEn;
    }
}
