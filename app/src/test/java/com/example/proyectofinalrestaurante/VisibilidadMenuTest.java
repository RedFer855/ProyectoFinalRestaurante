package com.example.proyectofinalrestaurante;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.domain.Modulo;
import com.example.proyectofinalrestaurante.domain.VisibilidadMenu;

import org.junit.Test;

/**
 * Tests de la visibilidad del menú por rol. Desde el Plan Fase 1c (E1),
 * {@code VisibilidadMenu} delega en {@code Permisos} — estos tests verifican que esa
 * delegación no cambió el comportamiento observable del menú lateral.
 */
public class VisibilidadMenuTest {

    @Test
    public void adminVeTodo() {
        for (Modulo modulo : Modulo.values()) {
            assertTrue(VisibilidadMenu.esVisible(VisibilidadMenu.ROL_ADMIN, modulo));
        }
    }

    @Test
    public void meseroNoVeEmpleadosNiReportes() {
        assertFalse(VisibilidadMenu.esVisible(VisibilidadMenu.ROL_MESERO, Modulo.EMPLEADOS));
        assertFalse(VisibilidadMenu.esVisible(VisibilidadMenu.ROL_MESERO, Modulo.REPORTES));
        assertTrue(VisibilidadMenu.esVisible(VisibilidadMenu.ROL_MESERO, Modulo.MESAS));
        assertTrue(VisibilidadMenu.esVisible(VisibilidadMenu.ROL_MESERO, Modulo.CLIENTES));
    }

    /**
     * Cocina ve Inicio, Pedidos y <b>también Menú</b> — consulta el menú para saber qué
     * lleva un platillo. Cambio respecto de la Fase 1b, donde Menú le estaba oculto:
     * la matriz del Plan Fase 1c y el diseño aprobado coinciden en que sí debe verlo.
     */
    @Test
    public void cocinaVeInicioPedidosYMenu() {
        assertTrue(VisibilidadMenu.esVisible(VisibilidadMenu.ROL_COCINA, Modulo.INICIO));
        assertTrue(VisibilidadMenu.esVisible(VisibilidadMenu.ROL_COCINA, Modulo.PEDIDOS));
        assertTrue(VisibilidadMenu.esVisible(VisibilidadMenu.ROL_COCINA, Modulo.MENU));
        assertFalse(VisibilidadMenu.esVisible(VisibilidadMenu.ROL_COCINA, Modulo.MESAS));
        assertFalse(VisibilidadMenu.esVisible(VisibilidadMenu.ROL_COCINA, Modulo.CLIENTES));
        assertFalse(VisibilidadMenu.esVisible(VisibilidadMenu.ROL_COCINA, Modulo.EMPLEADOS));
        assertFalse(VisibilidadMenu.esVisible(VisibilidadMenu.ROL_COCINA, Modulo.REPORTES));
    }

    @Test
    public void rolDesconocidoSoloVeInicio() {
        assertTrue(VisibilidadMenu.esVisible("otro", Modulo.INICIO));
        assertFalse(VisibilidadMenu.esVisible("otro", Modulo.PEDIDOS));
    }

    @Test
    public void rolNuloNoVeNada() {
        for (Modulo modulo : Modulo.values()) {
            assertFalse(VisibilidadMenu.esVisible(null, modulo));
        }
    }
}
