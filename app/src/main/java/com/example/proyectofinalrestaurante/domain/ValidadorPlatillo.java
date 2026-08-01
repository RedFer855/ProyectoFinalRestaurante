package com.example.proyectofinalrestaurante.domain;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.domain.model.NuevoPlatillo;
import com.example.proyectofinalrestaurante.domain.model.Platillo;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Validación de los campos de un platillo antes de mandarlo al servidor (Plan Fase 2a).
 *
 * <p>Devuelve <b>qué</b> está mal, no el texto del error: el mensaje sale de
 * {@code res/values/strings.xml} en {@code ui}. El dominio no puede ver {@code R}, y aun
 * si pudiera, un validador que devuelve texto no se puede traducir.</p>
 *
 * <p>Java puro y sin estado: es la pieza más barata de testear de todo el módulo.</p>
 */
public final class ValidadorPlatillo {

    /** Cada valor es un error concreto que {@code ui} pinta en el campo que corresponde. */
    public enum ErrorPlatillo {
        NOMBRE_VACIO,
        PRECIO_NO_POSITIVO,
        SIN_CATEGORIA
    }

    /** Ninguna categoría real tiene id 0; es el valor del dropdown sin elegir. */
    public static final int SIN_CATEGORIA_ELEGIDA = 0;

    private ValidadorPlatillo() {
    }

    public static Set<ErrorPlatillo> validar(@Nullable NuevoPlatillo nuevo) {
        if (nuevo == null) {
            return EnumSet.allOf(ErrorPlatillo.class);
        }
        return validarCampos(nuevo.getNombre(), nuevo.getPrecio(), nuevo.getIdCategoria());
    }

    public static Set<ErrorPlatillo> validar(@Nullable Platillo platillo) {
        if (platillo == null) {
            return EnumSet.allOf(ErrorPlatillo.class);
        }
        return validarCampos(platillo.getNombre(), platillo.getPrecio(),
                platillo.getIdCategoria());
    }

    public static boolean esValido(@Nullable NuevoPlatillo nuevo) {
        return validar(nuevo).isEmpty();
    }

    private static Set<ErrorPlatillo> validarCampos(@Nullable String nombre, double precio,
                                                    int idCategoria) {
        Set<ErrorPlatillo> errores = EnumSet.noneOf(ErrorPlatillo.class);

        if (nombre == null || nombre.trim().isEmpty()) {
            errores.add(ErrorPlatillo.NOMBRE_VACIO);
        }
        // El servidor tiene un CHECK (precio > 0): un precio 0 no es un platillo gratis,
        // es un error de captura. Se atrapa acá para no gastar un viaje de red.
        if (!(precio > 0)) {
            errores.add(ErrorPlatillo.PRECIO_NO_POSITIVO);
        }
        if (idCategoria <= SIN_CATEGORIA_ELEGIDA) {
            errores.add(ErrorPlatillo.SIN_CATEGORIA);
        }

        return errores.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(errores);
    }
}
