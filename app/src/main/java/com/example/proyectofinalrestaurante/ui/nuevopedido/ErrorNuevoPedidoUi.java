package com.example.proyectofinalrestaurante.ui.nuevopedido;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.ValidadorPedido;

/**
 * Traducción de presentación de {@link ValidadorPedido.ErrorPedido}: qué mensaje de
 * {@code strings.xml} pinta la UI. Mismo molde que {@code EstadoPedidoUi}: el dominio es Java
 * puro y no sabe de recursos; este helper cierra esa brecha. Los tres mensajes ya existen
 * desde la E7.
 */
final class ErrorNuevoPedidoUi {

    private ErrorNuevoPedidoUi() {
    }

    @StringRes
    static int mensaje(@Nullable ValidadorPedido.ErrorPedido error) {
        if (error == null) {
            return R.string.nuevo_pedido_error_carrito_vacio;
        }
        switch (error) {
            case CARRITO_VACIO:
                return R.string.nuevo_pedido_error_carrito_vacio;
            case DEMASIADAS_LINEAS:
                return R.string.nuevo_pedido_error_demasiadas_lineas;
            case CANTIDAD_INVALIDA:
            default:
                return R.string.nuevo_pedido_error_cantidad_invalida;
        }
    }
}