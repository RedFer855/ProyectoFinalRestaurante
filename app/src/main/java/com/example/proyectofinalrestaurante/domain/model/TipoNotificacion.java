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
    ERROR_SYNC,

    /**
     * Al drenar un {@code CREAR_PEDIDO}, el {@code cliente_id_local} referenciado había sido
     * descartado por error permanente (Plan Fase 3b, §4.2, B4): el pedido subió sin
     * {@code id_cliente} (null) y el usuario tiene que enterarse de que el dato accesorio
     * se perdió para no degradar la transacción.
     */
    PEDIDO_SIN_CLIENTE,

    /**
     * Al drenar un {@code CREAR_PEDIDO}, la {@code mesa_id_local} referenciada no tenía
     * {@code idServidor} (Plan Fase 3b, §4.2): el pedido subió con {@code id_mesa = NULL} y
     * la notificación avisa que ese dato se degradó. La regla de fondo: un pedido que no sube
     * es peor que un pedido sin mesa.
     */
    PEDIDO_SIN_MESA
}
