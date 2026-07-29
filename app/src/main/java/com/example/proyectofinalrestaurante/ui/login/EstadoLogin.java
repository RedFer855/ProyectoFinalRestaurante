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

    private EstadoLogin(boolean cargando, String error, Sesion sesion) {
        this.cargando = cargando;
        this.error = error;
        this.sesion = sesion;
    }

    public static EstadoLogin inicial() {
        return new EstadoLogin(false, null, null);
    }

    public static EstadoLogin cargando() {
        return new EstadoLogin(true, null, null);
    }

    public static EstadoLogin error(String mensaje) {
        return new EstadoLogin(false, mensaje, null);
    }

    public static EstadoLogin exito(Sesion sesion) {
        return new EstadoLogin(false, null, sesion);
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
}
