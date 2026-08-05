package com.example.proyectofinalrestaurante.data.remote.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Cuerpo de {@code POST rest/v1/rpc/reporte_ventas} (Plan Fase 3c, §4.1): el único argumento
 * del RPC, con su nombre exacto {@code p_rango}. El rango lo calcula el servidor en
 * {@code America/Tegucigalpa} — el cliente solo manda {@code "HOY"}/{@code "SEMANA"}/{@code "MES"}.
 */
public final class RangoReporteDto {

    @SerializedName("p_rango")
    private final String pRango;

    public RangoReporteDto(String pRango) {
        this.pRango = pRango;
    }
}
