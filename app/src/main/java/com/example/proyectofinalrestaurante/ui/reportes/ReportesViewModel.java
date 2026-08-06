package com.example.proyectofinalrestaurante.ui.reportes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.RangoReporte;
import com.example.proyectofinalrestaurante.domain.model.ReporteVentas;
import com.example.proyectofinalrestaurante.domain.repository.ReporteRepository;

import java.util.concurrent.ExecutorService;

/**
 * ViewModel de la pantalla de Reportes (Plan Fase 3c, E8). Mismo patrón que
 * {@code MesasViewModel}: el {@link ExecutorService} se inyecta por constructor (P-005) y las
 * lecturas vienen de {@link LiveData}.
 *
 * <p>El rango vive acá, no en el Fragment: sobrevive a la rotación (B6) y el
 * {@code ChipGroup} se re-marca <b>desde el estado</b> al recrear la vista, nunca al revés.
 * {@link #cambiarRango} cubre los disparadores 1 y 2 de refresco (§2.1 del plan: onStart
 * implícito en el constructor, y cambio de chip) llamando siempre a
 * {@link ReporteRepository#refrescar}, que internamente respeta el umbral de 15 minutos
 * (B1); {@link #pullToRefresh} es el disparador 3, explícito, y por eso llama a
 * {@link ReporteRepository#forzarRefresco}.</p>
 */
public class ReportesViewModel extends ViewModel {

    private final ReporteRepository repositorio;
    private final ExecutorService executor;
    private final MediatorLiveData<EstadoReportes> estado = new MediatorLiveData<>();

    private RangoReporte rangoActual = RangoReporte.HOY;
    @Nullable private LiveData<ReporteVentas> fuenteReporteActual;
    @Nullable private ReporteVentas reporteActual;
    private boolean sincronizando;
    @Nullable private String ultimoErrorSync;

    public ReportesViewModel(@NonNull ReporteRepository repositorio,
                             @NonNull ExecutorService executor) {
        this.repositorio = repositorio;
        this.executor = executor;
        estado.addSource(repositorio.getEstadoSincronizacion(), this::cuandoCambiaSincronizacion);
        estado.setValue(EstadoReportes.cargando(rangoActual));
        observarRango(rangoActual);
        // Disparador 1 (§2.1): equivalente al onStart del Fragment. refrescar() ya respeta el
        // umbral de 15 min, así que rotar la pantalla no vuelve a golpear al servidor (B1).
        executor.execute(() -> repositorio.refrescar(rangoActual));
    }

    public LiveData<EstadoReportes> getEstado() {
        return estado;
    }

    /** Disparador 2 (§2.1): cambio de chip. No hace nada si ya es el rango actual. */
    public void cambiarRango(RangoReporte nuevo) {
        if (nuevo == rangoActual) {
            return;
        }
        rangoActual = nuevo;
        reporteActual = null;
        observarRango(nuevo);
        recalcular();
        executor.execute(() -> repositorio.refrescar(nuevo));
    }

    /** Disparador 3 (§2.1): pull-to-refresh explícito, sin importar la edad de la instantánea. */
    public void pullToRefresh() {
        RangoReporte rango = rangoActual;
        executor.execute(() -> repositorio.forzarRefresco(rango));
    }

    private void observarRango(RangoReporte rango) {
        if (fuenteReporteActual != null) {
            estado.removeSource(fuenteReporteActual);
        }
        fuenteReporteActual = repositorio.observarReporte(rango);
        estado.addSource(fuenteReporteActual, this::cuandoLlegaReporte);
    }

    private void cuandoLlegaReporte(@Nullable ReporteVentas reporte) {
        reporteActual = reporte;
        recalcular();
    }

    private void cuandoCambiaSincronizacion(EstadoSincronizacion estadoSync) {
        sincronizando = estadoSync.isSincronizando();
        ultimoErrorSync = estadoSync.getUltimoError();
        recalcular();
    }

    private void recalcular() {
        estado.setValue(EstadoReportes.conDatos(rangoActual, reporteActual, sincronizando,
                ultimoErrorSync));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
