package com.example.proyectofinalrestaurante;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.domain.ValidadorCliente;
import com.example.proyectofinalrestaurante.domain.ValidadorCliente.ErrorCliente;
import com.example.proyectofinalrestaurante.domain.model.Cliente;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.NuevoCliente;

import org.junit.Test;

import java.util.Set;

/** Tests de {@link ValidadorCliente} (Fase 2d, E7). */
public class ValidadorClienteTest {

    private static NuevoCliente nuevo(String nombre, String apellido) {
        return new NuevoCliente(nombre, apellido, "0801199512345", "9988-1122");
    }

    private static Cliente cliente(String nombre, String apellido) {
        return new Cliente(1, 100, nombre, apellido,
                "0801199512345", "9988-1122", true, 0, EstadoSync.SINCRONIZADO);
    }

    // ------------------------------------------------------------------ nuevoCliente

    @Test
    public void nuevoClienteCompleto_esValido() {
        assertTrue(ValidadorCliente.esValido(nuevo("Ana", "Cruz")));
        assertTrue(ValidadorCliente.validar(nuevo("Ana", "Cruz")).isEmpty());
    }

    @Test
    public void nombreVacio_seDetecta() {
        assertTrue(ValidadorCliente.validar(nuevo("", "Cruz"))
                .contains(ErrorCliente.NOMBRE_VACIO));
    }

    @Test
    public void nombreNulo_seDetecta() {
        assertTrue(ValidadorCliente.validar(nuevo(null, "Cruz"))
                .contains(ErrorCliente.NOMBRE_VACIO));
    }

    @Test
    public void nombreSoloEspacios_seDetecta() {
        assertTrue(ValidadorCliente.validar(nuevo("   ", "Cruz"))
                .contains(ErrorCliente.NOMBRE_VACIO));
    }

    @Test
    public void apellidoVacio_seDetecta() {
        assertTrue(ValidadorCliente.validar(nuevo("Ana", ""))
                .contains(ErrorCliente.APELLIDO_VACIO));
    }

    @Test
    public void apellidoNulo_seDetecta() {
        assertTrue(ValidadorCliente.validar(nuevo("Ana", null))
                .contains(ErrorCliente.APELLIDO_VACIO));
    }

    @Test
    public void ambosVacios_devuelveAmbosErrores() {
        Set<ErrorCliente> errores = ValidadorCliente.validar(nuevo("", ""));
        assertEquals(2, errores.size());
        assertTrue(errores.contains(ErrorCliente.NOMBRE_VACIO));
        assertTrue(errores.contains(ErrorCliente.APELLIDO_VACIO));
    }

    @Test
    public void identidadMuyCorta_seDetecta() {
        NuevoCliente corto = new NuevoCliente("Ana", "Cruz", "123", "9988-1122");
        assertTrue(ValidadorCliente.validar(corto)
                .contains(ErrorCliente.IDENTIDAD_MUY_CORTA));
    }

    @Test
    public void identidadNula_esValida() {
        NuevoCliente sinId = new NuevoCliente("Ana", "Cruz", null, "9988-1122");
        assertTrue(ValidadorCliente.esValido(sinId));
    }

    @Test
    public void identidadVacia_esValida() {
        NuevoCliente sinId = new NuevoCliente("Ana", "Cruz", "", "9988-1122");
        assertTrue(ValidadorCliente.esValido(sinId));
    }

    @Test
    public void identidadConGuionesSeNormaliza() {
        NuevoCliente conGuiones = new NuevoCliente("Ana", "Cruz",
                "0801-1995-12345", "9988-1122");
        assertTrue(ValidadorCliente.esValido(conGuiones));
    }

    @Test
    public void nuevoClienteNulo_noRevientaYNoEsValida() {
        assertFalse(ValidadorCliente.esValido(null));
        assertEquals(3, ValidadorCliente.validar((NuevoCliente) null).size());
    }

    // ------------------------------------------------------------------ cliente existente

    @Test
    public void clienteCompleto_esValido() {
        assertTrue(ValidadorCliente.esValido(cliente("Ana", "Cruz")));
    }

    @Test
    public void clienteNombreVacio_seDetecta() {
        assertTrue(ValidadorCliente.validar(cliente("", "Cruz"))
                .contains(ErrorCliente.NOMBRE_VACIO));
    }

    @Test
    public void clienteApellidoVacio_seDetecta() {
        assertTrue(ValidadorCliente.validar(cliente("Ana", ""))
                .contains(ErrorCliente.APELLIDO_VACIO));
    }

    @Test
    public void clienteNulo_noRevientaYNoEsValida() {
        assertFalse(ValidadorCliente.esValido(null));
    }
}
