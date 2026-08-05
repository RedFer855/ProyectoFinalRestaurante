package com.example.proyectofinalrestaurante;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.domain.ValidadorPedido;
import com.example.proyectofinalrestaurante.domain.ValidadorPedido.ErrorPedido;
import com.example.proyectofinalrestaurante.domain.model.Carrito;
import com.example.proyectofinalrestaurante.domain.model.NuevoPedido;
import com.example.proyectofinalrestaurante.domain.model.Platillo;
import com.example.proyectofinalrestaurante.domain.model.TipoPedido;

import org.junit.Test;

import java.util.Set;

/** Tests de {@link ValidadorPedido} (Plan Fase 3b, E2): carrito vacío, tope de líneas. */
public class ValidadorPedidoTest {

    private static final TipoPedido TIPO = TipoPedido.EN_MESA;

    private static Platillo platillo(int id) {
        return new Platillo(id, id + 1000, "Baleada " + id, "Rica", 45.00, 1,
                "Platos fuertes", null, true,
                com.example.proyectofinalrestaurante.domain.model.EstadoSync.SINCRONIZADO);
    }

    private static NuevoPedido pedido(int numeroLineas) {
        Carrito carrito = Carrito.vacio();
        for (int i = 1; i <= numeroLineas; i++) {
            carrito = carrito.con(platillo(i));
        }
        return new NuevoPedido(carrito, 1, null, TIPO, "clave-1");
    }

    @Test
    public void pedidoValido_esValido() {
        assertTrue(ValidadorPedido.validar(pedido(3)).isEmpty());
        assertTrue(ValidadorPedido.esValido(pedido(3)));
    }

    @Test
    public void pedidoConCarritoVacio_carritoVacio() {
        NuevoPedido vacio = new NuevoPedido(Carrito.vacio(), 1, null, TIPO, "clave");
        Set<ErrorPedido> errores = ValidadorPedido.validar(vacio);
        assertTrue(errores.contains(ErrorPedido.CARRITO_VACIO));
        assertFalse(ValidadorPedido.esValido(vacio));
    }

    @Test
    public void pedidoNulo_carritoVacio() {
        assertTrue(ValidadorPedido.validar(null).contains(ErrorPedido.CARRITO_VACIO));
        assertFalse(ValidadorPedido.esValido(null));
    }

    @Test
    public void masDeCincuentaLineas_demasiadasLineas() {
        Set<ErrorPedido> errores = ValidadorPedido.validar(pedido(ValidadorPedido.MAX_NUM_LINEAS + 1));
        assertTrue(errores.contains(ErrorPedido.DEMASIADAS_LINEAS));
        assertFalse(ValidadorPedido.esValido(pedido(ValidadorPedido.MAX_NUM_LINEAS + 1)));
    }

    @Test
    public void exactamenteCincuentaLineas_esValido() {
        assertTrue(ValidadorPedido.esValido(pedido(ValidadorPedido.MAX_NUM_LINEAS)));
    }
}