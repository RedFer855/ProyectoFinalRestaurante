package com.example.proyectofinalrestaurante.domain.model;

/**
 * Tipos de notificación del buzón (Plan Fase 3, §4.6). Local, derivado dentro del delta
 * del {@code SincronizadorPedidos} — no es una tabla del servidor.
 *
 * <p>{@code PEDIDO_NUEVO} y {@code PEDIDO_LISTO} se derivan al aplicar el delta (una fila
 * que no existía en Room, o una que pasó a Listo). {@code ERROR_SYNC} lo emite el
 * sincronizador cuando una operación del outbox se descarta por error permanente.</p>
 *
 * <p>Los textos no se guardan en la base (regla de oro #8, y el error de P-019): la fila
 * lleva el {@code tipo} y sus argumentos, y la UI resuelve {@code getString(...)}.</p>
 */
public enum TipoNotificacion {

    /** Un pedido entró al tablero. {@code rolDestino = "cocina"}. */
    PEDIDO_NUEVO,

    /** El pedido pasó a Listo. {@code destinatarioAuth} = el mesero que lo tomó. */
    PEDIDO_LISTO,

    /** Un cambio local no se pudo subir (error permanente del drenado). */
    ERROR_SYNC
}
