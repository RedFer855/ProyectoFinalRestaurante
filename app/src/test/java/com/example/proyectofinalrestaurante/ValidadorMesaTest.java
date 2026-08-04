package com.example.proyectofinalrestaurante;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.domain.ValidadorMesa;
import com.example.proyectofinalrestaurante.domain.ValidadorMesa.ErrorMesa;
import com.example.proyectofinalrestaurante.domain.model.Mesa;
import com.example.proyectofinalrestaurante.domain.model.NuevaMesa;

import org.junit.Test;

import java.util.Set;

/** Tests de {@link ValidadorMesa} (Fase 2c, E7). */
public class ValidadorMesaTest {

    private static NuevaMesa nueva(int numero, int capacidad) {
        return new NuevaMesa(numero, capacidad, "Salón principal");
    }

    private static Mesa mesa(int numero, int capacidad) {
        return new Mesa(1, 100, numero, capacidad, "Salón",
                com.example.proyectofinalrestaurante.domain.model.EstadoMesa.LIBRE,
                true, com.example.proyectofinalrestaurante.domain.model.EstadoSync.SINCRONIZADO);
    }

    // ------------------------------------------------------------------ nuevaMesa

    @Test
    public void nuevaMesaCompleta_esValida() {
        assertTrue(ValidadorMesa.esValido(nueva(1, 4)));
        assertTrue(ValidadorMesa.validar(nueva(1, 4)).isEmpty());
    }

    @Test
    public void numeroCero_seDetecta() {
        assertTrue(ValidadorMesa.validar(nueva(0, 4))
                .contains(ErrorMesa.NUMERO_NO_POSITIVO));
    }

    @Test
    public void numeroNegativo_seDetecta() {
        assertTrue(ValidadorMesa.validar(nueva(-1, 4))
                .contains(ErrorMesa.NUMERO_NO_POSITIVO));
    }

    @Test
    public void capacidadCero_seDetecta() {
        assertTrue(ValidadorMesa.validar(nueva(1, 0))
                .contains(ErrorMesa.CAPACIDAD_NO_POSITIVA));
    }

    @Test
    public void capacidadNegativa_seDetecta() {
        assertTrue(ValidadorMesa.validar(nueva(1, -2))
                .contains(ErrorMesa.CAPACIDAD_NO_POSITIVA));
    }

    @Test
    public void ambosInvalidos_devuelveAmbosErrores() {
        Set<ErrorMesa> errores = ValidadorMesa.validar(nueva(0, 0));
        assertEquals(2, errores.size());
        assertTrue(errores.contains(ErrorMesa.NUMERO_NO_POSITIVO));
        assertTrue(errores.contains(ErrorMesa.CAPACIDAD_NO_POSITIVA));
    }

    @Test
    public void nuevaMesaNula_noRevientaYNoEsValida() {
        assertFalse(ValidadorMesa.esValido(null));
        assertEquals(2, ValidadorMesa.validar((NuevaMesa) null).size());
    }

    // ------------------------------------------------------------------ mesa existente

    @Test
    public void mesaCompleta_esValida() {
        assertTrue(ValidadorMesa.esValido(mesa(4, 6)));
    }

    @Test
    public void mesaNumeroCero_seDetecta() {
        assertTrue(ValidadorMesa.validar(mesa(0, 4))
                .contains(ErrorMesa.NUMERO_NO_POSITIVO));
    }

    @Test
    public void mesaCapacidadCero_seDetecta() {
        assertTrue(ValidadorMesa.validar(mesa(1, 0))
                .contains(ErrorMesa.CAPACIDAD_NO_POSITIVA));
    }

    @Test
    public void mesaNula_noRevientaYNoEsValida() {
        assertFalse(ValidadorMesa.esValido(null));
    }
}
