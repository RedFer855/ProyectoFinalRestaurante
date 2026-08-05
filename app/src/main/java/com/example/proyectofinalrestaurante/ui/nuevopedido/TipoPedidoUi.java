package com.example.proyectofinalrestaurante.ui.nuevopedido;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.model.TipoPedido;

/**
 * Traducción de presentación de {@link TipoPedido}: qué etiqueta de {@code strings.xml} pinta
 * el selector. Mismo molde que {@code EstadoPedidoUi}: el dominio es Java puro, la etiqueta es
 * un recurso (regla de oro #8).
 */
final class TipoPedidoUi {

    private TipoPedidoUi() {
    }

    @StringRes
    static int etiqueta(@Nullable TipoPedido tipo) {
        if (tipo == null) {
            return R.string.tipo_pedido_en_mesa;
        }
        switch (tipo) {
            case PARA_LLEVAR:
                return R.string.tipo_pedido_para_llevar;
            case EN_MESA:
            default:
                return R.string.tipo_pedido_en_mesa;
        }
    }
}