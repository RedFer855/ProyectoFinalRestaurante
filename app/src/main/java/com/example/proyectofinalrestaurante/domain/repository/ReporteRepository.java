package com.example.proyectofinalrestaurante.domain.repository;

import androidx.lifecycle.LiveData;

import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.RangoReporte;
import com.example.proyectofinalrestaurante.domain.model.ReporteVentas;

/**
 * Contrato del módulo Reportes (Domain Layer, Plan Fase 3c, §7.1). {@code data} lo implementa;
 * es la única cara que {@code ui} ve de los reportes.
 *
 * <p>A diferencia de {@code MesaRepository}/{@code PedidoRepository}, este contrato usa solo
 * <b>tres</b> de los cuatro miembros del contrato del proyecto (§2 del plan): lecturas
 * {@link LiveData} que no fallan y disparo de refresco, pero <b>ningún</b> método devuelve
 * {@link com.example.proyectofinalrestaurante.domain.Result} — un agregado derivado no tiene
 * escritura de usuario que pueda fallar de forma que el llamador necesite reaccionar
 * sincrónicamente. El fallo de un refresco se comunica por {@link #getEstadoSincronizacion()}.</p>
 */
public interface ReporteRepository {

    /**
     * La última instantánea de {@code rango}, o {@code null} si ese rango nunca se descargó.
     * Las tres instantáneas (HOY/SEMANA/MES) persisten por separado (§6 del plan).
     */
    LiveData<ReporteVentas> observarReporte(RangoReporte rango);

    /** Refrescando en este momento y, si el último refresco falló, su error. */
    LiveData<EstadoSincronizacion> getEstadoSincronizacion();

    /**
     * Refresca {@code rango} contra el servidor <b>solo si</b> la instantánea está vieja
     * ({@link com.example.proyectofinalrestaurante.domain.ReglasReporte#esVieja}) o no existe
     * todavía. Úsalo en el {@code onStart} del Fragment y al cambiar de chip (§2.1, disparadores
     * 1 y 2). Si el refresco falla, la instantánea previa <b>no se borra</b> — se conserva y el
     * error viaja por {@link #getEstadoSincronizacion()}.
     */
    void refrescar(RangoReporte rango);

    /**
     * Refresca {@code rango} contra el servidor <b>sin importar su edad</b> — el
     * pull-to-refresh explícito (§2.1, disparador 3). Mismo comportamiento que
     * {@link #refrescar} ante un fallo: nunca borra lo que ya había.
     */
    void forzarRefresco(RangoReporte rango);
}
