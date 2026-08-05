package com.example.proyectofinalrestaurante.ui.detallepedido;

import com.example.proyectofinalrestaurante.domain.model.LineaPedido;

import java.util.Collections;
import java.util.List;

/**
 * Estado único e inmutable del detalle de un pedido (Plan Fase 3b). Mismo patrón que
 * {@code EstadoBuzon}: transporta la lista de {@link LineaPedido} que baja {@code DetallePedidoHoja}
 * bajo demanda. No hay bandera de "cargando" ni de "vacío" suelta: ambas son derivadas de la lista.
 */
public final class EstadoDetallePedido {

    private final List<LineaPedido> lineas;

    /** Transporta la lista visible; {@code null} y vacío se ven igual (lista vacía). */
    EstadoDetallePedido(List<LineaPedido> lineas) {
        this.lineas = lineas == null
                ? Collections.emptyList() : Collections.unmodifiableList(lineas);
    }

    public List<LineaPedido> getLineas() {
        return lineas;
    }

    public boolean isVacio() {
        return lineas.isEmpty();
    }

    /** Suma de los subtotales de las líneas; la estimación local del total del pedido. */
    public double getTotal() {
        double total = 0;
        for (LineaPedido linea : lineas) {
            total += linea.subtotal();
        }
        return total;
    }
}