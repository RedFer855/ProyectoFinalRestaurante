package com.example.proyectofinalrestaurante.ui.login;

import com.example.proyectofinalrestaurante.domain.model.Sesion;

/**
 * Objeto de estado único de la pantalla de login (ver contexto/20 - Patrones/
 * MVVM en Android (ViewModel + LiveData).md — evita LiveData sueltas por campo).
 */
public final class EstadoLogin {

    private final boolean cargando;
    private final String error;
    private final Sesion sesion;
    private final boolean sesionConsumida;

    private EstadoLogin(boolean cargando, String error, Sesion sesion, boolean sesionConsumida) {
        this.cargando = cargando;
        this.error = error;
        this.sesion = sesion;
        this.sesionConsumida = sesionConsumida;
    }

    public static EstadoLogin inicial() {
        return new EstadoLogin(false, null, null, true);
    }

    public static EstadoLogin cargando() {
        return new EstadoLogin(true, null, null, true);
    }

    public static EstadoLogin error(String mensaje) {
        return new EstadoLogin(false, mensaje, null, true);
    }

    public static EstadoLogin exito(Sesion sesion) {
        return new EstadoLogin(false, null, sesion, false);
    }

    public boolean isCargando() {
        return cargando;
    }

    public String getError() {
        return error;
    }

    public Sesion getSesion() {
        return sesion;
    }

    /**
     * La navegación es un evento de un solo disparo modelado como estado (ver P-013 en
     * Deuda Técnica - Pendientes): sin este chequeo, un estado con sesión != null que se
     * vuelve a observar (p. ej. tras recrearse la Activity) navegaría de nuevo.
     */
    public boolean debeNavegar() {
        return sesion != null && !sesionConsumida;
    }

    /** Copia del estado actual marcando la sesión como ya consumida por la navegación. */
    public EstadoLogin sesionConsumida() {
        return new EstadoLogin(cargando, error, sesion, true);
    }
}
