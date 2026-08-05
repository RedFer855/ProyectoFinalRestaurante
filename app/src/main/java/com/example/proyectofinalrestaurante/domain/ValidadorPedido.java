package com.example.proyectofinalrestaurante.domain;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.domain.model.Carrito;
import com.example.proyectofinalrestaurante.domain.model.LineaCarrito;
import com.example.proyectofinalrestaurante.domain.model.NuevoPedido;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Validación de un pedido antes de encolarlo (Plan Fase 3b, §6.1). Audience: devolver
 * <b>qué</b> está mal, no el texto del error — el mensaje sale de {@code strings.xml} en
 * {@code ui}. Java puro y sin estado, mismo molde que {@link ValidadorCliente}.
 *
 * <p>El tope de líneas es el que también impone el RPC (Plan Fase 3b, §10): sin tope, el
 * payload de un carrito de mesa de 20 personas crecería sin límite en {@code payload_json}.</p>
 */
public final class ValidadorPedido {

    /** El máximo de líneas que admite un pedido; igual que el tope del RPC (§10). */
    public static final int MAX_NUM_LINEAS = 50;

    public enum ErrorPedido {
        CARRITO_VACIO,
        DEMASIADAS_LINEAS,
        CANTIDAD_INVALIDA
    }

    private ValidadorPedido() {
    }

    public static Set<ErrorPedido> validar(@Nullable NuevoPedido nuevo) {
        if (nuevo == null) {
            return EnumSet.of(ErrorPedido.CARRITO_VACIO);
        }
        Carrito carrito = nuevo.getCarrito();
        if (carrito == null || carrito.estaVacio()) {
            return EnumSet.of(ErrorPedido.CARRITO_VACIO);
        }
        Set<ErrorPedido> errores = EnumSet.noneOf(ErrorPedido.class);
        if (carrito.cantidadItems() > MAX_NUM_LINEAS) {
            errores.add(ErrorPedido.DEMASIADAS_LINEAS);
        }
        for (LineaCarrito linea : carrito.getLineas()) {
            if (linea.getCantidad() <= 0) {
                errores.add(ErrorPedido.CANTIDAD_INVALIDA);
                break;
            }
        }
        return errores.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(errores);
    }

    public static boolean esValido(@Nullable NuevoPedido nuevo) {
        return validar(nuevo).isEmpty();
    }
}