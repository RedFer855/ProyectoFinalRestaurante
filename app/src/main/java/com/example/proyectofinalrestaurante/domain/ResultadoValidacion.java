package com.example.proyectofinalrestaurante.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Resultado de validar una contraseña: qué requisitos cumple y cuáles no, para
 * pintarlos en vivo en la UI de recuperación (ver Plan Fase 1b, Entregable 2).
 * Inmutable.
 */
public final class ResultadoValidacion {

    private final Set<RequisitoContrasenia> incumplidos;

    public ResultadoValidacion(Set<RequisitoContrasenia> incumplidos) {
        if (incumplidos == null || incumplidos.isEmpty()) {
            this.incumplidos = Collections.emptySet();
        } else {
            this.incumplidos = Collections.unmodifiableSet(EnumSet.copyOf(incumplidos));
        }
    }

    /** La contraseña cumple todos los requisitos. */
    public boolean esValida() {
        return incumplidos.isEmpty();
    }

    public boolean cumple(RequisitoContrasenia requisito) {
        return !incumplidos.contains(requisito);
    }

    /** Requisitos que la contraseña no cumple (inmodificable). */
    public Set<RequisitoContrasenia> getIncumplidos() {
        return incumplidos;
    }
}
