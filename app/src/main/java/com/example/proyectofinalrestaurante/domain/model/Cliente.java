package com.example.proyectofinalrestaurante.domain.model;

import androidx.annotation.Nullable;

/**
 * Cliente del restaurante (Fase 2d). Inmutable.
 *
 * <p>El cliente no inicia sesión — es un registro de negocio que administra el staff
 * (ADR-006). La identidad es opcional (venta de mostrador). Nunca se borra, se da de
 * baja ({@code activo == false}).</p>
 *
 * <p>Datos personales: este modelo viaja por la UI y se cachea en Room. No se expone
 * de más — la identidad aparece en el detalle y la búsqueda, no en cada tarjeta.</p>
 */
public final class Cliente {

    private final int idLocal;
    @Nullable private final Integer idServidor;
    private final String nombre;
    private final String apellido;
    @Nullable private final String identidad;
    @Nullable private final String telefono;
    private final boolean activo;
    private final int cantidadPedidos;
    private final EstadoSync estadoSync;

    public Cliente(int idLocal, @Nullable Integer idServidor,
                   String nombre, String apellido,
                   @Nullable String identidad, @Nullable String telefono,
                   boolean activo, int cantidadPedidos, EstadoSync estadoSync) {
        this.idLocal = idLocal;
        this.idServidor = idServidor;
        this.nombre = nombre;
        this.apellido = apellido;
        this.identidad = identidad;
        this.telefono = telefono;
        this.activo = activo;
        this.cantidadPedidos = cantidadPedidos;
        this.estadoSync = estadoSync;
    }

    public int getIdLocal() {
        return idLocal;
    }

    @Nullable
    public Integer getIdServidor() {
        return idServidor;
    }

    public String getNombre() {
        return nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public String nombreCompleto() {
        return nombre + " " + apellido;
    }

    @Nullable
    public String getIdentidad() {
        return identidad;
    }

    @Nullable
    public String getTelefono() {
        return telefono;
    }

    public boolean isActivo() {
        return activo;
    }

    public int getCantidadPedidos() {
        return cantidadPedidos;
    }

    public EstadoSync getEstadoSync() {
        return estadoSync;
    }
}
