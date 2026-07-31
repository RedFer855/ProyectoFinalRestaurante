package com.example.proyectofinalrestaurante.core;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.domain.model.Sesion;

/**
 * Holder en memoria de la sesión activa (Plan Fase 1b, Entregable 5).
 * Se pierde al cerrar la app — la persistencia cifrada es P-009, otro trabajo.
 * Existe para que el rol/nombre de la sesión esté disponible después del login.
 */
public final class SesionActual {

    private static volatile Sesion sesion;

    private SesionActual() {
    }

    public static void guardar(Sesion nuevaSesion) {
        sesion = nuevaSesion;
    }

    @Nullable
    public static Sesion obtener() {
        return sesion;
    }

    public static void limpiar() {
        sesion = null;
    }
}
