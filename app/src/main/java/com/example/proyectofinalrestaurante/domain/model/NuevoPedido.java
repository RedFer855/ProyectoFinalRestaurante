package com.example.proyectofinalrestaurante.domain.model;

import androidx.annotation.Nullable;

/**
 * Datos para crear un pedido desde la app (Plan Fase 3b, §6.1). Inmutable.
 *
 * <p>La mesa y el cliente son <b>opcionales</b> (ADR-006): un pedido "para llevar" puede
 * no llevar ninguno. Cuando vienen, viajan como ids <b>locales</b> ({@code idLocal}), porque
 * un cliente o una mesa pueden no haberse subido todavía; el {@code SincronizadorPedidos}
 * los resuelve a {@code id_servidor} al drenar (§4.2).</p>
 *
 * <p>{@code claveIdempotencia} la genera el dispositivo al crear el pedido: es lo que hace
 * idempotente al RPC {@code crear_pedido} — el reintento del outbox vuelve a mandar la misma
 * clave y el servidor devuelve el mismo {@code id_pedido} sin duplicar (§2).</p>
 */
public final class NuevoPedido {

    private final Carrito carrito;
    @Nullable private final Integer idLocalMesa;
    @Nullable private final Integer idLocalCliente;
    private final TipoPedido tipoPedido;
    private final String claveIdempotencia;

    public NuevoPedido(Carrito carrito, @Nullable Integer idLocalMesa,
                       @Nullable Integer idLocalCliente, TipoPedido tipoPedido,
                       String claveIdempotencia) {
        this.carrito = carrito;
        this.idLocalMesa = idLocalMesa;
        this.idLocalCliente = idLocalCliente;
        this.tipoPedido = tipoPedido;
        this.claveIdempotencia = claveIdempotencia;
    }

    public Carrito getCarrito() {
        return carrito;
    }

    @Nullable
    public Integer getIdLocalMesa() {
        return idLocalMesa;
    }

    @Nullable
    public Integer getIdLocalCliente() {
        return idLocalCliente;
    }

    public TipoPedido getTipoPedido() {
        return tipoPedido;
    }

    public String getClaveIdempotencia() {
        return claveIdempotencia;
    }
}