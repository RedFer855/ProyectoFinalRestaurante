package com.example.proyectofinalrestaurante;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.domain.Accion;
import com.example.proyectofinalrestaurante.domain.Modulo;
import com.example.proyectofinalrestaurante.domain.Permisos;

import org.junit.Test;

/**
 * Tests de la matriz de permisos (Plan Fase 1c, Entregable 1). Incluye los casos
 * negativos a propósito: lo que un rol NO puede hacer es tan importante como lo que sí.
 */
public class PermisosTest {

    // --- admin ---

    @Test
    public void adminPuedeVerTodosLosModulos() {
        for (Modulo modulo : Modulo.values()) {
            assertTrue("admin debería ver " + modulo,
                    Permisos.puede(Permisos.ROL_ADMIN, modulo, Accion.VER));
        }
        assertEquals(Modulo.values().length, Permisos.modulosVisibles(Permisos.ROL_ADMIN).size());
    }

    @Test
    public void soloAdminAdministraEmpleados() {
        assertTrue(Permisos.puede(Permisos.ROL_ADMIN, Modulo.EMPLEADOS, Accion.CREAR));
        assertFalse(Permisos.puede(Permisos.ROL_MESERO, Modulo.EMPLEADOS, Accion.CREAR));
        assertFalse(Permisos.puede(Permisos.ROL_COCINA, Modulo.EMPLEADOS, Accion.CREAR));
    }

    @Test
    public void soloAdminVeReportes() {
        assertTrue(Permisos.puede(Permisos.ROL_ADMIN, Modulo.REPORTES, Accion.VER));
        assertFalse(Permisos.puede(Permisos.ROL_MESERO, Modulo.REPORTES, Accion.VER));
        assertFalse(Permisos.puede(Permisos.ROL_COCINA, Modulo.REPORTES, Accion.VER));
    }

    // --- mesero ---

    @Test
    public void meseroVeCincoModulos() {
        assertEquals(5, Permisos.modulosVisibles(Permisos.ROL_MESERO).size());
    }

    @Test
    public void meseroConsultaElMenuPeroNoLoModifica() {
        assertTrue(Permisos.puede(Permisos.ROL_MESERO, Modulo.MENU, Accion.VER));
        assertFalse(Permisos.puede(Permisos.ROL_MESERO, Modulo.MENU, Accion.CREAR));
        assertFalse(Permisos.puede(Permisos.ROL_MESERO, Modulo.MENU, Accion.EDITAR));
        assertFalse(Permisos.puede(Permisos.ROL_MESERO, Modulo.MENU, Accion.ELIMINAR));
    }

    @Test
    public void meseroCreaYEditaPedidosPeroNoLosCancela() {
        assertTrue(Permisos.puede(Permisos.ROL_MESERO, Modulo.PEDIDOS, Accion.CREAR));
        assertTrue(Permisos.puede(Permisos.ROL_MESERO, Modulo.PEDIDOS, Accion.EDITAR));
        assertFalse(Permisos.puede(Permisos.ROL_MESERO, Modulo.PEDIDOS, Accion.ELIMINAR));
    }

    @Test
    public void meseroCambiaEstadoDeMesaPeroNoCreaMesas() {
        assertTrue(Permisos.puede(Permisos.ROL_MESERO, Modulo.MESAS, Accion.CAMBIAR_ESTADO));
        assertFalse(Permisos.puede(Permisos.ROL_MESERO, Modulo.MESAS, Accion.CREAR));
        assertFalse(Permisos.puede(Permisos.ROL_MESERO, Modulo.MESAS, Accion.ELIMINAR));
    }

    @Test
    public void meseroNoEliminaClientes() {
        assertTrue(Permisos.puede(Permisos.ROL_MESERO, Modulo.CLIENTES, Accion.CREAR));
        assertFalse(Permisos.puede(Permisos.ROL_MESERO, Modulo.CLIENTES, Accion.ELIMINAR));
    }

    // --- cocina ---

    @Test
    public void cocinaVeTresModulos() {
        assertEquals(3, Permisos.modulosVisibles(Permisos.ROL_COCINA).size());
        assertTrue(Permisos.puede(Permisos.ROL_COCINA, Modulo.INICIO, Accion.VER));
        assertTrue(Permisos.puede(Permisos.ROL_COCINA, Modulo.MENU, Accion.VER));
        assertTrue(Permisos.puede(Permisos.ROL_COCINA, Modulo.PEDIDOS, Accion.VER));
    }

    @Test
    public void cocinaAvanzaEstadoDePedidoPeroNoLosCrea() {
        assertTrue(Permisos.puede(Permisos.ROL_COCINA, Modulo.PEDIDOS, Accion.CAMBIAR_ESTADO));
        assertFalse(Permisos.puede(Permisos.ROL_COCINA, Modulo.PEDIDOS, Accion.CREAR));
        assertFalse(Permisos.puede(Permisos.ROL_COCINA, Modulo.PEDIDOS, Accion.EDITAR));
    }

    @Test
    public void cocinaNoTocaMesasNiClientes() {
        assertFalse(Permisos.puede(Permisos.ROL_COCINA, Modulo.MESAS, Accion.VER));
        assertFalse(Permisos.puede(Permisos.ROL_COCINA, Modulo.CLIENTES, Accion.VER));
    }

    // --- casos límite: se niega por defecto ---

    @Test
    public void rolDesconocidoSoloVeInicio() {
        assertTrue(Permisos.puede("cajero", Modulo.INICIO, Accion.VER));
        assertFalse(Permisos.puede("cajero", Modulo.PEDIDOS, Accion.VER));
        assertEquals(1, Permisos.modulosVisibles("cajero").size());
    }

    @Test
    public void rolNuloNoPuedeNada() {
        for (Modulo modulo : Modulo.values()) {
            for (Accion accion : Accion.values()) {
                assertFalse(Permisos.puede(null, modulo, accion));
            }
        }
        assertTrue(Permisos.modulosVisibles(null).isEmpty());
    }

    @Test
    public void elRolNoDistingueMayusculas() {
        assertTrue(Permisos.puede("ADMIN", Modulo.EMPLEADOS, Accion.CREAR));
        assertTrue(Permisos.puede("Mesero", Modulo.PEDIDOS, Accion.CREAR));
    }

    @Test
    public void moduloNuloNoOtorgaNingunaAccion() {
        assertTrue(Permisos.accionesDe(Permisos.ROL_ADMIN, null).isEmpty());
    }
}
