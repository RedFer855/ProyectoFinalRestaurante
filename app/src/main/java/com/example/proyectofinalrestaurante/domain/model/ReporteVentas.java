package com.example.proyectofinalrestaurante.domain.model;

import java.util.Collections;
import java.util.List;

/**
 * Instantánea del RPC {@code reporte_ventas(p_rango)} para un {@link RangoReporte} (Plan Fase
 * 3c, §4.1). Inmutable — es la misma fila que alimenta tanto la pantalla de Reportes como la
 * tarjeta "Ventas de hoy" del dashboard (§5 del plan: un solo agregado en todo el proyecto).
 *
 * <p>{@code generadoEnEpochMillis} es la marca {@code generado_en} que manda el servidor,
 * parseada a epoch millis: es la que {@link com.example.proyectofinalrestaurante.domain.ReglasReporte#esVieja}
 * compara contra el reloj para decidir si la instantánea sigue vigente.</p>
 */
public final class ReporteVentas {

    private final RangoReporte rango;
    private final long generadoEnEpochMillis;
    private final double totalVentas;
    private final int cantidadPedidos;
    private final double ticketPromedio;
    private final List<ConteoPlatillo> topPlatillos;
    private final List<DesempenoMesero> desempenoMeseros;

    public ReporteVentas(RangoReporte rango, long generadoEnEpochMillis, double totalVentas,
                         int cantidadPedidos, double ticketPromedio,
                         List<ConteoPlatillo> topPlatillos,
                         List<DesempenoMesero> desempenoMeseros) {
        this.rango = rango;
        this.generadoEnEpochMillis = generadoEnEpochMillis;
        this.totalVentas = totalVentas;
        this.cantidadPedidos = cantidadPedidos;
        this.ticketPromedio = ticketPromedio;
        this.topPlatillos = topPlatillos == null
                ? Collections.emptyList() : Collections.unmodifiableList(topPlatillos);
        this.desempenoMeseros = desempenoMeseros == null
                ? Collections.emptyList() : Collections.unmodifiableList(desempenoMeseros);
    }

    public RangoReporte getRango() {
        return rango;
    }

    public long getGeneradoEnEpochMillis() {
        return generadoEnEpochMillis;
    }

    public double getTotalVentas() {
        return totalVentas;
    }

    public int getCantidadPedidos() {
        return cantidadPedidos;
    }

    public double getTicketPromedio() {
        return ticketPromedio;
    }

    public List<ConteoPlatillo> getTopPlatillos() {
        return topPlatillos;
    }

    public List<DesempenoMesero> getDesempenoMeseros() {
        return desempenoMeseros;
    }
}
