package com.example.proyectofinalrestaurante;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.domain.RequisitoContrasenia;
import com.example.proyectofinalrestaurante.domain.ResultadoValidacion;
import com.example.proyectofinalrestaurante.domain.ValidadorContrasenia;

import org.junit.Test;

/** Tests de {@link ValidadorContrasenia} — Java puro, sin red ni Android. */
public class ValidadorContraseniaTest {

    @Test
    public void contraseniaValida_cumpleTodo() {
        ResultadoValidacion r = ValidadorContrasenia.validar("Clave123!");
        assertTrue(r.esValida());
        assertTrue(r.getIncumplidos().isEmpty());
    }

    @Test
    public void demasiadoCorta_incumpleLongitud() {
        ResultadoValidacion r = ValidadorContrasenia.validar("A1!c");
        assertFalse(r.esValida());
        assertFalse(r.cumple(RequisitoContrasenia.LONGITUD_MINIMA));
    }

    @Test
    public void sinMayuscula_incumpleMayuscula() {
        ResultadoValidacion r = ValidadorContrasenia.validar("clave123!");
        assertFalse(r.esValida());
        assertFalse(r.cumple(RequisitoContrasenia.MAYUSCULA));
    }

    @Test
    public void sinMinuscula_incumpleMinuscula() {
        ResultadoValidacion r = ValidadorContrasenia.validar("CLAVE123!");
        assertFalse(r.esValida());
        assertFalse(r.cumple(RequisitoContrasenia.MINUSCULA));
    }

    @Test
    public void sinDigito_incumpleDigito() {
        ResultadoValidacion r = ValidadorContrasenia.validar("ClaveClave!");
        assertFalse(r.esValida());
        assertFalse(r.cumple(RequisitoContrasenia.DIGITO));
    }

    @Test
    public void sinSimbolo_incumpleSimbolo() {
        ResultadoValidacion r = ValidadorContrasenia.validar("Clave1234");
        assertFalse(r.esValida());
        assertFalse(r.cumple(RequisitoContrasenia.SIMBOLO));
    }

    @Test
    public void vacia_incumpleTodosLosRequisitos() {
        ResultadoValidacion r = ValidadorContrasenia.validar("");
        assertFalse(r.esValida());
        assertTrue(r.getIncumplidos().size() == RequisitoContrasenia.values().length);
    }

    @Test
    public void nula_incumpleTodosLosRequisitos() {
        ResultadoValidacion r = ValidadorContrasenia.validar(null);
        assertFalse(r.esValida());
        assertTrue(r.getIncumplidos().size() == RequisitoContrasenia.values().length);
    }

    @Test
    public void contraseniaJustaAlLimite_cumple() {
        ResultadoValidacion r = ValidadorContrasenia.validar("A1b2c3d!");
        assertTrue(r.esValida());
    }
}
