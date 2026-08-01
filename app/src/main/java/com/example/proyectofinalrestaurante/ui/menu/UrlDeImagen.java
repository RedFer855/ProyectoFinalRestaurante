package com.example.proyectofinalrestaurante.ui.menu;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.BuildConfig;

/**
 * Arma la URL pública de la foto de un platillo a partir de la ruta guardada en la base.
 *
 * <p>La fila guarda {@code a3f9….jpg} y no la URL completa: si mañana cambia el proyecto,
 * el dominio o el nombre del bucket, todas las filas quedarían apuntando a la nada. La URL
 * se arma acá, en {@code ui}, y en un solo lugar — {@code domain} no puede conocer al
 * proveedor de almacenamiento.</p>
 *
 * <p>No hace falta token: el bucket {@code platillos} es público de lectura. Ver la
 * decisión documentada en el Plan Fase 2a, §2.5.</p>
 */
public final class UrlDeImagen {

    private static final String PREFIJO_PUBLICO = "/storage/v1/object/public/platillos/";

    private UrlDeImagen() {
    }

    /**
     * URL pública de la foto, o {@code null} si el platillo no tiene ninguna — que es lo
     * que hace que el adapter muestre el <i>placeholder</i>.
     */
    @Nullable
    public static String urlDePlatillo(@Nullable String rutaImagen) {
        if (rutaImagen == null || rutaImagen.trim().isEmpty()) {
            return null;
        }
        if (BuildConfig.SUPABASE_URL.isEmpty()) {
            return null;
        }
        return BuildConfig.SUPABASE_URL + PREFIJO_PUBLICO + rutaImagen.trim();
    }
}
