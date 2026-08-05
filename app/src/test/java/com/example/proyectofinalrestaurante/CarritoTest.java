package com.example.proyectofinalrestaurante;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.domain.model.Carrito;
import com.example.proyectofinalrestaurante.domain.model.LineaCarrito;
import com.example.proyectofinalrestaurante.domain.model.Platillo;

import org.junit.Test;

/**
 * Tests de {@link Carrito} (Plan Fase 3b, E2 / B6-B7): inmutable y que fusiona líneas
 * del mismo platillo en vez de duplicarlas.
 */
public class CarritoTest {

    private static Platillo platillo(int id) {
        return new Platillo(id, id + 1000, "Baleada " + id, "Rica", 45.00, 1,
                "Platos fuertes", null, true, com.example.proyectofinalrestaurante.domain.model.EstadoSync.SINCRONIZADO);
    }

    @Test
    public void vacio_estaVacio_totalCero() {
        Carrito carrito = Carrito.vacio();
        assertTrue(carrito.estaVacio());
        assertEquals(0, carrito.cantidadItems());
        assertEquals(0.0, carrito.total(), 0.0001);
    }

    @Test
    public void con_agregaUnaLineaConCantidadUno() {
        Carrito carrito = Carrito.vacio().con(platillo(5));
        assertFalse(carrito.estaVacio());
        assertEquals(1, carrito.cantidadItems());
        LineaCarrito linea = carrito.getLineas().get(0);
        assertEquals(5, linea.getIdLocalPlatillo());
        assertEquals(1, linea.getCantidad());
        assertEquals("Baleada 5", linea.getNombre());
    }

    @Test
    public void con_dosVecesElMismoPlatillo_fusionaEnUnaLineaCantidad2() {
        Carrito carrito = Carrito.vacio().con(platillo(7)).con(platillo(7));
        assertEquals(1, carrito.cantidadItems());
        assertEquals(2, carrito.getLineas().get(0).getCantidad());
    }

    @Test
    public void con_platillosDistintos_sonLineasSeparadas() {
        Carrito carrito = Carrito.vacio().con(platillo(1)).con(platillo(2));
        assertEquals(2, carrito.cantidadItems());
    }

    @Test
    public void con_platilloNulo_noHaceNada() {
        Carrito vacio = Carrito.vacio();
        assertSame(vacio, vacio.con(null));
    }

    @Test
    public void conCantidad_fijaLaCantidadDeLaLinea() {
        Carrito carrito = Carrito.vacio().con(platillo(3)).conCantidad(3, 4);
        assertEquals(4, carrito.getLineas().get(0).getCantidad());
    }

    @Test
    public void conCantidad_cero_eliminaLaLinea() {
        Carrito carrito = Carrito.vacio().con(platillo(3)).conCantidad(3, 0);
        assertTrue(carrito.estaVacio());
    }

    @Test
    public void conCantidad_negativa_eliminaLaLinea() {
        Carrito carrito = Carrito.vacio().con(platillo(3)).conCantidad(3, -2);
        assertTrue(carrito.estaVacio());
    }

    @Test
    public void conCantidad_mismaCantidad_devuelveThis() {
        Carrito carrito = Carrito.vacio().con(platillo(3));
        assertSame(carrito, carrito.conCantidad(3, 1));
    }

    @Test
    public void conCantidad_platilloQueNoEstá_devuelveThis() {
        Carrito vacio = Carrito.vacio();
        assertSame(vacio, vacio.conCantidad(99, 2));
    }

    @Test
    public void sinPlatillo_eliminaLaLinea() {
        Carrito carrito = Carrito.vacio().con(platillo(1)).con(platillo(2)).sinPlatillo(1);
        assertEquals(1, carrito.cantidadItems());
        assertEquals(2, carrito.getLineas().get(0).getIdLocalPlatillo());
    }

    @Test
    public void sinPlatillo_inexistente_devuelveThis() {
        Carrito carrito = Carrito.vacio().con(platillo(1));
        assertSame(carrito, carrito.sinPlatillo(99));
    }

    @Test
    public void total_sumaLosSubtotales() {
        // 2x Baleada 1 (qty 2, precio 45) + 3x Baleada 2 (qty 3, precio 45) = 225
        Carrito carrito = Carrito.vacio().con(platillo(1)).con(platillo(1)).con(platillo(2))
                .con(platillo(2)).con(platillo(2));
        assertEquals(225.0, carrito.total(), 0.0001);
    }

    @Test
    public void cualquierOperacion_noMutaElOriginal() {
        Carrito original = Carrito.vacio().con(platillo(1));
        Carrito modificado = original.conCantidad(1, 5);
        assertNotSame(original, modificado);
        assertTrue(original.getLineas().get(0).getCantidad() == 1);
        assertTrue(modificado.getLineas().get(0).getCantidad() == 5);
    }

    @Test
    public void lasLineasDeUnOperacion_noAlteranLasDeOtra() {
        Carrito original = Carrito.vacio().con(platillo(1));
        original.con(platillo(2));
        assertEquals(1, original.cantidadItems());
    }
}