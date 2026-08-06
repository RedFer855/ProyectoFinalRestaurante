package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Cuerpo {@code jsonb} que devuelve el RPC {@code reporte_ventas(p_rango)} (Plan Fase 3c,
 * §4.1): una instantánea atómica y coherente en un solo viaje, en vez de cuatro RPCs
 * separados. {@code generadoEn} es lo que la UI muestra como "datos al …" — importa la edad
 * del <b>dato</b>, no la del archivo local.
 */
public final class ReporteVentasDto {

    @SerializedName("generado_en")
    private String generadoEn;

    @SerializedName("total_ventas")
    private double totalVentas;

    @SerializedName("cantidad_pedidos")
    private int cantidadPedidos;

    @SerializedName("ticket_promedio")
    private double ticketPromedio;

    @SerializedName("top_platillos")
    private List<ConteoPlatilloDto> topPlatillos;

    @SerializedName("desempeno_meseros")
    private List<DesempenoMeseroDto> desempenoMeseros;

    public String getGeneradoEn() {
        return generadoEn;
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

    public List<ConteoPlatilloDto> getTopPlatillos() {
        return topPlatillos;
    }

    public List<DesempenoMeseroDto> getDesempenoMeseros() {
        return desempenoMeseros;
    }
}
