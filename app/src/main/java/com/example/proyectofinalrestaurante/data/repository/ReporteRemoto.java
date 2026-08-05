package com.example.proyectofinalrestaurante.data.repository;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.data.outbox.ClasificadorDeError;
import com.example.proyectofinalrestaurante.data.remote.SupabaseReporteApi;
import com.example.proyectofinalrestaurante.data.remote.dto.RangoReporteDto;
import com.example.proyectofinalrestaurante.data.remote.dto.ReporteVentasDto;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.function.Supplier;

import retrofit2.Response;

/**
 * Ejecutor de la única llamada de red del módulo Reportes (Plan Fase 3c, §7.2). Mismo rol que
 * {@link MesaRemoto}, pero sin escrituras: el RPC {@code reporte_ventas} es de solo lectura,
 * así que no hay {@code ClasificadorDeError} decidiendo si reintentar — el llamador
 * ({@code ReporteRepositorioLocal}) ya decide con {@code ReglasReporte.esVieja} cuándo vale la
 * pena llamar.
 */
public final class ReporteRemoto {

    static final String SIN_CONEXION = "Sin conexión al servidor. Intentá de nuevo.";
    static final String SIN_PERMISO_RED =
            "La app no tiene permiso de red. Contactá al desarrollador.";
    static final String SIN_SESION = "Tu sesión venció. Volvé a iniciar sesión.";

    private final SupabaseReporteApi api;
    private final Supplier<String> proveedorToken;
    private final Gson gson = new Gson();

    public ReporteRemoto(SupabaseReporteApi api, Supplier<String> proveedorToken) {
        this.api = api;
        this.proveedorToken = proveedorToken;
    }

    @Nullable
    private String bearer() {
        String token = proveedorToken.get();
        return token == null ? null : "Bearer " + token;
    }

    public ResultadoRed<ReporteVentasDto> reporteVentas(String rango) {
        String bearer = bearer();
        if (bearer == null) {
            return ResultadoRed.fallo(401, SIN_SESION);
        }
        try {
            Response<ReporteVentasDto> respuesta =
                    api.reporteVentas(bearer, new RangoReporteDto(rango)).execute();
            if (!respuesta.isSuccessful() || respuesta.body() == null) {
                return ResultadoRed.fallo(respuesta.code(),
                        mensajeDeError(respuesta, "No se pudo generar el reporte."));
            }
            return ResultadoRed.exito(respuesta.body());
        } catch (IOException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_CONEXION);
        } catch (SecurityException ex) {
            return ResultadoRed.fallo(ClasificadorDeError.SIN_CODIGO, SIN_PERMISO_RED);
        }
    }

    private String mensajeDeError(Response<?> respuesta, String porDefecto) {
        if (respuesta.errorBody() == null) {
            return porDefecto;
        }
        try {
            String crudo = respuesta.errorBody().string();
            MensajePostgrest postgrest = gson.fromJson(crudo, MensajePostgrest.class);
            if (postgrest != null && postgrest.message != null && !postgrest.message.isEmpty()) {
                return postgrest.message;
            }
        } catch (IOException | RuntimeException ignorada) {
            // Cuerpo ilegible o que no es JSON: se cae al mensaje genérico.
        }
        return porDefecto;
    }

    private static final class MensajePostgrest {
        String message;
    }
}
