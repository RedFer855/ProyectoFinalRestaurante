package com.example.proyectofinalrestaurante.domain.model;

import androidx.annotation.Nullable;

/**
 * Los siete valores del dashboard de Inicio (Plan Fase 3c, §5). Inmutable.
 *
 * <p>Seis son locales y nunca fallan (contadores de Room); el séptimo,
 * {@code ventasHoy}, es {@code null} cuando la instantánea del rango {@code HOY} nunca se
 * descargó — la UI debe mostrar "—", <b>nunca</b> {@code L 0.00} (§6 del plan: decir "cero"
 * cuando lo que pasa es "no sé" es el error más caro posible en un dashboard de ventas).
 * {@code ventasHoyGeneradoEn} es la marca de esa instantánea, para el subtítulo "al 14:32".</p>
 */
public final class ResumenInicio {

    private final int pedidosPendientes;
    private final int pedidosEnPreparacion;
    private final int mesasOcupadas;
    private final int mesasTotales;
    private final int clientesRegistrados;
    private final int platillosActivos;
    private final int empleadosActivos;
    @Nullable private final Double ventasHoy;
    @Nullable private final Long ventasHoyGeneradoEn;

    public ResumenInicio(int pedidosPendientes, int pedidosEnPreparacion, int mesasOcupadas,
                         int mesasTotales, int clientesRegistrados, int platillosActivos,
                         int empleadosActivos, @Nullable Double ventasHoy,
                         @Nullable Long ventasHoyGeneradoEn) {
        this.pedidosPendientes = pedidosPendientes;
        this.pedidosEnPreparacion = pedidosEnPreparacion;
        this.mesasOcupadas = mesasOcupadas;
        this.mesasTotales = mesasTotales;
        this.clientesRegistrados = clientesRegistrados;
        this.platillosActivos = platillosActivos;
        this.empleadosActivos = empleadosActivos;
        this.ventasHoy = ventasHoy;
        this.ventasHoyGeneradoEn = ventasHoyGeneradoEn;
    }

    public int getPedidosPendientes() {
        return pedidosPendientes;
    }

    public int getPedidosEnPreparacion() {
        return pedidosEnPreparacion;
    }

    public int getMesasOcupadas() {
        return mesasOcupadas;
    }

    public int getMesasTotales() {
        return mesasTotales;
    }

    public int getClientesRegistrados() {
        return clientesRegistrados;
    }

    public int getPlatillosActivos() {
        return platillosActivos;
    }

    public int getEmpleadosActivos() {
        return empleadosActivos;
    }

    @Nullable
    public Double getVentasHoy() {
        return ventasHoy;
    }

    @Nullable
    public Long getVentasHoyGeneradoEn() {
        return ventasHoyGeneradoEn;
    }
}
