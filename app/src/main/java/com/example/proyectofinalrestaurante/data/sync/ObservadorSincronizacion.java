package com.example.proyectofinalrestaurante.data.sync;

import androidx.annotation.Nullable;

/**
 * El estado global de la sincronización lo ve la UI (indicador "sincronizando…" y
 * {@code ultimoError}) a través de {@code EstadoSincronizacion} (Plan Fase 2b, §4.5). La
 * implementación concreta la expone el repositorio del Menú; el worker le avisa acá.
 *
 * <p>Separado a propósito: el {@link MenuSyncWorker} no debe depender del repositorio ni de
 * la UI, y el repositorio no debe saber de WorkManager.</p>
 */
public interface ObservadorSincronizacion {

    ObservadorSincronizacion NINGUNO = new ObservadorSincronizacion() {
        @Override
        public void alIniciar() {
        }

        @Override
        public void alTerminar(@Nullable String ultimoError) {
        }
    };

    /** El worker va a arrancar la pasada. */
    void alIniciar();

    /**
     * La pasada terminó. {@code ultimoError} no nulo = algo se cayó de forma permanente
     * (o el delta perdió un cambio local) y el usuario debe enterarse.
     */
    void alTerminar(@Nullable String ultimoError);
}
