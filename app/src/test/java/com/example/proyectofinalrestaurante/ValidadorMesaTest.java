package com.example.proyectofinalrestaurante;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.domain.ReglasMesa;
import com.example.proyectofinalrestaurante.domain.ValidadorMesa;
import com.example.proyectofinalrestaurante.domain.ValidadorMesa.ErrorMesa;
import com.example.proyectofinalrestaurante.domain.model.NuevaMesa;

import org.junit.Test;

import java.util.Set;

/** Tests de {@link ValidadorMesa} (Plan Fase 2c, E1). */
public class ValidadorMesaTest {

    private static String textoDeLargo(int largo) {
        StringBuilder texto = new StringBuilder(largo);
        for (int i = 0; i < largo; i++) {
            texto.append('a');
        }
        return texto.toString();
    }

    private static NuevaMesa mesa(int numero, int capacidad, String ubicacion) {
        return new NuevaMesa(numero, capacidad, ubicacion);
    }

    @Test
    public void mesaCompleta_esValida() {
        assertTrue(ValidadorMesa.esValido(mesa(4, 6, "Ventana")));
        assertTrue(ValidadorMesa.validar(mesa(4, 6, "Ventana")).isEmpty());
    }

    @Test
    public void mesaSinUbicacion_esValida() {
        // La ubicación es opcional: no hay error por no tenerla.
        assertTrue(ValidadorMesa.esValido(mesa(4, 6, null)));
        assertTrue(ValidadorMesa.validar(mesa(4, 6, null)).isEmpty());
    }

    @Test
    public void numeroCero_seDetecta() {
        assertTrue(ValidadorMesa.validar(mesa(0, 6, null)).contains(ErrorMesa.NUMERO_INVALIDO));
        assertFalse(ValidadorMesa.esValido(mesa(0, 6, null)));
    }

    @Test
    public void numeroNegativo_seDetecta() {
        assertTrue(ValidadorMesa.validar(mesa(-3, 6, null)).contains(ErrorMesa.NUMERO_INVALIDO));
        assertFalse(ValidadorMesa.esValido(mesa(-3, 6, null)));
    }

    @Test
    public void capacidadCero_seDetecta() {
        assertTrue(ValidadorMesa.validar(mesa(4, 0, null)).contains(ErrorMesa.CAPACIDAD_NO_POSITIVA));
        assertFalse(ValidadorMesa.esValido(mesa(4, 0, null)));
    }

    @Test
    public void capacidadNegativa_seDetecta() {
        assertTrue(ValidadorMesa.validar(mesa(4, -2, null)).contains(ErrorMesa.CAPACIDAD_NO_POSITIVA));
        assertFalse(ValidadorMesa.esValido(mesa(4, -2, null)));
    }

    @Test
    public void ubicacionDe101Caracteres_seDetecta() {
        assertTrue(ValidadorMesa.validar(mesa(4, 6, textoDeLargo(101)))
                .contains(ErrorMesa.UBICACION_MUY_LARGA));
        assertFalse(ValidadorMesa.esValido(mesa(4, 6, textoDeLargo(101))));
    }

    @Test
    public void ubicacionDeExactamente100Caracteres_esValida() {
        // El límite es "más de 100" es error; 100 exactos entra.
        assertTrue(ValidadorMesa.esValido(
                mesa(4, 6, textoDeLargo(ReglasMesa.MAX_UBICACION_LONGITUD))));
    }

    @Test
    public void formularioEnBlanco_devuelveLosTresErrores() {
        Set<ErrorMesa> errores = ValidadorMesa.validar(mesa(0, 0, textoDeLargo(101)));

        assertEquals(3, errores.size());
        assertTrue(errores.contains(ErrorMesa.NUMERO_INVALIDO));
        assertTrue(errores.contains(ErrorMesa.CAPACIDAD_NO_POSITIVA));
        assertTrue(errores.contains(ErrorMesa.UBICACION_MUY_LARGA));
        assertFalse(ValidadorMesa.esValido(mesa(0, 0, textoDeLargo(101))));
    }

    @Test
    public void mesaNula_noRevientaYNoEsValida() {
        assertFalse(ValidadorMesa.esValido(null));
        assertEquals(3, ValidadorMesa.validar((NuevaMesa) null).size());
    }
}
