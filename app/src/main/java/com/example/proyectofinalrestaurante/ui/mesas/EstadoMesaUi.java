package com.example.proyectofinalrestaurante.ui.mesas;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.model.EstadoMesa;

/**
 * Etiqueta y color de cada {@link EstadoMesa} (Plan Fase 2c, E6). El dominio deliberadamente
 * no sabe de {@code R}: {@link EstadoMesa} documenta que "las etiquetas y colores que la UI
 * pinta viven en la capa de presentación, no acá" — esta clase es esa capa.
 *
 * <p>Mismos tres colores que ya usaba la maqueta de la Fase 1c, tomados de la paleta
 * compartida de estados (nunca hardcodeados) — ver [[Guía de Diseño Visual]].</p>
 */
final class EstadoMesaUi {

    private EstadoMesaUi() {
    }

    @StringRes
    static int etiqueta(@Nullable EstadoMesa estado) {
        if (estado == null) {
            return R.string.mesas_de_baja;
        }
        switch (estado) {
            case LIBRE:
                return R.string.estado_mesa_libre;
            case OCUPADA:
                return R.string.estado_mesa_ocupada;
            default:
                return R.string.estado_mesa_reservada;
        }
    }

    @ColorRes
    static int color(@Nullable EstadoMesa estado) {
        if (estado == null) {
            return R.color.estado_entregado;
        }
        switch (estado) {
            case LIBRE:
                return R.color.estado_listo;
            case OCUPADA:
                return R.color.estado_cancelado;
            default:
                return R.color.estado_pendiente;
        }
    }
}
