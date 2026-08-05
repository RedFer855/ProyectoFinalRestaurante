package com.example.proyectofinalrestaurante.data.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * {@link SenalDeCambio} es un valor inmutable y sin dependencias de Android (Plan Fase 3, §4.1).
 */
public class SenalDeCambioTest {

    @Test
    public void pedidos_reporteElModuloPedidos() {
        assertEquals(SenalDeCambio.MODULO_PEDIDOS, SenalDeCambio.pedidos().getModulo());
        assertEquals("PEDIDOS", SenalDeCambio.pedidos().getModulo());
    }

    @Test
    public void mismaSenal_sonIguales() {
        assertEquals(SenalDeCambio.pedidos(), SenalDeCambio.pedidos());
        assertEquals(SenalDeCambio.pedidos().hashCode(), SenalDeCambio.pedidos().hashCode());
    }

    @Test
    public void distintoModulo_noSonIguales() {
        assertNotEquals(SenalDeCambio.pedidos(), SenalDeCambio.deModulo("MESAS"));
    }

    @Test
    public void toString_esEstable() {
        assertTrue(SenalDeCambio.pedidos().toString().contains("PEDIDOS"));
    }
}