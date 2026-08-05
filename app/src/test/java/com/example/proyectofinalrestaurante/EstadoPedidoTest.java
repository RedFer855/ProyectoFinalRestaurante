package com.example.proyectofinalrestaurante;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.proyectofinalrestaurante.domain.model.EstadoPedido;

import org.junit.Test;

/**
 * Tests del catálogo {@link EstadoPedido} (Plan Fase 3, E2).
 */
public class EstadoPedidoTest {

    @Test
    public void idsDelCatalogo_coincidenConLaParteA() {
        // Mismos ids que inserta la Parte A en public.estado_pedido (Plan Fase 3, §2.1).
        assertEquals(1, EstadoPedido.PENDIENTE.getId());
        assertEquals(2, EstadoPedido.EN_PREPARACION.getId());
        assertEquals(3, EstadoPedido.LISTO.getId());
        assertEquals(4, EstadoPedido.ENTREGADO.getId());
        assertEquals(5, EstadoPedido.CANCELADO.getId());
    }

    @Test
    public void porId_estadoConocido() {
        assertEquals(EstadoPedido.LISTO, EstadoPedido.porId(3));
    }

    @Test
    public void porId_estadoDesconocido_devuelveNull() {
        // El servidor puede traer un estado más nuevo que este APK: mapearlo a un estado
        // conocido sería mentirle al cocinero (Plan Fase 3, §2.1).
        assertNull(EstadoPedido.porId(99));
    }

    @Test
    public void porId_cero_devuelveNull() {
        assertNull(EstadoPedido.porId(0));
    }

    @Test
    public void elFlujoDeCocinaEsContiguo() {
        // El RPC validó que cocina solo avanza de a uno (nuevo = actual + 1): el catálogo
        // tiene que ser contiguo del 1 al 4 para que esa cuenta funcione.
        assertEquals(EstadoPedido.PENDIENTE.getId() + 1, EstadoPedido.EN_PREPARACION.getId());
        assertEquals(EstadoPedido.EN_PREPARACION.getId() + 1, EstadoPedido.LISTO.getId());
        assertEquals(EstadoPedido.LISTO.getId() + 1, EstadoPedido.ENTREGADO.getId());
    }
}
