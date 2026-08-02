package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Fila de {@code public.vista_clientes} (Plan Fase 2d, §2.5). Se reusa para leer lo que
 * devuelve el {@code INSERT} sobre la tabla {@code clientes} con
 * {@code Prefer: return=representation}: esa respuesta no trae {@code activo} ni
 * {@code cantidad_pedidos} (solo existen en la vista), así que el estado se deriva siempre de
 * {@code id_estado} y un cliente recién creado no tiene pedidos (0 es el valor correcto de
 * todos modos). Ver {@code ClienteMapper.desdeServidor}.
 *
 * <p>Las claves {@code nombres}/{@code apellidos} van en plural: el DDL real de
 * {@code clientes} (Parte A, verificado 2026-08-01) las nombra así, igual que
 * {@code empleados}. El plan original asumía singular; se corrigió acá, no en la base.</p>
 */
public final class ClienteDto {

    @SerializedName("id_cliente")
    private int idCliente;

    @SerializedName("nombres")
    private String nombre;

    @SerializedName("apellidos")
    private String apellido;

    @SerializedName("identidad")
    private String identidad;

    @SerializedName("telefono")
    private String telefono;

    @SerializedName("id_estado")
    private int idEstado;

    /** Derivado en la vista como {@code (id_estado = 1)}; solo existe en la vista. */
    @SerializedName("activo")
    private boolean activo;

    /** Solo existe en la vista; en la respuesta del INSERT llega en 0 (Gson deja el default). */
    @SerializedName("cantidad_pedidos")
    private int cantidadPedidos;

    @SerializedName("actualizado_en")
    private String actualizadoEn;

    public int getIdCliente() {
        return idCliente;
    }

    public String getNombre() {
        return nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public String getIdentidad() {
        return identidad;
    }

    public String getTelefono() {
        return telefono;
    }

    public int getIdEstado() {
        return idEstado;
    }

    public boolean isActivo() {
        return activo;
    }

    public int getCantidadPedidos() {
        return cantidadPedidos;
    }

    public String getActualizadoEn() {
        return actualizadoEn;
    }
}
