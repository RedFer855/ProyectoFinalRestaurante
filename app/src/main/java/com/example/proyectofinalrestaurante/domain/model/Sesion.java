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

    /**
     * Copia de la sesión con otro rol. La usa el selector de rol de debug para
     * previsualizar la app como otro usuario sin volver a autenticarse.
     *
     * <p>Cambiar el rol acá <b>no otorga ningún acceso</b>: las policies RLS de Postgres
     * leen el rol de la base con el JWT del usuario, no de lo que diga el cliente.</p>
     */
    public Sesion conRol(String nuevoRol) {
        return new Sesion(idUsuario, correo, accessToken, nombre, nuevoRol);
    }
}
