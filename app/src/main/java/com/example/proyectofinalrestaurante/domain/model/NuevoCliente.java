package com.example.proyectofinalrestaurante.domain.model;

import androidx.annotation.Nullable;

/**
 * Datos que se mandan al crear o editar un cliente (Plan Fase 2d, E1). Sin id: lo genera la
 * base. Inmutable.
 */
public final class NuevoCliente {

    private final String nombre;
    private final String apellido;
    @Nullable private final String identidad;
    @Nullable private final String telefono;

    public NuevoCliente(String nombre, String apellido, @Nullable String identidad,
                        @Nullable String telefono) {
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
