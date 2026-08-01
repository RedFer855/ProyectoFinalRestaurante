package com.example.proyectofinalrestaurante.data.remote.dto;

/**
 * Cuerpo de {@code POST functions/v1/crear-empleado}. Los nombres de los campos
 * coinciden con los que espera la Edge Function.
 */
public final class CrearEmpleadoRequestDto {

    private final String nombres;
    private final String apellidos;
    private final String identidad;
    private final String telefono;
    private final String correo;
    private final String rol;
    /** Contraseña temporal. Viaja una sola vez y nunca se guarda ni se loguea. */
    private final String contrasenia;

    public CrearEmpleadoRequestDto(String nombres, String apellidos, String identidad,
                                   String telefono, String correo, String rol,
                                   String contrasenia) {
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.identidad = identidad;
        this.telefono = telefono;
        this.correo = correo;
        this.rol = rol;
        this.contrasenia = contrasenia;
    }
}
