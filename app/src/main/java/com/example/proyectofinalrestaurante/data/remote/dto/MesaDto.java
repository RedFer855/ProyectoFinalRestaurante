package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Fila de {@code public.vista_mesas} (Plan Fase 2c, §2.5). En la base todo es
 * {@code snake_case}; el mapeo a Java es explícito con {@code @SerializedName}.
 *
 * <p>La vista une {@code mesa} con el catálogo {@code estado_mesa} y deriva {@code activo}
 * de {@code id_estado}: {@code estado_mesa} (la descripción del join) y {@code activo} solo
 * vienen de la vista.</p>
 *
 * <p>Este mismo DTO se reusa para leer lo que devuelve el {@code INSERT} sobre la tabla
 * {@code mesa} con {@code Prefer: return=representation}. Ojo con la diferencia: la tabla
 * <b>no</b> tiene {@code estado_mesa} ni {@code activo}, así que en ese caso llegan
 * {@code null}/{@code false}. Por eso la mesa se deriva siempre de {@code id_estado} y
 * {@code id_estado_mesa}, que sí existen en las dos.</p>
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

    /** Descripción del catálogo {@code estado_mesa} resuelta por el join de la vista. */
    @SerializedName("estado_mesa")
    private String estadoMesa;

    @SerializedName("id_estado")
    private int idEstado;

    /** Derivado en la vista como {@code (id_estado = 1)}; solo existe en la vista. */
    @SerializedName("activo")
    private boolean activo;

    /** Marca de la fila en el servidor; es la que permite el sync delta (Fase 2b, §4.3). */
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
