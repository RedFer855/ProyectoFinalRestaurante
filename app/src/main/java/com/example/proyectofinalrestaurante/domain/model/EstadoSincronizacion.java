package com.example.proyectofinalrestaurante.domain.model;

import androidx.annotation.Nullable;

/**
 * Estado global de la sincronización (Plan Fase 2b, §4.5). Inmutable.
 *
 * <p>Es lo que alimenta el indicador global de la pantalla de Menú: un {@code sincronizando}
 * mientras el {@code SyncWorker} está corriendo y, cuando algo se cae de forma permanente,
 * un {@code ultimoError} para mostrarle al usuario que algunos cambios no llegaron.</p>
 *
 * <p>Los "N cambios sin subir" no viajan acá: se derivan en la UI contando las filas cuyo
 * {@code estadoSync} no es {@code SINCRONIZADO}, así no hay dos fuentes de verdad.</p>
 */
public final class EstadoSincronizacion {

    private final boolean sincronizando;
    @Nullable private final String ultimoError;

    public EstadoSincronizacion(boolean sincronizando, @Nullable String ultimoError) {
        this.sincronizando = sincronizando;
        this.ultimoError = ultimoError;
    }

    public boolean isSincronizando() {
        return sincronizando;
    }

    @Nullable
    public String getUltimoError() {
        return ultimoError;
    }
}
