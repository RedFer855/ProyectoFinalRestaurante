package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Fila de la vista {@code public.vista_empleados}, que une empleados + usuarios +
 * perfiles del lado del servidor. En la base todo es {@code snake_case}; el mapeo a
 * Java es explícito con {@code @SerializedName}.
 */
public final class EmpleadoDto {

    @SerializedName("id_empleado")
    private int idEmpleado;

    @SerializedName("nombres")
    private String nombres;

    @SerializedName("apellidos")
    private String apellidos;

    @SerializedName("identidad")
    private String identidad;

    @SerializedName("telefono")
    private String telefono;

    @SerializedName("correo")
    private String correo;

    @SerializedName("id_usuario")
    private int idUsuario;

    @SerializedName("apodo_usuario")
    private String apodoUsuario;

    @SerializedName("id_auth_user")
    private String idAuthUser;

    @SerializedName("rol")
    private String rol;

    @SerializedName("activo")
    private boolean activo;

    /**
     * Marca de agua del sync delta. El servidor la calcula como el máximo entre
     * {@code empleados.actualizado_en} y {@code perfiles.actualizado_en}: un empleado
     * "cambia" tanto si se editan sus datos como si se le cambia el rol o el estado, y esos
     * viven en tablas distintas.
     */
    @SerializedName("actualizado_en")
    private String actualizadoEn;

    public int getIdEmpleado() {
        return idEmpleado;
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

    public String getTelefono() {
        return telefono;
    }

    public String getCorreo() {
        return correo;
    }

    public int getIdUsuario() {
        return idUsuario;
    }

    public String getApodoUsuario() {
        return apodoUsuario;
    }

    public String getIdAuthUser() {
        return idAuthUser;
    }

    public String getRol() {
        return rol;
    }

    public boolean isActivo() {
        return activo;
    }

    public String getActualizadoEn() {
        return actualizadoEn;
    }
}
