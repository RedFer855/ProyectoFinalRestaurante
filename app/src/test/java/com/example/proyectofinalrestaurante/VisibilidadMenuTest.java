package com.example.proyectofinalrestaurante;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.domain.VisibilidadMenu;

import org.junit.Test;

/** Tests de la matriz de visibilidad del menú por rol (Plan Fase 1b, E6). */
public class VisibilidadMenuTest {

    @Test
    public void adminVeTodo() {
        for (VisibilidadMenu.Item item : VisibilidadMenu.Item.values()) {
            assertTrue(VisibilidadMenu.esVisible(VisibilidadMenu.ROL_ADMIN, item));
        }
    }

    @Test
    public void meseroNoVeEmpleadosNiReportes() {
        assertFalse(VisibilidadMenu.esVisible(VisibilidadMenu.ROL_MESERO, VisibilidadMenu.Item.EMPLEADOS));
        assertFalse(VisibilidadMenu.esVisible(VisibilidadMenu.ROL_MESERO, VisibilidadMenu.Item.REPORTES));
        assertTrue(VisibilidadMenu.esVisible(VisibilidadMenu.ROL_MESERO, VisibilidadMenu.Item.MESAS));
        assertTrue(VisibilidadMenu.esVisible(VisibilidadMenu.ROL_MESERO, VisibilidadMenu.Item.CLIENTES));
    }

    @Test
    public void cocinaSoloVeInicioYPedidos() {
        assertTrue(VisibilidadMenu.esVisible(VisibilidadMenu.ROL_COCINA, VisibilidadMenu.Item.INICIO));
        assertTrue(VisibilidadMenu.esVisible(VisibilidadMenu.ROL_COCINA, VisibilidadMenu.Item.PEDIDOS));
        assertFalse(VisibilidadMenu.esVisible(VisibilidadMenu.ROL_COCINA, VisibilidadMenu.Item.MESAS));
        assertFalse(VisibilidadMenu.esVisible(VisibilidadMenu.ROL_COCINA, VisibilidadMenu.Item.MENU));
        assertFalse(VisibilidadMenu.esVisible(VisibilidadMenu.ROL_COCINA, VisibilidadMenu.Item.CLIENTES));
        assertFalse(VisibilidadMenu.esVisible(VisibilidadMenu.ROL_COCINA, VisibilidadMenu.Item.EMPLEADOS));
        assertFalse(VisibilidadMenu.esVisible(VisibilidadMenu.ROL_COCINA, VisibilidadMenu.Item.REPORTES));
    }

    @Test
    public void rolDesconocidoSoloVeInicio() {
        assertTrue(VisibilidadMenu.esVisible("otro", VisibilidadMenu.Item.INICIO));
        assertFalse(VisibilidadMenu.esVisible("otro", VisibilidadMenu.Item.PEDIDOS));
    }

    @Test
    public void rolNuloNoVeNada() {
        for (VisibilidadMenu.Item item : VisibilidadMenu.Item.values()) {
            assertFalse(VisibilidadMenu.esVisible(null, item));
        }
    }
}
