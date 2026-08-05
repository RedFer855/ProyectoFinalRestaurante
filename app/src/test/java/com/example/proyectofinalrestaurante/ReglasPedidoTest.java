package com.example.proyectofinalrestaurante;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.domain.Permisos;
import com.example.proyectofinalrestaurante.domain.ReglasPedido;
import com.example.proyectofinalrestaurante.domain.model.EstadoPedido;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.Pedido;
import com.example.proyectofinalrestaurante.domain.model.Platillo;

import org.junit.Test;

/**
 * Tests de {@link ReglasPedido} — el espejo en el cliente de la matriz del RPC
 * {@code avanzar_estado_pedido} (Plan Fase 3, §2.5 y B14). La matriz tiene que coincidir
 * exactamente con la del servidor: lo que acá se permita y allá se niegue se traduce en un
 * viaje de red gastado en vano.
 */
public class ReglasPedidoTest {

    private static Pedido pedidoEn(EstadoPedido estado) {
        return new Pedido(1, 1042, "2026-08-04T12:05:00-06:00", estado, 4, "Ana Cruz",
                380.00, 3, "uuid-del-mesero", "2026-08-04T12:05:00-06:00",
                EstadoSync.SINCRONIZADO);
    }

    // ------------------------------------------------------------------ la matriz 3x5

    private static void afirmar(boolean esperado, String rol, EstadoPedido actual,
                                EstadoPedido nuevo) {
        boolean resultado = ReglasPedido.puedeCambiarA(rol, pedidoEn(actual), nuevo);
        String mensaje = rol + " " + actual + " -> " + nuevo;
        if (esperado) {
            assertTrue(mensaje, resultado);
        } else {
            assertFalse(mensaje, resultado);
        }
    }

    @Test
    public void admin_puedeLlevarACualquierEstadoAbierto() {
        afirmar(true, Permisos.ROL_ADMIN, EstadoPedido.PENDIENTE, EstadoPedido.EN_PREPARACION);
        afirmar(true, Permisos.ROL_ADMIN, EstadoPedido.PENDIENTE, EstadoPedido.LISTO);
        afirmar(true, Permisos.ROL_ADMIN, EstadoPedido.PENDIENTE, EstadoPedido.ENTREGADO);
        afirmar(true, Permisos.ROL_ADMIN, EstadoPedido.EN_PREPARACION, EstadoPedido.LISTO);
    }

    @Test
    public void admin_puedeCancelar() {
        afirmar(true, Permisos.ROL_ADMIN, EstadoPedido.PENDIENTE, EstadoPedido.CANCELADO);
        afirmar(true, Permisos.ROL_ADMIN, EstadoPedido.EN_PREPARACION, EstadoPedido.CANCELADO);
    }

    @Test
    public void cocina_avanzaDeAUnoSoloPorElFlujo() {
        afirmar(true, Permisos.ROL_COCINA, EstadoPedido.PENDIENTE, EstadoPedido.EN_PREPARACION);
        afirmar(true, Permisos.ROL_COCINA, EstadoPedido.EN_PREPARACION, EstadoPedido.LISTO);
        // Cocina no puede saltar de Pendiente a Listo, ni marcar Entregado, ni cancelar.
        afirmar(false, Permisos.ROL_COCINA, EstadoPedido.PENDIENTE, EstadoPedido.LISTO);
        afirmar(false, Permisos.ROL_COCINA, EstadoPedido.EN_PREPARACION, EstadoPedido.ENTREGADO);
        afirmar(false, Permisos.ROL_COCINA, EstadoPedido.PENDIENTE, EstadoPedido.CANCELADO);
    }

    @Test
    public void mesero_soloMarcaEntregadoDesdeListo() {
        afirmar(true, Permisos.ROL_MESERO, EstadoPedido.LISTO, EstadoPedido.ENTREGADO);
        // Mesero no avanza cocina ni cancela.
        afirmar(false, Permisos.ROL_MESERO, EstadoPedido.PENDIENTE, EstadoPedido.EN_PREPARACION);
        afirmar(false, Permisos.ROL_MESERO, EstadoPedido.EN_PREPARACION, EstadoPedido.LISTO);
        afirmar(false, Permisos.ROL_MESERO, EstadoPedido.PENDIENTE, EstadoPedido.CANCELADO);
    }

