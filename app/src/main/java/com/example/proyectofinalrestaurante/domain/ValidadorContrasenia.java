package com.example.proyectofinalrestaurante.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Valida la fuerza de una contraseña con las mismas reglas que la política de
 * Supabase (Authentication → Policies, tarea S-2 del Plan Fase 1b): mínimo 8
 * caracteres, al menos una mayúscula, una minúscula, un dígito y un símbolo.
 *
 * <p>Java puro, sin dependencias de Android — es la pieza del plan Fase 1b
 * 100 % testeable con JUnit. La validación de acá es UX; la que manda es la
 * del servidor.</p>
 */
public final class ValidadorContrasenia {

    public static final int LONGITUD_MINIMA = 8;

    private ValidadorContrasenia() {
    }

    /**
     * Valida una contraseña. {@code null} y la cadena vacía se tratan igual:
     * todos los requisitos incumplidos.
     */
    public static ResultadoValidacion validar(String contrasenia) {
        Set<RequisitoContrasenia> incumplidos = EnumSet.noneOf(RequisitoContrasenia.class);

        if (contrasenia == null || contrasenia.length() < LONGITUD_MINIMA) {
            incumplidos.add(RequisitoContrasenia.LONGITUD_MINIMA);
        }

        boolean tieneMayuscula = false;
        boolean tieneMinuscula = false;
        boolean tieneDigito = false;
        boolean tieneSimbolo = false;

        if (contrasenia != null) {
            for (int i = 0; i < contrasenia.length(); i++) {
                char c = contrasenia.charAt(i);
                if (Character.isUpperCase(c)) {
                    tieneMayuscula = true;
                } else if (Character.isLowerCase(c)) {
                    tieneMinuscula = true;
                } else if (Character.isDigit(c)) {
                    tieneDigito = true;
                } else {
                    tieneSimbolo = true;
                }
            }
        }

        if (!tieneMayuscula) {
            incumplidos.add(RequisitoContrasenia.MAYUSCULA);
        }
        if (!tieneMinuscula) {
            incumplidos.add(RequisitoContrasenia.MINUSCULA);
        }
        if (!tieneDigito) {
            incumplidos.add(RequisitoContrasenia.DIGITO);
        }
        if (!tieneSimbolo) {
            incumplidos.add(RequisitoContrasenia.SIMBOLO);
        }

        return new ResultadoValidacion(incumplidos);
    }
}
