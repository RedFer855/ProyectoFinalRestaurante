package com.example.proyectofinalrestaurante;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.domain.Permisos;
import com.example.proyectofinalrestaurante.domain.ReglasMesa;
import com.example.proyectofinalrestaurante.domain.model.EstadoMesa;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.Mesa;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tests de {@link ReglasMesa} — el espejo en el cliente de las reglas del servidor
 * (Plan Fase 2c, E1).
 */
public class ReglasMesaTest {

    private static Mesa mesa(int id, int numero, boolean activo) {
        return new Mesa(id, id, numero, 4, null, EstadoMesa.LIBRE, activo, null,
                EstadoSync.SINCRONIZADO);
    }

    // ------------------------------------------------------------------ cambiar estado

    @Test
    public void adminPuedeCambiarEstadoDeMesa() {
        assertTrue(ReglasMesa.puedeCambiarEstado(Permisos.ROL_ADMIN));
    }

    @Test
    public void meseroPuedeCambiarEstadoDeMesa() {
        assertTrue(ReglasMesa.puedeCambiarEstado(Permisos.ROL_MESERO));
    }

    @Test
    public void cocinaNoPuedeCambiarEstadoDeMesa() {
        assertFalse(ReglasMesa.puedeCambiarEstado(Permisos.ROL_COCINA));
    }

    @Test
    public void rolNuloNoPuedeCambiarEstadoDeMesa() {
        assertFalse(ReglasMesa.puedeCambiarEstado(null));
    }

    @Test
    public void mesaActiva_puedeCambiarEstadoOperativo() {
        assertTrue(ReglasMesa.puedeCambiarEstadoOperativo(mesa(1, 4, true)));
    }

    @Test
    public void mesaDeBaja_noPuedeCambiarEstadoOperativo() {
        // El RPC del servidor solo actualiza filas con id_estado = 1: la mesa de baja queda
        // congelada (Plan Fase 2c, §2.4).
        assertFalse(ReglasMesa.puedeCambiarEstadoOperativo(mesa(2, 5, false)));
    }

    @Test
    public void mesaNula_noPuedeCambiarEstadoOperativo() {
        assertFalse(ReglasMesa.puedeCambiarEstadoOperativo(null));
    }

    // ------------------------------------------------------------------ número duplicado

    @Test
    public void listaVacia_noHayDuplicado() {
        assertFalse(ReglasMesa.esNumeroDuplicado(Collections.emptyList(), 4, 0));
    }

    @Test
    public void duplicadoDeOtroId_seDetecta() {
        List<Mesa> mesas = Arrays.asList(mesa(1, 4, true), mesa(2, 7, true));

        assertTrue(ReglasMesa.esNumeroDuplicado(mesas, 7, 0));
    }

    @Test
    public void elPropioNumeroNoCuentaComoDuplicado() {
        List<Mesa> mesas = Arrays.asList(mesa(1, 4, true));

        // Editar la capacidad sin tocar el número no debe reportarse como duplicado.
        assertFalse(ReglasMesa.esNumeroDuplicado(mesas, 4, 1));
    }

    @Test
    public void sinDuplicado_noHayError() {
        List<Mesa> mesas = Arrays.asList(mesa(1, 4, true), mesa(2, 7, true));

        assertFalse(ReglasMesa.esNumeroDuplicado(mesas, 9, 0));
    }

    @Test
    public void listaNula_noRevienta() {
        assertFalse(ReglasMesa.esNumeroDuplicado(null, 4, 0));
    }

    // ------------------------------------------------------------------ no se borran

    @Test
    public void lasMesasNoSeBorran_reglaDelServidor() {
        // El trigger trg_mesa_no_borrar rechaza todo DELETE: solo existe la baja lógica.
        assertTrue(ReglasMesa.lasMesasNoSeBorran());
    }
}
