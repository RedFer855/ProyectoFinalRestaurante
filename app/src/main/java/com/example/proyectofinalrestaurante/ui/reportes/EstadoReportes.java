package com.example.proyectofinalrestaurante.ui.reportes;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.domain.model.RangoReporte;
import com.example.proyectofinalrestaurante.domain.model.ReporteVentas;

/**
 * Estado único e inmutable de la pantalla de Reportes (Plan Fase 3c, E8). Mismo patrón que
 * {@code EstadoMesas}: {@link #isVacio()} es derivado de {@link #reporte}, nunca una bandera
 * suelta que pueda desincronizarse.
 *
 * <p>{@code reporte == null} cubre dos casos honestos y distintos (§6 del plan), que la UI
 * distingue mirando {@link #isSincronizando()}: instantánea que nunca se descargó (vacío) y
 * está por llegar (vacío + sincronizando).</p>
 */
public final class EstadoReportes {

    private final boolean cargando;
    private final RangoReporte rango;
    @Nullable private final ReporteVentas reporte;
    private final boolean sincronizando;
    @Nullable private final String ultimoErrorSync;

    private EstadoReportes(boolean cargando, RangoReporte rango, @Nullable ReporteVentas reporte,
                           boolean sincronizando, @Nullable String ultimoErrorSync) {
        this.cargando = cargando;
        this.rango = rango;
        this.reporte = reporte;
        this.sincronizando = sincronizando;
        this.ultimoErrorSync = ultimoErrorSync;
    }

    public static EstadoReportes cargando(RangoReporte rango) {
        return new EstadoReportes(true, rango, null, false, null);
    }

    public static EstadoReportes conDatos(RangoReporte rango, @Nullable ReporteVentas reporte,
                                          boolean sincronizando, @Nullable String ultimoErrorSync) {
        return new EstadoReportes(false, rango, reporte, sincronizando, ultimoErrorSync);
    }

    public boolean isCargando() {
        return cargando;
    }

    public RangoReporte getRango() {
        return rango;
    }

    @Nullable
    public ReporteVentas getReporte() {
        return reporte;
    }

    public boolean isSincronizando() {
        return sincronizando;
    }

    @Nullable
    public String getUltimoErrorSync() {
        return ultimoErrorSync;
    }

    /** Sin instantánea para este rango: nunca se descargó (Plan Fase 3c, §6). */
    public boolean isVacio() {
        return !cargando && reporte == null;
    }

    /**
     * Hay instantánea y dice que el rango no tuvo ventas. Distinto de {@link #isVacio()}: acá
     * el dato bajó y es real, así que la franja "Datos al …" se sigue mostrando; lo que se
     * oculta es el bloque de cifras, que si no quedaba en L 0.00 con dos listas vacías colgando
     * de sus títulos.
     */
    public boolean isSinVentas() {
        return reporte != null && reporte.sinVentas();
    }
}
