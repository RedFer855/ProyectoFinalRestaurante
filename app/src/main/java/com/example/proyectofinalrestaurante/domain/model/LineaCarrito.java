package com.example.proyectofinalrestaurante.domain.model;

import androidx.annotation.Nullable;

/**
 * Una línea del carrito de un pedido en curso (Plan Fase 3b, §6.1). Inmutable.
 *
 * <p>Guarda los dos ids del platillo —el local (PK de Room) y el del servidor— porque el
 * payload de {@code crear_pedido} manda {@code id_platillo} y el carrito se arma con
 * platillos que pueden no haberse subido aún ({@code idServidor == null}, ver {@link Platillo}).
 * {@code precioEstimado} es lo que muestra el total sin red: el servidor sella el precio real
 * (Plan Fase 3b, §2.3 / ADR-010).</p>
 */
public final class LineaCarrito {

    private final int idLocalPlatillo;
    @Nullable private final Integer idServidorPlatillo;
    private final String nombre;
    private final double precioEstimado;
    private final int cantidad;

    public LineaCarrito(int idLocalPlatillo, @Nullable Integer idServidorPlatillo,
                        String nombre, double precioEstimado, int cantidad) {
        this.idLocalPlatillo = idLocalPlatillo;
        this.idServidorPlatillo = idServidorPlatillo;
        this.nombre = nombre;
        this.precioEstimado = precioEstimado;
        this.cantidad = cantidad;
    }

    public int getIdLocalPlatillo() {
        return idLocalPlatillo;
    }

    @Nullable
    public Integer getIdServidorPlatillo() {
        return idServidorPlatillo;
    }

    public String getNombre() {
        return nombre;
    }

    public double getPrecioEstimado() {
        return precioEstimado;
    }

    public int getCantidad() {
        return cantidad;
    }

    /** Estimación, derivada: el servidor sella el precio real al crear (ADR-010). */
    public double subtotal() {
        return precioEstimado * cantidad;
    }

    /** Copia con otra cantidad. Inmutable. */
    public LineaCarrito conCantidad(int nuevaCantidad) {
        return new LineaCarrito(idLocalPlatillo, idServidorPlatillo, nombre, precioEstimado,
                nuevaCantidad);
    }
}