    @Test
    public void pedidoCerrado_noAvanzaParaNadie() {
        for (String rol : new String[]{Permisos.ROL_ADMIN, Permisos.ROL_COCINA, Permisos.ROL_MESERO}) {
            afirmar(false, rol, EstadoPedido.ENTREGADO, EstadoPedido.LISTO);
            afirmar(false, rol, EstadoPedido.CANCELADO, EstadoPedido.PENDIENTE);
        }
    }

    @Test
    public void rolDesconocido_noPuedeNada() {
        afirmar(false, "cajero", EstadoPedido.PENDIENTE, EstadoPedido.EN_PREPARACION);
    }

    @Test
    public void rolNulo_noPuedeNada() {
        assertFalse(ReglasPedido.puedeCambiarA(null, pedidoEn(EstadoPedido.PENDIENTE),
                EstadoPedido.EN_PREPARACION));
    }

    @Test
    public void pedidoNulo_noPuedeNada() {
        assertFalse(ReglasPedido.puedeCambiarA(Permisos.ROL_ADMIN, null, EstadoPedido.LISTO));
    }

    @Test
    public void estadoDesconocido_noPuedeAvanzar() {
        Pedido conEstadoNuevo = new Pedido(1, 1042, "2026-08-04T12:05:00-06:00", null,
                4, "Ana Cruz", 380.00, 3, null, null, EstadoSync.SINCRONIZADO);
        assertFalse(ReglasPedido.puedeCambiarA(Permisos.ROL_ADMIN, conEstadoNuevo,
                EstadoPedido.EN_PREPARACION));
    }

    // ------------------------------------------------------------------ cancelar

    @Test
    public void cancelar_soloAdmin() {
        assertTrue(ReglasPedido.puedeCancelar(Permisos.ROL_ADMIN));
        assertFalse(ReglasPedido.puedeCancelar(Permisos.ROL_MESERO));
        assertFalse(ReglasPedido.puedeCancelar(Permisos.ROL_COCINA));
        assertFalse(ReglasPedido.puedeCancelar(null));
    }

    // ------------------------------------------------------------------ siguiente

    @Test
    public void siguienteDe_recorreElFlujo() {
        assertEquals(EstadoPedido.EN_PREPARACION, ReglasPedido.siguienteDe(EstadoPedido.PENDIENTE));
        assertEquals(EstadoPedido.LISTO, ReglasPedido.siguienteDe(EstadoPedido.EN_PREPARACION));
        assertEquals(EstadoPedido.ENTREGADO, ReglasPedido.siguienteDe(EstadoPedido.LISTO));
    }

    @Test
    public void siguienteDe_estadoDeCierreNoAvanza() {
        assertNull(ReglasPedido.siguienteDe(EstadoPedido.ENTREGADO));
        assertNull(ReglasPedido.siguienteDe(EstadoPedido.CANCELADO));
        assertNull(ReglasPedido.siguienteDe(null));
    }

    // -------------------------------------------------- toma de pedido (Plan Fase 3b)

    @Test
    public void puedeTomarPedido_adminYMesero_si_cocinaYOtros_no() {
        assertTrue(ReglasPedido.puedeTomarPedido(Permisos.ROL_ADMIN));
        assertTrue(ReglasPedido.puedeTomarPedido(Permisos.ROL_MESERO));
        assertFalse(ReglasPedido.puedeTomarPedido(Permisos.ROL_COCINA));
        assertFalse(ReglasPedido.puedeTomarPedido("cajero"));
        assertFalse(ReglasPedido.puedeTomarPedido(null));
    }

    @Test
    public void puedePedirse_platiulleSincronizadoYActivo_true() {
        assertTrue(ReglasPedido.puedePedirse(
                platilloCon(5, 1042, true)));
    }

    @Test
    public void puedePedirse_sinIdServidor_false() {
        // Un platillo que no ha subido no tiene a qué apuntar (Plan Fase 3b, §4.2).
        assertFalse(ReglasPedido.puedePedirse(
                platilloCon(5, null, true)));
    }

    @Test
    public void puedePedirse_platilloInactivo_false() {
        assertFalse(ReglasPedido.puedePedirse(
                platilloCon(5, 1042, false)));
    }

    @Test
    public void puedePedirse_platilloNulo_false() {
        assertFalse(ReglasPedido.puedePedirse(null));
    }

    private static Platillo platilloCon(int idLocal, Integer idServidor, boolean activo) {
        return new Platillo(idLocal, idServidor, "Baleada", "Con mantequilla", 45.00, 1,
                "Platos fuertes", null, activo, EstadoSync.SINCRONIZADO);
    }
}
