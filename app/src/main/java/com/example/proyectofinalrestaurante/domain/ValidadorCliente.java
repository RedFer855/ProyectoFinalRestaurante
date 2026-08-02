package com.example.proyectofinalrestaurante.domain;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.domain.model.NuevoCliente;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Validación de los campos de un cliente antes de mandarlos al servidor (Plan Fase 2d, E1).
 *
 * <p>Devuelve <b>qué</b> está mal, no el texto del error: el mensaje sale de
 * {@code res/values/strings.xml} en {@code ui}. Java puro y sin estado.</p>
 */
public final class ValidadorCliente {

    public enum ErrorCliente {
        NOMBRE_OBLIGATORIO,
        APELLIDO_OBLIGATORIO,
        IDENTIDAD_INVALIDA
    }

    private ValidadorCliente() {
    }

    public static Set<ErrorCliente> validar(@Nullable NuevoCliente nuevo) {
        if (nuevo == null) {
            return EnumSet.of(ErrorCliente.NOMBRE_OBLIGATORIO, ErrorCliente.APELLIDO_OBLIGATORIO);
        }
        return validarCampos(nuevo.getNombre(), nuevo.getApellido(), nuevo.getIdentidad());
    }

    public static boolean esValido(@Nullable NuevoCliente nuevo) {
        return validar(nuevo).isEmpty();
    }

    private static Set<ErrorCliente> validarCampos(@Nullable String nombre,
                                                    @Nullable String apellido,
                                                    @Nullable String identidad) {
        Set<ErrorCliente> errores = EnumSet.noneOf(ErrorCliente.class);

        if (nombre == null || nombre.trim().isEmpty()) {
            errores.add(ErrorCliente.NOMBRE_OBLIGATORIO);
        }
        if (apellido == null || apellido.trim().isEmpty()) {
            errores.add(ErrorCliente.APELLIDO_OBLIGATORIO);
        }
        // La identidad es opcional (venta de mostrador, ADR-006); si viene, se normaliza y
        // se exige el largo del documento hondureño — nunca se rechaza por el formato del
        // guion (Plan Fase 2d, §4).
        String normalizada = ReglasCliente.normalizarIdentidad(identidad);
        if (normalizada != null && normalizada.length() < ReglasCliente.DIGITOS_IDENTIDAD) {
            errores.add(ErrorCliente.IDENTIDAD_INVALIDA);
        }

        return errores.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(errores);
    }
}
