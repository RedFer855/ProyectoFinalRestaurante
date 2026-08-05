package com.example.proyectofinalrestaurante.ui.comun;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Formatea una marca epoch millis para las franjas "datos al …" de Reportes e Inicio (Plan
 * Fase 3c, §6). Vive en {@code ui} porque {@code SimpleDateFormat} es una dependencia que
 * {@code domain} no puede tener (regla de oro #1).
 */
public final class FormateadorFecha {

    private FormateadorFecha() {
    }

    /** "5 ago, 14:32", en el locale del dispositivo — la franja larga de Reportes. */
    public static String fechaHoraCorta(long epochMillis) {
        return new SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(new Date(epochMillis));
    }

    /** "14:32" — el subtítulo corto de la tarjeta "Ventas de hoy" del dashboard. */
    public static String horaCorta(long epochMillis) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(epochMillis));
    }
}
