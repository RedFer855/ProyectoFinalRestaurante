package com.example.proyectofinalrestaurante.domain;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.domain.model.NuevaMesa;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Validación de los campos de una mesa antes de mandarlos al servidor (Plan Fase 2c, E1).
 *
 * <p>Devuelve <b>qué</b> está mal, no el texto del error: el mensaje sale de
 * {@code res/values/strings.xml} en {@code ui}. El dominio no puede ver {@code R}.</p>
 *
 * <p>Java puro y sin estado: es la pieza más barata de testear de todo el módulo.</p>
 */
public final class ValidadorMesa {

    /** Cada valor es un error concreto que {@code ui} pinta en el campo que corresponde. */
    public enum ErrorMesa {
        NUMERO_INVALIDO,
        CAPACIDAD_NO_POSITIVA,
        UBICACION_MUY_LARGA
    }

    private ValidadorMesa() {
    }

    public static Set<ErrorMesa> validar(@Nullable NuevaMesa nueva) {
        if (nueva == null) {
            return EnumSet.allOf(ErrorMesa.class);
        }
        return validarCampos(nueva.getNumeroMesa(), nueva.getCapacidad(), nueva.getUbicacion());
    }

    public static boolean esValido(@Nullable NuevaMesa nueva) {
        return validar(nueva).isEmpty();
    }

    private static Set<ErrorMesa> validarCampos(int numeroMesa, int capacidad,
                                                @Nullable String ubicacion) {
        Set<ErrorMesa> errores = EnumSet.noneOf(ErrorMesa.class);

        // El servidor tiene un único sobre numero_mesa (Plan Fase 2c, §2.2): 0 es una mesa
        // sin número, no un caso válido.
        if (numeroMesa <= 0) {
            errores.add(ErrorMesa.NUMERO_INVALIDO);
        }
        // Una mesa sin capacidad no tiene dónde sentar gente: 0 es un error de captura.
        if (capacidad <= 0) {
            errores.add(ErrorMesa.CAPACIDAD_NO_POSITIVA);
        }
        // La ubicación es opcional; si viene, no puede pasarse del límite del servidor.
        if (ubicacion != null && ubicacion.length() > ReglasMesa.MAX_UBICACION_LONGITUD) {
            errores.add(ErrorMesa.UBICACION_MUY_LARGA);
        }

        return errores.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(errores);
    }
}
