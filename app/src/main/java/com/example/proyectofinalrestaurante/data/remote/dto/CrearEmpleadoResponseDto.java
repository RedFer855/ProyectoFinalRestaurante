package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/** Respuesta 201 de {@code POST functions/v1/crear-empleado}. */
public final class CrearEmpleadoResponseDto {

    @SerializedName("id_empleado")
    private int idEmpleado;

    @SerializedName("id_auth_user")
    private String idAuthUser;

    @SerializedName("apodo_usuario")
    private String apodoUsuario;

    public int getIdEmpleado() {
        return idEmpleado;
    }

    public String getIdAuthUser() {
        return idAuthUser;
    }

    public String getApodoUsuario() {
        return apodoUsuario;
    }
}
