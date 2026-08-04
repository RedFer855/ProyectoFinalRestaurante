package com.example.proyectofinalrestaurante.domain;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.domain.model.Mesa;
import com.example.proyectofinalrestaurante.domain.model.NuevaMesa;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Validación de los campos de una mesa antes de mandarla al servidor (Fase 2c).
 *
 * <p>Devuelve <b>qué</b> está mal, no el texto del error: el mensaje sale de
 * {@code res/values/strings.xml} en {@code ui}. El dominio no puede ver {@code R}.</p>
 *
 * <p>Java puro y sin estado: es la pieza más barata de testear.</p>
 */
public final class ValidadorMesa {

    public enum ErrorMesa {
        NUMERO_NO_POSITIVO,
        CAPACIDAD_NO_POSITIVA
    }

    private ValidadorMesa() {
    }

    public static Set<ErrorMesa> validar(@Nullable NuevaMesa nueva) {
        if (nueva == null) {
            return EnumSet.allOf(ErrorMesa.class);
        }
        return validarCampos(nueva.getNumeroMesa(), nueva.getCapacidad());
    }

    public static Set<ErrorMesa> validar(@Nullable Mesa mesa) {
        if (mesa == null) {
            return EnumSet.allOf(ErrorMesa.class);
        }
        return validarCampos(mesa.getNumeroMesa(), mesa.getCapacidad());
    }

    public static boolean esValido(@Nullable NuevaMesa nueva) {
        return validar(nueva).isEmpty();
    }

    private static Set<ErrorMesa> validarCampos(int numeroMesa, int capacidad) {
        Set<ErrorMesa> errores = EnumSet.noneOf(ErrorMesa.class);

        if (numeroMesa <= 0) {
            errores.add(ErrorMesa.NUMERO_NO_POSITIVO);
        }
        if (capacidad <= 0) {
            errores.add(ErrorMesa.CAPACIDAD_NO_POSITIVA);
        }

        return errores.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(errores);
    }
}
