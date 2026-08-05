package com.example.proyectofinalrestaurante.ui.principal;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.R;

/**
 * Una tarjeta del dashboard de Inicio, ya resuelta por permiso (Plan Fase 3c, E9). Vive en
 * {@code ui} y no en {@code domain} porque carga el {@code menuId} de navegación
 * ({@code R.id.nav_*}) — el valor que muestra sigue siendo un número/monto crudo, nunca un
 * string formateado: formatear con {@code getString(...)} necesita {@code Resources}, y el
 * ViewModel que arma esta lista no puede tocar {@code Context} (regla de oro #5). El formateo
 * final es responsabilidad del adapter, vía {@link TarjetaInicioUi}.
 */
public final class TarjetaInicio {

    public enum Tipo {
        PEDIDOS_PENDIENTES,
        PEDIDOS_EN_PREPARACION,
        MESAS_OCUPADAS,
        CLIENTES_REGISTRADOS,
        PLATILLOS_ACTIVOS,
        VENTAS_HOY,
        EMPLEADOS_ACTIVOS
    }

    private final Tipo tipo;
    @IdRes private final int menuId;
    private final int valorPrincipal;
    private final int valorSecundario;
    @Nullable private final Double montoOpcional;
    @Nullable private final Long generadoEnOpcional;

    private TarjetaInicio(Tipo tipo, @IdRes int menuId, int valorPrincipal, int valorSecundario,
                          @Nullable Double montoOpcional, @Nullable Long generadoEnOpcional) {
        this.tipo = tipo;
        this.menuId = menuId;
        this.valorPrincipal = valorPrincipal;
        this.valorSecundario = valorSecundario;
        this.montoOpcional = montoOpcional;
        this.generadoEnOpcional = generadoEnOpcional;
    }

    public static TarjetaInicio pedidosPendientes(int cantidad) {
        return new TarjetaInicio(Tipo.PEDIDOS_PENDIENTES, R.id.nav_pedidos, cantidad, 0, null, null);
    }

    public static TarjetaInicio pedidosEnPreparacion(int cantidad) {
        return new TarjetaInicio(Tipo.PEDIDOS_EN_PREPARACION, R.id.nav_pedidos, cantidad, 0, null, null);
    }

    public static TarjetaInicio mesasOcupadas(int ocupadas, int totales) {
        return new TarjetaInicio(Tipo.MESAS_OCUPADAS, R.id.nav_mesas, ocupadas, totales, null, null);
    }

    public static TarjetaInicio clientesRegistrados(int cantidad) {
        return new TarjetaInicio(Tipo.CLIENTES_REGISTRADOS, R.id.nav_clientes, cantidad, 0, null, null);
    }

    public static TarjetaInicio platillosActivos(int cantidad) {
        return new TarjetaInicio(Tipo.PLATILLOS_ACTIVOS, R.id.nav_menu, cantidad, 0, null, null);
    }

    /** {@code monto}/{@code generadoEn} nulos: la instantánea de HOY nunca se descargó. */
    public static TarjetaInicio ventasHoy(@Nullable Double monto, @Nullable Long generadoEn) {
        return new TarjetaInicio(Tipo.VENTAS_HOY, R.id.nav_reportes, 0, 0, monto, generadoEn);
    }

    public static TarjetaInicio empleadosActivos(int cantidad) {
        return new TarjetaInicio(Tipo.EMPLEADOS_ACTIVOS, R.id.nav_empleados, cantidad, 0, null, null);
    }

    public Tipo getTipo() {
        return tipo;
    }

    @IdRes
    public int getMenuId() {
        return menuId;
    }

    public int getValorPrincipal() {
        return valorPrincipal;
    }

    public int getValorSecundario() {
        return valorSecundario;
    }

    @Nullable
    public Double getMontoOpcional() {
        return montoOpcional;
    }

    @Nullable
    public Long getGeneradoEnOpcional() {
        return generadoEnOpcional;
    }
}
