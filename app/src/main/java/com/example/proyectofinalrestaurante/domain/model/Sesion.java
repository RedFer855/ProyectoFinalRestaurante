package com.example.proyectofinalrestaurante.domain.model;

/** Entidad de dominio: sesión de un usuario autenticado, con su perfil ya verificado. */
public final class Sesion {

    private final String idUsuario;
    private final String correo;
    private final String accessToken;
    private final String nombre;
    private final String rol;

    public Sesion(String idUsuario, String correo, String accessToken, String nombre, String rol) {
        this.idUsuario = idUsuario;
        this.correo = correo;
        this.accessToken = accessToken;
        this.nombre = nombre;
        this.rol = rol;
    }

    public String getIdUsuario() {
        return idUsuario;
    }

    public String getCorreo() {
        return correo;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getNombre() {
        return nombre;
    }

    public String getRol() {
        return rol;
    }
}
