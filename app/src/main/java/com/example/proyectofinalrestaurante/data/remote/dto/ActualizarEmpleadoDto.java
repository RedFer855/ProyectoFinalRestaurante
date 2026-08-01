package com.example.proyectofinalrestaurante.data.remote.dto;

/** Cuerpo de {@code PATCH rest/v1/empleados} — solo datos personales. */
public final class ActualizarEmpleadoDto {

    private final String nombres;
    private final String apellidos;
    private final String identidad;
    private final String telefono;
    private final String correo;

    public ActualizarEmpleadoDto(String nombres, String apellidos, String identidad,
                                 String telefono, String correo) {
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.identidad = identidad;
        this.telefono = telefono;
        this.correo = correo;
    }
}
