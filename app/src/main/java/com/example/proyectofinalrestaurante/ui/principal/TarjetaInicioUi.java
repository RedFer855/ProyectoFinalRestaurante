package com.example.proyectofinalrestaurante.ui.principal;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.example.proyectofinalrestaurante.R;

/**
 * Traduce {@link TarjetaInicio.Tipo} a icono y etiqueta (Plan Fase 3c, E9). Mismo rol que
 * {@code EstadoMesaUi} para {@code EstadoMesa}: la capa de presentación vive acá, no en
 * {@code domain}.
 */
public final class TarjetaInicioUi {

    private TarjetaInicioUi() {
    }

    @DrawableRes
    public static int icono(TarjetaInicio.Tipo tipo) {
        switch (tipo) {
            case PEDIDOS_PENDIENTES:
            case PEDIDOS_EN_PREPARACION:
                return R.drawable.ic_pedidos;
            case MESAS_OCUPADAS:
                return R.drawable.ic_mesas;
            case CLIENTES_REGISTRADOS:
                return R.drawable.ic_clientes;
            case PLATILLOS_ACTIVOS:
                return R.drawable.ic_menu;
            case VENTAS_HOY:
                return R.drawable.ic_reportes;
            case EMPLEADOS_ACTIVOS:
                return R.drawable.ic_empleados;
            default:
                throw new IllegalArgumentException("Tipo de tarjeta desconocido: " + tipo);
        }
    }

    @StringRes
    public static int etiqueta(TarjetaInicio.Tipo tipo) {
        switch (tipo) {
            case PEDIDOS_PENDIENTES:
                return R.string.inicio_pedidos_pendientes;
            case PEDIDOS_EN_PREPARACION:
                return R.string.inicio_pedidos_preparacion;
            case MESAS_OCUPADAS:
                return R.string.inicio_mesas_ocupadas;
            case CLIENTES_REGISTRADOS:
                return R.string.inicio_clientes_registrados;
            case PLATILLOS_ACTIVOS:
                return R.string.inicio_platillos_activos;
            case VENTAS_HOY:
                return R.string.inicio_ventas_hoy;
            case EMPLEADOS_ACTIVOS:
                return R.string.inicio_empleados_activos;
            default:
                throw new IllegalArgumentException("Tipo de tarjeta desconocido: " + tipo);
        }
    }
}
