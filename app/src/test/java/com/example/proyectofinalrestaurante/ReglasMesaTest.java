package com.example.proyectofinalrestaurante;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.domain.ReglasMesa;
import com.example.proyectofinalrestaurante.domain.model.EstadoMesa;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.Mesa;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Tests de {@link ReglasMesa} (Fase 2c, E7). Java puro, sin dependencias de Android. */
public class ReglasMesaTest {

    private static Mesa mesa(int idLocal, int numero, boolean activo) {
        return new Mesa(idLocal, null, numero, 4, "Salón", EstadoMesa.LIBRE,
                activo, EstadoSync.SINCRONIZADO);
    }

    // ------------------------------------------------------------------ cambiarEstado

    @Test
    public void mesaActiva_sePuedeCambiarEstado() {
        assertTrue(ReglasMesa.puedeCambiarEstado(mesa(1, 1, true)));
    }

    @Test
    public void mesaInactiva_noSePuedeCambiarEstado() {
        assertFalse(ReglasMesa.puedeCambiarEstado(mesa(1, 1, false)));
    }

    @Test
    public void mesaNula_noSePuedeCambiarEstado() {
        assertFalse(ReglasMesa.puedeCambiarEstado(null));
    }

    // ------------------------------------------------------------------ editarse

    @Test
    public void mesaActiva_sePuedeEditar() {
        assertTrue(ReglasMesa.puedeEditarse(mesa(1, 1, true)));
    }

    @Test
    public void mesaInactiva_noSePuedeEditar() {
        assertFalse(ReglasMesa.puedeEditarse(mesa(1, 1, false)));
    }

    // ------------------------------------------------------------------ darseDeBaja

    @Test
    public void mesaActiva_sePuedeDarDeBaja() {
        assertTrue(ReglasMesa.puedeDarseDeBaja(mesa(1, 1, true)));
    }

    @Test
    public void mesaInactiva_noSePuedeDarDeBaja() {
        assertFalse(ReglasMesa.puedeDarseDeBaja(mesa(1, 1, false)));
    }

    // ------------------------------------------------------------------ reactivarse

    @Test
    public void mesaInactiva_sePuedeReactivar() {
        assertTrue(ReglasMesa.puedeReactivarse(mesa(1, 1, false)));
    }

    @Test
    public void mesaActiva_noSePuedeReactivar() {
        assertFalse(ReglasMesa.puedeReactivarse(mesa(1, 1, true)));
    }

    // ------------------------------------------------------------------ borrarse

    @Test
    public void mesaActiva_nuncaSePuedeBorrar() {
        assertFalse(ReglasMesa.puedeBorrarse(mesa(1, 1, true)));
    }

    @Test
    public void mesaInactiva_nuncaSePuedeBorrar() {
        assertFalse(ReglasMesa.puedeBorrarse(mesa(1, 1, false)));
    }

    // ------------------------------------------------------------------ duplicados

    @Test
    public void listaNula_noHayDuplicado() {
        assertFalse(ReglasMesa.existeOtraMesaConNumero(null, 4, 0));
    }

    @Test
    public void mismaId_noEsDuplicado() {
        List<Mesa> mesas = Arrays.asList(mesa(1, 4, true));
        assertFalse(ReglasMesa.existeOtraMesaConNumero(mesas, 4, 1));
    }

    @Test
    public void otraMesaMismoNumero_esDuplicado() {
        List<Mesa> mesas = Arrays.asList(
                mesa(1, 4, true),
                mesa(2, 4, true));
        assertTrue(ReglasMesa.existeOtraMesaConNumero(mesas, 4, 1));
    }

    @Test
    public void otraMesaDistintoNumero_noEsDuplicado() {
        List<Mesa> mesas = Arrays.asList(
                mesa(1, 4, true),
                mesa(2, 6, true));
        assertFalse(ReglasMesa.existeOtraMesaConNumero(mesas, 4, 1));
    }

    @Test
    public void creacion_conDuplicado_esDuplicado() {
        // idMesaActual = 0 (creación)
        List<Mesa> mesas = Arrays.asList(mesa(1, 4, true));
        assertTrue(ReglasMesa.existeOtraMesaConNumero(mesas, 4, 0));
    }

    // ------------------------------------------------------------------ necesitaEtiqueta

    @Test
    public void necesitaEtiqueta_siempreTrue() {
        assertTrue(ReglasMesa.necesitaEtiqueta());
    }
}
