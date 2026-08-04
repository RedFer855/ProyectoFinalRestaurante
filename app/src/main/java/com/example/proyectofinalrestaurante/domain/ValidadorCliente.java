package com.example.proyectofinalrestaurante.domain;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.domain.model.Cliente;
import com.example.proyectofinalrestaurante.domain.model.NuevoCliente;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Validación de los campos de un cliente antes de mandarlo al servidor (Fase 2d).
 *
 * <p>Nombre y apellido son obligatorios; identidad y teléfono son opcionales. Si la
 * identidad viene, se normaliza (sin guiones ni espacios) y se verifica que tenga al
 * menos 13 dígitos (formato hondureño).</p>
 *
 * <p>Java puro y sin estado: trivial de testear.</p>
 */
public final class ValidadorCliente {

    public enum ErrorCliente {
        NOMBRE_VACIO,
        APELLIDO_VACIO,
        IDENTIDAD_MUY_CORTA
    }

    private static final int LONGITUD_MINIMA_IDENTIDAD = 13;

    private ValidadorCliente() {
    }

    public static Set<ErrorCliente> validar(@Nullable NuevoCliente nuevo) {
        if (nuevo == null) {
            return EnumSet.allOf(ErrorCliente.class);
        }
        return validarCampos(nuevo.getNombre(), nuevo.getApellido(), nuevo.getIdentidad());
    }

    public static Set<ErrorCliente> validar(@Nullable Cliente cliente) {
        if (cliente == null) {
            return EnumSet.allOf(ErrorCliente.class);
        }
        return validarCampos(cliente.getNombre(), cliente.getApellido(), cliente.getIdentidad());
    }

    public static boolean esValido(@Nullable NuevoCliente nuevo) {
        return validar(nuevo).isEmpty();
    }

    private static Set<ErrorCliente> validarCampos(String nombre, String apellido,
                                                   @Nullable String identidad) {
        Set<ErrorCliente> errores = EnumSet.noneOf(ErrorCliente.class);

        if (nombre == null || nombre.trim().isEmpty()) {
            errores.add(ErrorCliente.NOMBRE_VACIO);
        }
        if (apellido == null || apellido.trim().isEmpty()) {
            errores.add(ErrorCliente.APELLIDO_VACIO);
        }
        if (identidad != null && !identidad.trim().isEmpty()) {
            String normalizada = ReglasCliente.normalizarIdentidad(identidad);
            if (normalizada.length() < LONGITUD_MINIMA_IDENTIDAD) {
                errores.add(ErrorCliente.IDENTIDAD_MUY_CORTA);
            }
        }

        return errores.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(errores);
    }
}
