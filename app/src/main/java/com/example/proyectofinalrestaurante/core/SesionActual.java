package com.example.proyectofinalrestaurante.core;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.domain.model.Sesion;

/**
 * Holder en memoria de la sesión activa (Plan Fase 1b, Entregable 5).
 *
 * <p>Sigue siendo <b>solo el caché en memoria</b> — se pierde al matar el proceso. Lo que
 * cambió con P-009 es que ya no queda vacío al reabrir la app:
 * {@code SyncApplication.onCreate()} lo hidrata desde {@code SesionRepository} (persistencia
 * cifrada con Android Keystore, ver {@code AlmacenSeguro}) antes de que cualquier otra cosa
 * lo consulte. {@code ProveedorDeToken} lee y escribe acá en cada refresh, así que el valor
 * siempre refleja la sesión vigente sin tener que ir a disco.</p>
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
