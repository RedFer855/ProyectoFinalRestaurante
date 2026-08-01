package com.example.proyectofinalrestaurante;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.domain.Permisos;
import com.example.proyectofinalrestaurante.domain.ReglasEmpleado;
import com.example.proyectofinalrestaurante.domain.model.Empleado;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;

import org.junit.Test;

/**
 * Tests de las reglas del módulo Empleados (Plan Fase 1d, E3). Espejan el trigger
 * {@code proteger_admins()} del servidor; acá se verifica el lado del cliente.
 */
public class ReglasEmpleadoTest {

    private static final String UUID_ADMIN = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String UUID_OTRO_ADMIN = "bbbbbbbb-0000-0000-0000-000000000002";
    private static final String UUID_MESERO = "cccccccc-0000-0000-0000-000000000003";

    private static Empleado empleado(String idAuth, String rol) {
        return new Empleado(1, "Nombre", "Apellido", "0000", "9999", "a@b.hn",
                1, "napellido", idAuth, rol, true, EstadoSync.SINCRONIZADO);
    }

    private static final Empleado OTRO_ADMIN = empleado(UUID_OTRO_ADMIN, Permisos.ROL_ADMIN);
    private static final Empleado YO_MISMO = empleado(UUID_ADMIN, Permisos.ROL_ADMIN);
    private static final Empleado UN_MESERO = empleado(UUID_MESERO, Permisos.ROL_MESERO);

    // --- editar datos personales ---

    @Test
    public void adminPuedeEditarAUnMesero() {
        assertTrue(ReglasEmpleado.puedeEditar(Permisos.ROL_ADMIN, UUID_ADMIN, UN_MESERO));
    }

    @Test
    public void adminNoPuedeEditarAOtroAdmin() {
        assertFalse(ReglasEmpleado.puedeEditar(Permisos.ROL_ADMIN, UUID_ADMIN, OTRO_ADMIN));
    }

    @Test
    public void adminPuedeEditarSusPropiosDatos() {
        assertTrue(ReglasEmpleado.puedeEditar(Permisos.ROL_ADMIN, UUID_ADMIN, YO_MISMO));
    }

    // --- cambiar rol: mas estricto que editar ---

    @Test
    public void adminPuedeCambiarElRolDeUnMesero() {
        assertTrue(ReglasEmpleado.puedeCambiarRol(Permisos.ROL_ADMIN, UUID_ADMIN, UN_MESERO));
    }

    @Test
    public void adminNoPuedeCambiarElRolDeOtroAdmin() {
        assertFalse(ReglasEmpleado.puedeCambiarRol(Permisos.ROL_ADMIN, UUID_ADMIN, OTRO_ADMIN));
    }

    @Test
    public void adminNoPuedeCambiarSuPropioRol() {
        assertFalse(ReglasEmpleado.puedeCambiarRol(Permisos.ROL_ADMIN, UUID_ADMIN, YO_MISMO));
    }

    // --- activar / desactivar ---

    @Test
    public void adminPuedeDesactivarAUnMesero() {
        assertTrue(ReglasEmpleado.puedeCambiarEstado(Permisos.ROL_ADMIN, UUID_ADMIN, UN_MESERO));
    }

    @Test
    public void adminNoPuedeDesactivarseASiMismo() {
        assertFalse(ReglasEmpleado.puedeCambiarEstado(Permisos.ROL_ADMIN, UUID_ADMIN, YO_MISMO));
    }

    @Test
    public void adminNoPuedeDesactivarAOtroAdmin() {
        assertFalse(ReglasEmpleado.puedeCambiarEstado(Permisos.ROL_ADMIN, UUID_ADMIN, OTRO_ADMIN));
    }

    // --- quien no es admin no puede nada ---

    @Test
    public void unMeseroNoPuedeHacerNadaSobreEmpleados() {
        assertFalse(ReglasEmpleado.puedeEditar(Permisos.ROL_MESERO, UUID_MESERO, UN_MESERO));
        assertFalse(ReglasEmpleado.puedeCambiarRol(Permisos.ROL_MESERO, UUID_MESERO, UN_MESERO));
        assertFalse(ReglasEmpleado.puedeCambiarEstado(Permisos.ROL_MESERO, UUID_MESERO, UN_MESERO));
    }

    @Test
    public void unCocinaNoPuedeHacerNadaSobreEmpleados() {
        assertFalse(ReglasEmpleado.puedeEditar(Permisos.ROL_COCINA, UUID_MESERO, UN_MESERO));
        assertFalse(ReglasEmpleado.puedeCambiarRol(Permisos.ROL_COCINA, UUID_MESERO, UN_MESERO));
    }

    // --- casos limite ---

    @Test
    public void rolNuloNoPuedeNada() {
        assertFalse(ReglasEmpleado.puedeEditar(null, UUID_ADMIN, UN_MESERO));
        assertFalse(ReglasEmpleado.puedeCambiarRol(null, UUID_ADMIN, UN_MESERO));
        assertFalse(ReglasEmpleado.puedeCambiarEstado(null, UUID_ADMIN, UN_MESERO));
    }

    @Test
    public void objetivoNuloNoPermiteNada() {
        assertFalse(ReglasEmpleado.puedeEditar(Permisos.ROL_ADMIN, UUID_ADMIN, null));
        assertFalse(ReglasEmpleado.puedeCambiarRol(Permisos.ROL_ADMIN, UUID_ADMIN, null));
    }

    @Test
    public void elRolNoDistingueMayusculas() {
        assertTrue(ReglasEmpleado.puedeEditar("ADMIN", UUID_ADMIN, UN_MESERO));
        assertFalse(ReglasEmpleado.puedeEditar("Admin", UUID_ADMIN,
                empleado(UUID_OTRO_ADMIN, "ADMIN")));
    }
}
