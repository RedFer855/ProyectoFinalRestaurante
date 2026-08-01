package com.example.proyectofinalrestaurante.data.sync;

/**
 * Lo que el {@link SyncWorker} necesita saber de un módulo para sincronizarlo: una pasada
 * que drena su parte del outbox y baja su delta.
 *
 * <p>Existe para que el worker sea <b>uno solo</b>. La regla 3 de
 * {@code Offline-First con Room y Outbox} lo pide explícitamente: un {@code SyncWorker}
 * único, para que nunca haya dos drenando la misma cola en paralelo. Sin esta interfaz, cada
 * módulo nuevo tentaría con un worker propio.</p>
 */
public interface Sincronizador {

    /** Nombre del módulo, para los mensajes de error y el diagnóstico. */
    String modulo();

    /** Una pasada completa: drenar el outbox del módulo y bajar su delta. */
    ResultadoSync sincronizar();
}
