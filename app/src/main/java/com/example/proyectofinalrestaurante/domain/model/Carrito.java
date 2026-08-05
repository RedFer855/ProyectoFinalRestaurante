package com.example.proyectofinalrestaurante.domain.model;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Carrito de un pedido en curso (Plan Fase 3b, §6.1). <b>Inmutable</b>: cada operación
 * devuelve un {@code Carrito} nuevo y jamás muta el original; las operaciones que no cambian
 * nada devuelven {@code this}.
 *
 * <p><b>Fusiona líneas del mismo platillo (B6)</b>: agregar dos veces el mismo platillo da
 * una línea con {@code cantidad = 2}, nunca dos líneas. La identidad de una línea es el
 * {@code idLocalPlatillo} — el id de Room, no el del servidor, porque un platillo puede
 * no haberse subido todavía.</p>
 */
public final class Carrito {

    private static final Carrito VACIO = new Carrito(Collections.<LineaCarrito>emptyList());

    private final List<LineaCarrito> lineas;

    private Carrito(List<LineaCarrito> lineas) {
        this.lineas = Collections.unmodifiableList(lineas);
    }

    /** Carrito sin líneas. Se reusa la misma instancia inmutable en cada {@code vacio()}. */
    public static Carrito vacio() {
        return VACIO;
    }

    public List<LineaCarrito> getLineas() {
        return lineas;
    }

    public boolean estaVacio() {
        return lineas.isEmpty();
    }

    /** Cantidad de líneas (no de ítems): un platillo repetido es una sola línea. */
    public int cantidadItems() {
        return lineas.size();
    }

    /** Total estimado del carrito, sumando los subtotales de cada línea (ADR-010). */
    public double total() {
        double total = 0;
        for (LineaCarrito linea : lineas) {
            total += linea.subtotal();
        }
        return total;
    }

    /**
     * Agrega un platillo (suma 1 a su línea si ya está). Devuelve un {@code Carrito} nuevo.
     * Un platillo {@code null} no hace nada.
     */
    public Carrito con(@Nullable Platillo platillo) {
        if (platillo == null) {
            return this;
        }
        LineaCarrito existente = encontrarLinea(platillo.getIdLocal());
        if (existente == null) {
            return anadir(new LineaCarrito(platillo.getIdLocal(), platillo.getIdServidor(),
                    platillo.getNombre(), platillo.getPrecio(), 1));
        }
        return reemplazar(existente.conCantidad(existente.getCantidad() + 1));
    }

    /**
     * Fija la cantidad de un platillo a {@code n}. Una cantidad {@code <= 0} elimina la
     * línea (B6). Devuelve {@code this} si el platillo no está o {@code n} no cambia el valor.
     */
    public Carrito conCantidad(int idLocalPlatillo, int n) {
        LineaCarrito linea = encontrarLinea(idLocalPlatillo);
        if (linea == null || linea.getCantidad() == n) {
            return this;
        }
        if (n <= 0) {
            return sinPlatillo(idLocalPlatillo);
        }
        return reemplazar(linea.conCantidad(n));
    }

    /**
     * Quita un platillo del carrito por su id local. Si no está, devuelve {@code this}.
     */
    public Carrito sinPlatillo(int idLocalPlatillo) {
        java.util.ArrayList<LineaCarrito> resultado = new java.util.ArrayList<>(lineas.size());
        LineaCarrito encontrada = null;
        for (LineaCarrito linea : lineas) {
            if (linea.getIdLocalPlatillo() == idLocalPlatillo) {
                encontrada = linea;
            } else {
                resultado.add(linea);
            }
        }
        return encontrada == null ? this : new Carrito(resultado);
    }

    // ------------------------------------------------------------------ helpers

    @Nullable
    private LineaCarrito encontrarLinea(int idLocalPlatillo) {
        for (LineaCarrito linea : lineas) {
            if (linea.getIdLocalPlatillo() == idLocalPlatillo) {
                return linea;
            }
        }
        return null;
    }

    private Carrito reemplazar(LineaCarrito nueva) {
        java.util.ArrayList<LineaCarrito> resultado = new java.util.ArrayList<>(lineas);
        for (int i = 0; i < resultado.size(); i++) {
            if (resultado.get(i).getIdLocalPlatillo() == nueva.getIdLocalPlatillo()) {
                resultado.set(i, nueva);
                return new Carrito(resultado);
            }
        }
        throw new IllegalStateException("La línea a reemplazar no está en el carrito");
    }

    private Carrito anadir(LineaCarrito linea) {
        java.util.ArrayList<LineaCarrito> resultado = new java.util.ArrayList<>(lineas);
        resultado.add(linea);
        return new Carrito(resultado);
    }
}