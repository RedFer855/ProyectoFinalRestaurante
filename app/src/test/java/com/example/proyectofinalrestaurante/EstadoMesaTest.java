package com.example.proyectofinalrestaurante;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.proyectofinalrestaurante.domain.model.EstadoMesa;

import org.junit.Test;

/** Tests de {@link EstadoMesa} — el mapeo id ↔ estado del catálogo (Plan Fase 2c, E1). */
public class EstadoMesaTest {

    @Test
    public void idsConocidos_devuelvenSuEstado() {
        assertEquals(EstadoMesa.LIBRE, EstadoMesa.porId(1));
        assertEquals(EstadoMesa.OCUPADA, EstadoMesa.porId(2));
        assertEquals(EstadoMesa.RESERVADA, EstadoMesa.porId(3));
    }

    @Test
    public void idDesconocido_devuelveNull() {
        // El servidor puede traer estados más nuevos que este APK: se devuelve null y la UI
        // lo pinta como estado desconocido, sin mentirle al mesero con un estado inventado.
        assertNull(EstadoMesa.porId(4));
        assertNull(EstadoMesa.porId(0));
    }

    @Test
    public void cadaEstadoTieneSuIdDeCatalogo() {
        assertEquals(1, EstadoMesa.LIBRE.getId());
        assertEquals(2, EstadoMesa.OCUPADA.getId());
        assertEquals(3, EstadoMesa.RESERVADA.getId());
    }
}
