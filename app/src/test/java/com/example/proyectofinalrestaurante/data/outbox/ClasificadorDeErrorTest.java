package com.example.proyectofinalrestaurante.data.outbox;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;

/**
 * La política de reintentos del outbox (Plan Fase 2b, §4.4): qué fallos son transitorios y
 * se vuelven a intentar, y cuáles permanentes y se descartan. Sin red ni DAOs: es JUnit
 * puro sobre la clase sola.
 */
public class ClasificadorDeErrorTest {

    @Test
    public void esTransitorio_IOException_esTransitorioPorDefinicion() {
        assertTrue(ClasificadorDeError.esTransitorio(new IOException("sin red")));
    }

    @Test
    public void esTransitorio_excepcionInesperada_esPermanente() {
        assertFalse(ClasificadorDeError.esTransitorio(new RuntimeException("bug")));
    }

    @Test
    public void esTransitorio_sinCodigo_esTransitorio() {
        assertTrue(ClasificadorDeError.esTransitorio(ClasificadorDeError.SIN_CODIGO));
    }

    @Test
    public void esTransitorio_codigosReintentables_sonTransitorios() {
        assertTrue(ClasificadorDeError.esTransitorio(401));
        assertTrue(ClasificadorDeError.esTransitorio(408));
        assertTrue(ClasificadorDeError.esTransitorio(429));
    }

    @Test
    public void esTransitorio_cualquier5xx_esTransitorio() {
        assertTrue(ClasificadorDeError.esTransitorio(500));
        assertTrue(ClasificadorDeError.esTransitorio(502));
        assertTrue(ClasificadorDeError.esTransitorio(503));
    }

    @Test
    public void esTransitorio_restoDeLos4xx_esPermanente() {
        assertFalse(ClasificadorDeError.esTransitorio(400));
        assertFalse(ClasificadorDeError.esTransitorio(404));
        // El 409 es el caso documentado del índice uq_platillo_nombre: reintentarlo
        // volvería a chocar con el mismo nombre; hay que descartarlo.
        assertFalse(ClasificadorDeError.esTransitorio(409));
        assertFalse(ClasificadorDeError.esTransitorio(422));
    }
}
