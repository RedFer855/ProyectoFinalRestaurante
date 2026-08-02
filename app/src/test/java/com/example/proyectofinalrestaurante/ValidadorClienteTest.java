package com.example.proyectofinalrestaurante;

import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.domain.ValidadorCliente;
import com.example.proyectofinalrestaurante.domain.ValidadorCliente.ErrorCliente;
import com.example.proyectofinalrestaurante.domain.model.NuevoCliente;

import org.junit.Test;

import java.util.Set;

/** Tests de {@link ValidadorCliente} (Plan Fase 2d, E7): incluye la normalización de identidad. */
public class ValidadorClienteTest {

    @Test
    public void nuevoValido_sinIdentidadNiTelefono_esValido() {
        // Venta de mostrador (ADR-006): ni identidad ni teléfono son obligatorios.
        assertTrue(ValidadorCliente.esValido(new NuevoCliente("Gabriela", "Paz", null, null)));
    }

    @Test
    public void nombreVacio_seDetecta() {
        Set<ErrorCliente> errores = ValidadorCliente.validar(new NuevoCliente(" ", "Cruz", null, null));
        assertTrue(errores.contains(ErrorCliente.NOMBRE_OBLIGATORIO));
    }

    @Test
    public void apellidoVacio_seDetecta() {
        Set<ErrorCliente> errores = ValidadorCliente.validar(new NuevoCliente("Ana", "", null, null));
        assertTrue(errores.contains(ErrorCliente.APELLIDO_OBLIGATORIO));
    }

    @Test
    public void nuevoNulo_devuelveNombreYApellidoObligatorios() {
        Set<ErrorCliente> errores = ValidadorCliente.validar(null);
        assertTrue(errores.contains(ErrorCliente.NOMBRE_OBLIGATORIO));
        assertTrue(errores.contains(ErrorCliente.APELLIDO_OBLIGATORIO));
    }

    @Test
    public void identidadCon13Digitos_esValida() {
        assertTrue(ValidadorCliente.esValido(
                new NuevoCliente("Ana", "Cruz", "0801-1995-12345", null)));
    }

    @Test
    public void identidadConMenosDe13Digitos_seDetecta() {
        Set<ErrorCliente> errores =
                ValidadorCliente.validar(new NuevoCliente("Ana", "Cruz", "0801-1995", null));
        assertTrue(errores.contains(ErrorCliente.IDENTIDAD_INVALIDA));
    }

    @Test
    public void identidadConGuionesYSuficientesDigitos_noSeRechazaPorElFormato() {
        // El guion no importa: lo que cuenta son los dígitos que quedan tras normalizar.
        // "0801-1995-1234-5" normaliza a 14 dígitos, válida a pesar del formato raro.
        assertTrue(ValidadorCliente.esValido(
                new NuevoCliente("Ana", "Cruz", "0801-1995-1234-5", null)));
    }
}
