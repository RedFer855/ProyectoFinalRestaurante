package com.example.proyectofinalrestaurante.domain.model;

import androidx.annotation.Nullable;

/**
 * Cliente del restaurante (Plan Fase 2d). Inmutable.
 *
 * <p>Misma identidad que en Mesas: {@code idLocal} es la PK de Room y {@code idServidor} es
 * el {@code id_cliente} del servidor, {@code null} mientras la fila no se haya subido.</p>
 *
 * <p>{@code identidad} y {@code telefono} son opcionales a propósito: ADR-006 exige poder
 * registrar una "venta de mostrador" sin documento. {@code cantidadPedidos} viene de
 * {@code vista_clientes} y es lo que decide si {@link com.example.proyectofinalrestaurante.domain.ReglasCliente#puedeBorrarse}
 * puede ofrecer un borrado real en vez de una baja lógica.</p>
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
    @Nullable private final String actualizadoEn;
    private final EstadoSync estadoSync;

    public Cliente(int idLocal, @Nullable Integer idServidor, String nombre, String apellido,
                   @Nullable String identidad, @Nullable String telefono, boolean activo,
                   int cantidadPedidos, @Nullable String actualizadoEn, EstadoSync estadoSync) {
        this.idLocal = idLocal;
        this.idServidor = idServidor;
        this.nombre = nombre;
        this.apellido = apellido;
        this.identidad = identidad;
        this.telefono = telefono;
        this.activo = activo;
        this.cantidadPedidos = cantidadPedidos;
        this.actualizadoEn = actualizadoEn;
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

    @Nullable
    public String getActualizadoEn() {
        return actualizadoEn;
    }

    public EstadoSync getEstadoSync() {
        return estadoSync;
    }

    public String nombreCompleto() {
        return nombre + " " + apellido;
    }
}
