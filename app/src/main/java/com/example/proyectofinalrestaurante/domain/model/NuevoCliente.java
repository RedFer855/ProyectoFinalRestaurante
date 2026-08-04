package com.example.proyectofinalrestaurante.domain.model;

import androidx.annotation.Nullable;

/**
 * Datos para crear o editar un cliente (Fase 2d). Sin id, sin estado, sin fecha.
 *
 * <p>La identidad es opcional — la venta de mostrador no la pide (ADR-006). El teléfono
 * también es opcional.</p>
 */
public final class NuevoCliente {

    private final String nombre;
    private final String apellido;
    @Nullable private final String identidad;
    @Nullable private final String telefono;

    public NuevoCliente(String nombre, String apellido,
                        @Nullable String identidad, @Nullable String telefono) {
        this.nombre = nombre;
        this.apellido = apellido;
        this.identidad = identidad;
        this.telefono = telefono;
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
}
