package com.example.proyectofinalrestaurante.domain.model;

import androidx.annotation.Nullable;

/**
 * Pedido del tablero en tiempo real (Plan Fase 3, §4.4). Inmutable.
 *
 * <p>Misma identidad que el resto de los módulos local-first: {@code idLocal} es la PK de
 * Room y {@code idServidor} es el {@code id_pedido} del servidor, {@code null} mientras la
 * fila no se haya subido (en la Fase 3 no se crean pedidos desde el cliente, así que en la
 * práctica los pedidos llegan siempre con {@code idServidor}).</p>
 *
 * <p>{@code estado} es {@code null} únicamente cuando el servidor trae un
 * {@code id_estado_pedido} que este APK todavía no conoce (catálogo más nuevo que el
 * cliente): ver {@link EstadoPedido#porId(int)}. La UI debe mostrarlo como estado
 * desconocido y no ofrecer cambiarlo a ciegas.</p>
 *
 * <p>{@code idAuthUsuario} es el {@code uuid} de {@code auth.users} del empleado que tomó
 * el pedido, expuesto por {@code vista_pedidos}: es lo que permite al buzón avisar a "el
 * mesero que tomó el pedido" comparando strings en memoria, sin una consulta extra.</p>
 */
public final class Pedido {

    private final long idLocal;
    @Nullable private final Integer idServidor;
    /** {@code fecha} del servidor, en ISO 8601 con zona (la vista lo trae así). */
    private final String fecha;
    @Nullable private final EstadoPedido estado;
    /** Número de la mesa asignada; {@code null} en los pedidos "para llevar". */
    @Nullable private final Integer numeroMesa;
    @Nullable private final String cliente;
    private final double total;
    private final int cantidadItems;
    @Nullable private final String idAuthUsuario;
    @Nullable private final String actualizadoEn;
    private final EstadoSync estadoSync;

    public Pedido(long idLocal, @Nullable Integer idServidor, String fecha,
                  @Nullable EstadoPedido estado, @Nullable Integer numeroMesa,
                  @Nullable String cliente, double total, int cantidadItems,
                  @Nullable String idAuthUsuario, @Nullable String actualizadoEn,
                  EstadoSync estadoSync) {
        this.idLocal = idLocal;
        this.idServidor = idServidor;
        this.fecha = fecha;
        this.estado = estado;
        this.numeroMesa = numeroMesa;
        this.cliente = cliente;
        this.total = total;
        this.cantidadItems = cantidadItems;
        this.idAuthUsuario = idAuthUsuario;
        this.actualizadoEn = actualizadoEn;
        this.estadoSync = estadoSync;
    }

    public long getIdLocal() {
        return idLocal;
    }

    @Nullable
    public Integer getIdServidor() {
        return idServidor;
    }

    public String getFecha() {
        return fecha;
    }

    @Nullable
    public EstadoPedido getEstado() {
        return estado;
    }

    @Nullable
    public Integer getNumeroMesa() {
        return numeroMesa;
    }

    @Nullable
    public String getCliente() {
        return cliente;
    }

    public double getTotal() {
        return total;
    }

    public int getCantidadItems() {
        return cantidadItems;
    }

    @Nullable
    public String getIdAuthUsuario() {
        return idAuthUsuario;
    }

    @Nullable
    public String getActualizadoEn() {
        return actualizadoEn;
    }

    public EstadoSync getEstadoSync() {
        return estadoSync;
    }

    /**
     * ¿Es un pedido de cierre ({@code Entregado} o {@code Cancelado})? Un pedido cerrado no
     * avanza de estado: el RPC del servidor lo rechaza, así que la UI no ofrece avanzarlo.
     */
    public boolean estaCerrado() {
        return estado == EstadoPedido.ENTREGADO || estado == EstadoPedido.CANCELADO;
    }

    /**
     * Copia con otro estado. Inmutable, para que {@code DiffUtil} detecte el cambio.
     */
    public Pedido conEstado(EstadoPedido nuevo) {
        return new Pedido(idLocal, idServidor, fecha, nuevo, numeroMesa, cliente, total,
                cantidadItems, idAuthUsuario, actualizadoEn, estadoSync);
    }
}
