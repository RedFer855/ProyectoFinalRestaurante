package com.example.proyectofinalrestaurante.ui.pedidos;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.model.EstadoPedido;

/**
 * Traducción de presentación de {@link EstadoPedido}: qué etiqueta y qué color pinta la UI.
 * Las etiquetas y colores son recursos (regla de oro #8); el dominio es Java puro y no sabe
 * de recursos — este helper cierra esa brecha, mismo rol que el viejo
 * {@code DatosMaqueta.EstadoPedido}.
 *
 * <p>{@code null} (estado que este APK no conoce, ver {@link EstadoPedido#porId}) se pinta
 * como "Desconocido" con color neutro: no se mapea a un estado conocido por error.</p>
 */
final class EstadoPedidoUi {

    private EstadoPedidoUi() {
    }

    @StringRes
    static int etiqueta(@Nullable EstadoPedido estado) {
        if (estado == null) {
            return R.string.estado_pedido_desconocido;
        }
        switch (estado) {
            case EN_PREPARACION:
                return R.string.estado_pedido_preparacion;
            case LISTO:
                return R.string.estado_pedido_listo;
            case ENTREGADO:
                return R.string.estado_pedido_entregado;
            case CANCELADO:
                return R.string.estado_pedido_cancelado;
            case PENDIENTE:
            default:
                return R.string.estado_pedido_pendiente;
        }
    }

    @ColorRes
    static int color(@Nullable EstadoPedido estado) {
        if (estado == null) {
            return R.color.brand_outline;
        }
        switch (estado) {
            case EN_PREPARACION:
                return R.color.estado_preparacion;
            case LISTO:
                return R.color.estado_listo;
            case ENTREGADO:
                return R.color.estado_entregado;
            case CANCELADO:
                return R.color.estado_cancelado;
            case PENDIENTE:
            default:
                return R.color.estado_pendiente;
        }
    }
}