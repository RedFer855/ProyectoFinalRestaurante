package com.example.proyectofinalrestaurante;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.domain.ReglasCliente;
import com.example.proyectofinalrestaurante.domain.model.Cliente;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Tests de {@link ReglasCliente} (Fase 2d, E7). Java puro, sin dependencias de Android. */
public class ReglasClienteTest {

    private static Cliente cliente(int idLocal, boolean activo, int pedidos) {
        return new Cliente(idLocal, 100 + idLocal, "Ana", "Cruz",
                "0801199512345", "9988-1122", activo, pedidos, EstadoSync.SINCRONIZADO);
    }

    private static Cliente conIdentidad(int idLocal, String identidad) {
        return new Cliente(idLocal, 100 + idLocal, "Ana", "Cruz",
                identidad, "9988-1122", true, 0, EstadoSync.SINCRONIZADO);
    }

    // ------------------------------------------------------------------ normalizarIdentidad

    @Test
    public void identidadNormalizada_quitaGuiones() {
        assertEquals("0801199512345", ReglasCliente.normalizarIdentidad("0801-1995-12345"));
    }

    @Test
    public void identidadNormalizada_quitaEspacios() {
        assertEquals("0801199512345", ReglasCliente.normalizarIdentidad("0801 1995 12345"));
    }

    @Test
    public void identidadNormalizada_quitaLetras() {
        assertEquals("0801199512345", ReglasCliente.normalizarIdentidad("0801-ABC-12345"));
    }

    @Test
    public void identidadNula_devuelveVacia() {
        assertEquals("", ReglasCliente.normalizarIdentidad(null));
    }

    @Test
    public void identidadVacia_devuelveVacia() {
        assertEquals("", ReglasCliente.normalizarIdentidad(""));
    }

    // ------------------------------------------------------------------ puedeDarseDeBaja

    @Test
    public void clienteActivo_sePuedeDarDeBaja() {
        assertTrue(ReglasCliente.puedeDarseDeBaja(cliente(1, true, 0)));
    }

    @Test
    public void clienteInactivo_noSePuedeDarDeBaja() {
        assertFalse(ReglasCliente.puedeDarseDeBaja(cliente(1, false, 0)));
    }

    @Test
    public void clienteNulo_noSePuedeDarDeBaja() {
        assertFalse(ReglasCliente.puedeDarseDeBaja(null));
    }

    // ------------------------------------------------------------------ puedeReactivarse

    @Test
    public void clienteInactivo_sePuedeReactivar() {
        assertTrue(ReglasCliente.puedeReactivarse(cliente(1, false, 0)));
    }

    @Test
    public void clienteActivo_noSePuedeReactivar() {
        assertFalse(ReglasCliente.puedeReactivarse(cliente(1, true, 0)));
    }

    @Test
    public void clienteNulo_noSePuedeReactivar() {
        assertFalse(ReglasCliente.puedeReactivarse(null));
    }

    // ------------------------------------------------------------------ puedeBorrarse

    @Test
    public void clienteSinPedidos_sePuedeBorrar() {
        assertTrue(ReglasCliente.puedeBorrarse(cliente(1, true, 0)));
    }

    @Test
    public void clienteConPedidos_noSePuedeBorrar() {
        assertFalse(ReglasCliente.puedeBorrarse(cliente(1, true, 5)));
    }

    @Test
    public void clienteNulo_noSePuedeBorrar() {
        assertFalse(ReglasCliente.puedeBorrarse(null));
    }

    // ------------------------------------------------------------------ puedeEditarse

    @Test
    public void clienteExistente_sePuedeEditar() {
        assertTrue(ReglasCliente.puedeEditarse(cliente(1, true, 0)));
    }

    @Test
    public void clienteNulo_noSePuedeEditar() {
        assertFalse(ReglasCliente.puedeEditarse(null));
    }

    // ------------------------------------------------------------------ existeOtroConIdentidad

    @Test
    public void listaNula_noHayDuplicado() {
        assertFalse(ReglasCliente.existeOtroConIdentidad(null, "0801199512345", 0));
    }

    @Test
    public void identidadNula_noHayDuplicado() {
        List<Cliente> clientes = Arrays.asList(cliente(1, true, 0));
        assertFalse(ReglasCliente.existeOtroConIdentidad(clientes, null, 0));
    }

    @Test
    public void identidadVacia_noHayDuplicado() {
        List<Cliente> clientes = Arrays.asList(cliente(1, true, 0));
        assertFalse(ReglasCliente.existeOtroConIdentidad(clientes, "", 0));
    }

    @Test
    public void mismaId_noEsDuplicado() {
        List<Cliente> clientes = Arrays.asList(conIdentidad(1, "0801199512345"));
        assertFalse(ReglasCliente.existeOtroConIdentidad(clientes, "0801199512345", 1));
    }

    @Test
    public void otroClienteMismaIdentidad_esDuplicado() {
        List<Cliente> clientes = Arrays.asList(
                conIdentidad(1, "0801199512345"),
                conIdentidad(2, "0801199512345"));
        assertTrue(ReglasCliente.existeOtroConIdentidad(clientes, "0801199512345", 1));
    }

    @Test
    public void otroClienteDistintaIdentidad_noEsDuplicado() {
        List<Cliente> clientes = Arrays.asList(
                conIdentidad(1, "0801199512345"),
                conIdentidad(2, "0501200203344"));
        assertFalse(ReglasCliente.existeOtroConIdentidad(clientes, "0801199512345", 1));
    }

    @Test
    public void creacion_conDuplicado_esDuplicado() {
        List<Cliente> clientes = Arrays.asList(conIdentidad(1, "0801199512345"));
        assertTrue(ReglasCliente.existeOtroConIdentidad(clientes, "0801199512345", 0));
    }

    @Test
    public void identidadConGuiones_seNormalizaParaComparar() {
        List<Cliente> clientes = Arrays.asList(conIdentidad(1, "0801-1995-12345"));
        assertTrue(ReglasCliente.existeOtroConIdentidad(clientes, "0801199512345", 0));
    }
}
