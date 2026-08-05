package com.example.proyectofinalrestaurante.domain.model;

import androidx.annotation.Nullable;

/**
 * Una línea del detalle de un pedido existente (Plan Fase 3b, §6.1). Inmutable.
 *
 * <p>Es una <b>lectura</b> del pedido ya tomado ({{@code DetallePedidoHoja}}): a diferencia
 * de {@link LineaCarrito}, que arma el alta, esto representa una fila de
 * {@code detalle_pedido} que ya está en el servidor — con {@code idServidor} del platillo
 * y el precio selleado por el servidor.</p>
 */
public final class LineaPedido {

    private final long idLocal;
    @Nullable private final Integer idServidor;
    private final String nombre;
    private final int cantidad;
    private final double precio;

    public LineaPedido(long idLocal, @Nullable Integer idServidor, String nombre,
                       int cantidad, double precio) {
        this.idLocal = idLocal;
        this.idServidor = idServidor;
        this.nombre = nombre;
        this.cantidad = cantidad;
        this.precio = precio;
    }

    public long getIdLocal() {
        return idLocal;
    }

    @Nullable
    public Integer getIdServidor() {
        return idServidor;
    }

    public String getNombre() {
        return nombre;
    }

    public int getCantidad() {
        return cantidad;
    }

    public double getPrecio() {
        return precio;
    }

    /** Subtotal de la línea. */
    public double subtotal() {
        return precio * cantidad;
    }
}