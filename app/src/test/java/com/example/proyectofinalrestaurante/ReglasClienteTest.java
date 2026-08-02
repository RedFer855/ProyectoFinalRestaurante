package com.example.proyectofinalrestaurante;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.domain.Permisos;
import com.example.proyectofinalrestaurante.domain.ReglasCliente;
import com.example.proyectofinalrestaurante.domain.model.Cliente;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;

import org.junit.Test;

/** {@link ReglasCliente} (Plan Fase 2d, E7). */
public class ReglasClienteTest {

    @Test
    public void normalizarIdentidad_quitaGuionesYEspacios() {
        assertEquals("0801199012345", ReglasCliente.normalizarIdentidad("0801-1990-12345"));
        assertEquals("0801199012345", ReglasCliente.normalizarIdentidad("0801 1990 12345"));
        assertEquals("0801199012345", ReglasCliente.normalizarIdentidad("0801199012345"));
    }

    @Test
    public void normalizarIdentidad_mismoResultadoConYSinGuiones() {
        // Es justo lo que permite que el buscar-o-crear detecte al mismo cliente.
        assertEquals(ReglasCliente.normalizarIdentidad("0801-1990-12345"),
                ReglasCliente.normalizarIdentidad("080119901 2345"));
    }

    @Test
    public void normalizarIdentidad_nullODeSoloSimbolos_devuelveNull() {
        assertNull(ReglasCliente.normalizarIdentidad(null));
        assertNull(ReglasCliente.normalizarIdentidad(""));
        assertNull(ReglasCliente.normalizarIdentidad("----"));
    }

    @Test
    public void puedeBorrarse_sinPedidos() {
        assertTrue(ReglasCliente.puedeBorrarse(cliente(0)));
    }

    @Test
    public void puedeBorrarse_conPedidos_esFalso() {
        assertFalse(ReglasCliente.puedeBorrarse(cliente(3)));
    }

    @Test
    public void puedeBorrarse_null_esFalso() {
        assertFalse(ReglasCliente.puedeBorrarse(null));
    }

    @Test
    public void puedeDarDeBajaOBorrar_soloAdmin() {
        assertTrue(ReglasCliente.puedeDarDeBajaOBorrar(Permisos.ROL_ADMIN));
        assertFalse(ReglasCliente.puedeDarDeBajaOBorrar(Permisos.ROL_MESERO));
        assertFalse(ReglasCliente.puedeDarDeBajaOBorrar(Permisos.ROL_COCINA));
        assertFalse(ReglasCliente.puedeDarDeBajaOBorrar(null));
    }

    private static Cliente cliente(int cantidadPedidos) {
        return new Cliente(1, 10, "Ana", "Cruz", "0801199012345", "9999-0000", true,
                cantidadPedidos, "2026-08-01", EstadoSync.SINCRONIZADO);
    }
}
