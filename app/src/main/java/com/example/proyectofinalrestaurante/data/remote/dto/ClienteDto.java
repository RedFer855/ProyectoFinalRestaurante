package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Fila de {@code public.vista_clientes}. En la base todo es {@code snake_case}; el mapeo
 * a Java es explícito con {@code @SerializedName}.
 */
public final class ClienteDto {

    @SerializedName("id_cliente")
    private int idCliente;

    @SerializedName("nombre")
    private String nombre;

    @SerializedName("apellido")
    private String apellido;

    @SerializedName("identidad")
    private String identidad;

    @SerializedName("telefono")
    private String telefono;

    @SerializedName("id_estado")
    private int idEstado;

    @SerializedName("activo")
    private boolean activo;

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
