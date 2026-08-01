package com.example.proyectofinalrestaurante.domain.model;

import androidx.annotation.Nullable;

/**
 * Datos para dar de alta un empleado (Plan Fase 1d). Se separa de {@link Empleado}
 * porque al crear todavía no existen ni el id, ni el usuario, ni el perfil — y sí
 * existe una contraseña temporal que el empleado ya creado nunca vuelve a exponer.
 */
public final class NuevoEmpleado {

    private final String nombres;
    private final String apellidos;
    private final String identidad;
    @Nullable private final String telefono;
    private final String correo;
    private final String rol;
    /**
     * Contraseña temporal que define el admin. Vive solo el tiempo de la llamada:
     * nunca se guarda, nunca se muestra de vuelta y nunca se escribe en un log.
     */
    private final String contraseniaTemporal;

    public NuevoEmpleado(String nombres, String apellidos, String identidad,
                         @Nullable String telefono, String correo, String rol,
                         String contraseniaTemporal) {
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.identidad = identidad;
        this.telefono = telefono;
        this.correo = correo;
        this.rol = rol;
        this.contraseniaTemporal = contraseniaTemporal;
    }

    public String getNombres() {
        return nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public String getIdentidad() {
        return identidad;
    }

    @Nullable
    public String getTelefono() {
        return telefono;
    }

    public String getCorreo() {
        return correo;
    }

    public String getRol() {
        return rol;
    }

    public String getContraseniaTemporal() {
        return contraseniaTemporal;
    }
}
