package com.example.proyectofinalrestaurante;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.proyectofinalrestaurante.domain.model.TipoPedido;

import org.junit.Test;

/** Tests de {@link TipoPedido} (Plan Fase 3b, E2). */
public class TipoPedidoTest {

    @Test
    public void porId_mapeaLosConocidos() {
        assertEquals(TipoPedido.EN_MESA, TipoPedido.porId(1));
        assertEquals(TipoPedido.PARA_LLEVAR, TipoPedido.porId(2));
    }

    @Test
    public void porId_idDesconocido_devuelveNull() {
        assertNull(TipoPedido.porId(99));
        assertNull(TipoPedido.porId(0));
    }

    @Test
    public void getters_devuelvenElIdDelCatalogo() {
        assertEquals(1, TipoPedido.EN_MESA.getId());
        assertEquals(2, TipoPedido.PARA_LLEVAR.getId());
    }
}