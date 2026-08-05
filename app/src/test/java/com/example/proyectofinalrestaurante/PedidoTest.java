package com.example.proyectofinalrestaurante;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.domain.model.EstadoPedido;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.Pedido;

import org.junit.Test;

/**
 * Tests del modelo {@link Pedido} (Plan Fase 3, E2).
 */
public class PedidoTest {

    private static Pedido pedido(EstadoPedido estado) {
        return new Pedido(1, 1042, "2026-08-04T12:05:00-06:00", estado, 4, "Ana Cruz",
                380.00, 3, "uuid-del-mesero", "2026-08-04T12:05:00-06:00",
                EstadoSync.SINCRONIZADO);
    }

    @Test
    public void pedidoAbierto_noEstaCerrado() {
        assertFalse(pedido(EstadoPedido.PENDIENTE).estaCerrado());
    }

    @Test
    public void pedidoEntregado_estaCerrado() {
        assertTrue(pedido(EstadoPedido.ENTREGADO).estaCerrado());
    }

    @Test
    public void pedidoCancelado_estaCerrado() {
        assertTrue(pedido(EstadoPedido.CANCELADO).estaCerrado());
    }

    @Test
    public void conEstado_devuelveCopiaInmutable() {
        Pedido original = pedido(EstadoPedido.PENDIENTE);

        Pedido cambiado = original.conEstado(EstadoPedido.EN_PREPARACION);

        assertNotSame(original, cambiado);
        assertEquals(EstadoPedido.PENDIENTE, original.getEstado());
        assertEquals(EstadoPedido.EN_PREPARACION, cambiado.getEstado());
        // El resto de los campos no se tocan.
        assertEquals(1042, (int) cambiado.getIdServidor());
        assertEquals(380.00, cambiado.getTotal(), 0.001);
    }

    @Test
    public void pedidoSinMesa_paraLaVistaEsNull() {
        // Un pedido "para llevar" no tiene mesa: la vista trae id_mesa null.
        Pedido paraLlevar = new Pedido(2, 1041, "2026-08-04T12:00:00-06:00",
                EstadoPedido.EN_PREPARACION, null, "Sofía Ramos", 130.00, 1, null,
                "2026-08-04T12:00:00-06:00", EstadoSync.SINCRONIZADO);

        assertNullPedido(paraLlevar);
    }

    private static void assertNullPedido(Pedido paraLlevar) {
        assertEquals(null, paraLlevar.getNumeroMesa());
    }
}
