package com.example.proyectofinalrestaurante;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.domain.ValidadorPlatillo;
import com.example.proyectofinalrestaurante.domain.ValidadorPlatillo.ErrorPlatillo;
import com.example.proyectofinalrestaurante.domain.model.NuevoPlatillo;

import org.junit.Test;

import java.util.Set;

/** Tests de {@link ValidadorPlatillo} (Plan Fase 2a, E7). */
public class ValidadorPlatilloTest {

    private static NuevoPlatillo platillo(String nombre, double precio, int idCategoria) {
        return new NuevoPlatillo(nombre, "Con frijoles", precio, idCategoria);
    }

    @Test
    public void platilloCompleto_esValido() {
        assertTrue(ValidadorPlatillo.esValido(platillo("Baleada sencilla", 35.0, 1)));
        assertTrue(ValidadorPlatillo.validar(platillo("Baleada sencilla", 35.0, 1)).isEmpty());
    }

    @Test
    public void nombreVacio_seDetecta() {
        assertTrue(ValidadorPlatillo.validar(platillo("", 35.0, 1))
                .contains(ErrorPlatillo.NOMBRE_VACIO));
    }

    @Test
    public void nombreSoloEspacios_seDetecta() {
        // El servidor normaliza con btrim: "   " no es un nombre, es un campo vacío.
        assertTrue(ValidadorPlatillo.validar(platillo("   ", 35.0, 1))
                .contains(ErrorPlatillo.NOMBRE_VACIO));
    }

    @Test
    public void precioCero_seDetecta() {
        // Espeja el CHECK (precio > 0): un precio 0 es un error de captura, no un regalo.
        assertTrue(ValidadorPlatillo.validar(platillo("Baleada", 0.0, 1))
                .contains(ErrorPlatillo.PRECIO_NO_POSITIVO));
    }

    @Test
    public void precioNegativo_seDetecta() {
        assertTrue(ValidadorPlatillo.validar(platillo("Baleada", -10.0, 1))
                .contains(ErrorPlatillo.PRECIO_NO_POSITIVO));
    }

    @Test
    public void sinCategoriaElegida_seDetecta() {
        assertTrue(ValidadorPlatillo.validar(platillo("Baleada", 35.0, 0))
                .contains(ErrorPlatillo.SIN_CATEGORIA));
    }

    @Test
    public void formularioEnBlanco_devuelveLosTresErrores() {
        Set<ErrorPlatillo> errores = ValidadorPlatillo.validar(platillo("", 0.0, 0));

        assertEquals(3, errores.size());
        assertTrue(errores.contains(ErrorPlatillo.NOMBRE_VACIO));
        assertTrue(errores.contains(ErrorPlatillo.PRECIO_NO_POSITIVO));
        assertTrue(errores.contains(ErrorPlatillo.SIN_CATEGORIA));
    }

    @Test
    public void platilloNulo_noRevientaYNoEsValido() {
        assertFalse(ValidadorPlatillo.esValido(null));
        assertEquals(3, ValidadorPlatillo.validar((NuevoPlatillo) null).size());
    }
}
