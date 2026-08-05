package com.example.proyectofinalrestaurante.ui.reportes;

import androidx.annotation.StringRes;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.model.RangoReporte;

/** Traduce {@link RangoReporte} a la etiqueta de su chip (Plan Fase 3c, E10). */
public final class RangoReporteUi {

    private RangoReporteUi() {
    }

    @StringRes
    public static int etiqueta(RangoReporte rango) {
        switch (rango) {
            case HOY:
                return R.string.reportes_rango_hoy;
            case SEMANA:
                return R.string.reportes_rango_semana;
            case MES:
                return R.string.reportes_rango_mes;
            default:
                throw new IllegalArgumentException("Rango desconocido: " + rango);
        }
    }
}
