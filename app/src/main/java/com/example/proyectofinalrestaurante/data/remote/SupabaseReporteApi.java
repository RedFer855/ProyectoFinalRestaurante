package com.example.proyectofinalrestaurante.data.remote;

import com.example.proyectofinalrestaurante.data.remote.dto.RangoReporteDto;
import com.example.proyectofinalrestaurante.data.remote.dto.ReporteVentasDto;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

/**
 * Endpoint PostgREST del módulo Reportes (Plan Fase 3c, §4.1): un único RPC de solo lectura,
 * sin equivalente de escritura — a diferencia de {@link SupabaseMesaApi} o
 * {@link SupabasePedidoApi}, este módulo no tiene tabla propia que el cliente modifique.
 *
 * <p>El header {@code apikey} lo pone el interceptor de {@code SupabaseClient}; el
 * {@code Authorization} lo pone {@code ReporteRemoto}, igual que en el resto de los módulos.</p>
 */
public interface SupabaseReporteApi {

    @POST("rest/v1/rpc/reporte_ventas")
    Call<ReporteVentasDto> reporteVentas(
            @Header("Authorization") String bearerToken,
            @Body RangoReporteDto cuerpo);
}
