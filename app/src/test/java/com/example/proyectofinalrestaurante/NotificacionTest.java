package com.example.proyectofinalrestaurante;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.domain.model.Notificacion;
import com.example.proyectofinalrestaurante.domain.model.TipoNotificacion;

import org.junit.Test;

/**
 * Tests del modelo {@link Notificacion} (Plan Fase 3, E2).
 */
public class NotificacionTest {

    private static Notificacion nueva(boolean leida) {
        return new Notificacion(7, TipoNotificacion.PEDIDO_NUEVO, "cocina", null,
                "1042", 1000L, leida);
    }

    @Test
    public void comoLeida_devuelveCopiaInmutable() {
        Notificacion original = nueva(false);

        Notificacion leida = original.comoLeida();

        assertNotSame(original, leida);
        assertTrue(leida.isLeida());
        assertFalse(original.isLeida());
        assertEquals(TipoNotificacion.PEDIDO_NUEVO, leida.getTipo());
        assertEquals("cocina", leida.getRolDestino());
        assertEquals("1042", leida.getArg1());
    }

    @Test
    public void notificacionConDestinatarioAuth_noLlevaRol() {
        Notificacion lista = new Notificacion(8, TipoNotificacion.PEDIDO_LISTO, null,
                "uuid-del-mesero", "1042", 1000L, false);

        assertEquals(null, lista.getRolDestino());
        assertEquals("uuid-del-mesero", lista.getDestinatarioAuth());
    }
}
