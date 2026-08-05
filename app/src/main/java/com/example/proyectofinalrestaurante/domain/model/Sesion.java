package com.example.proyectofinalrestaurante.domain.model;

import androidx.annotation.Nullable;

/**
 * Entidad de dominio: sesión de un usuario autenticado, con su perfil ya verificado.
 *
 * <p>Desde P-009 transporta también lo necesario para persistirse y refrescarse sola:
 * {@code refreshToken} y {@code expiraEnMillis} (instante absoluto, no duración — Supabase
 * manda {@code expires_in} en segundos relativos, y guardar la duración obligaría a saber
 * cuándo se leyó para que sirviera de algo). Ver {@code ProveedorDeToken} y
 * {@code AlmacenSeguro}.</p>
 */
public final class Sesion {

    private final String idUsuario;
    private final String correo;
    private final String accessToken;
    @Nullable
    private final String refreshToken;
    private final long expiraEnMillis;
    private final String nombre;
    private final String rol;

    public Sesion(String idUsuario, String correo, String accessToken,
                  @Nullable String refreshToken, long expiraEnMillis,
                  String nombre, String rol) {
        this.idUsuario = idUsuario;
        this.correo = correo;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiraEnMillis = expiraEnMillis;
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

    @Nullable
    public String getRefreshToken() {
        return refreshToken;
    }

    /** Instante absoluto (epoch millis) en que vence el {@code accessToken}. */
    public long getExpiraEnMillis() {
        return expiraEnMillis;
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
        return new Sesion(idUsuario, correo, accessToken, refreshToken, expiraEnMillis,
                nombre, nuevoRol);
    }

    /**
     * Copia de la sesión con el token renovado (P-009). La usa {@code ProveedorDeToken}
     * después de un refresh exitoso — el resto de los datos (usuario, rol) no cambia.
     */
    public Sesion conTokenRenovado(String nuevoAccessToken, @Nullable String nuevoRefreshToken,
                                   long nuevoExpiraEnMillis) {
        return new Sesion(idUsuario, correo, nuevoAccessToken, nuevoRefreshToken,
                nuevoExpiraEnMillis, nombre, rol);
    }
}
