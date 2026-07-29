package com.example.proyectofinalrestaurante.domain.model;

/** Entidad de dominio: sesión de un usuario autenticado. */
public final class Sesion {

    private final String idUsuario;
    private final String correo;
    private final String accessToken;

    public Sesion(String idUsuario, String correo, String accessToken) {
        this.idUsuario = idUsuario;
        this.correo = correo;
        this.accessToken = accessToken;
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
}
