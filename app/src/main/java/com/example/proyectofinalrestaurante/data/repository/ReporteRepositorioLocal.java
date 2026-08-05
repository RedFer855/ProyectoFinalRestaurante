package com.example.proyectofinalrestaurante.data.repository;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.proyectofinalrestaurante.data.local.AppDatabase;
import com.example.proyectofinalrestaurante.data.local.dao.ReporteDao;
import com.example.proyectofinalrestaurante.data.local.entity.ConteoPlatilloEntity;
import com.example.proyectofinalrestaurante.data.local.entity.DesempenoMeseroEntity;
import com.example.proyectofinalrestaurante.data.local.entity.ReporteVentasEntity;
import com.example.proyectofinalrestaurante.data.local.mapper.ReporteMapper;
import com.example.proyectofinalrestaurante.data.remote.dto.ReporteVentasDto;
import com.example.proyectofinalrestaurante.domain.ReglasReporte;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.RangoReporte;
import com.example.proyectofinalrestaurante.domain.model.ReporteVentas;
import com.example.proyectofinalrestaurante.domain.repository.ReporteRepository;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Implementación de {@link ReporteRepository} (Plan Fase 3c, §2 y §7.2).
 *
 * <p><b>No implementa {@code Sincronizador} ni usa {@link com.example.proyectofinalrestaurante.data.outbox.Outbox}</b>:
 * un agregado derivado no tiene cola ni marca de agua propia, y no entra a la lista del
 * {@code SyncWorker} (ADR-013) — ver el razonamiento completo en el plan, §2.1: si esto se
 * disparara en el {@code SyncWorker}, la agregación más cara del sistema correría en los 25
 * dispositivos con cada señal del WebSocket de Pedidos.</p>
 *
 * <p>{@code refrescar} y {@code forzarRefresco} son <b>síncronas y bloqueantes</b> — igual que
 * las escrituras de {@code MesaRepositorioLocal} — porque quien las llama
 * ({@code ReportesViewModel}) ya las corre dentro de su propio {@code Executor}. La única
 * diferencia con esas escrituras es que acá el "fallo" no es un {@code Result} sincrónico sino
 * {@link #getEstadoSincronizacion()}: nunca hay un usuario esperando una confirmación puntual,
 * solo una pantalla que puede mostrar "última actualización + error".</p>
 */
public final class ReporteRepositorioLocal implements ReporteRepository {

    private final ReporteDao reporteDao;
    private final ReporteRemoto remoto;
    private final Supplier<Long> reloj;

    private final MutableLiveData<EstadoSincronizacion> estadoSincronizacion =
            new MutableLiveData<>(new EstadoSincronizacion(false, null));

    public ReporteRepositorioLocal(AppDatabase base, ReporteRemoto remoto, Supplier<Long> reloj) {
        this.reporteDao = base.reporteDao();
        this.remoto = remoto;
        this.reloj = reloj;
    }

    @Override
    public LiveData<ReporteVentas> observarReporte(RangoReporte rango) {
        String clave = rango.name();
        MediatorLiveData<ReporteVentas> combinado = new MediatorLiveData<>();
        EstadoCombinado estado = new EstadoCombinado();

        combinado.addSource(reporteDao.observarCabecera(clave), cabecera -> {
            estado.cabecera = cabecera;
            combinado.setValue(combinar(estado));
        });
        combinado.addSource(reporteDao.observarTopPlatillos(clave), lista -> {
            estado.topPlatillos = lista;
            combinado.setValue(combinar(estado));
        });
        combinado.addSource(reporteDao.observarDesempeno(clave), lista -> {
            estado.desempenoMeseros = lista;
            combinado.setValue(combinar(estado));
        });
        return combinado;
    }

    @Nullable
    private static ReporteVentas combinar(EstadoCombinado estado) {
        return ReporteMapper.aDominio(estado.cabecera, estado.topPlatillos, estado.desempenoMeseros);
    }

    private static final class EstadoCombinado {
        @Nullable ReporteVentasEntity cabecera;
        List<ConteoPlatilloEntity> topPlatillos = Collections.emptyList();
        List<DesempenoMeseroEntity> desempenoMeseros = Collections.emptyList();
    }

    @Override
    public LiveData<EstadoSincronizacion> getEstadoSincronizacion() {
        return estadoSincronizacion;
    }

    @Override
    public void refrescar(RangoReporte rango) {
        refrescarInterno(rango, false);
    }

    @Override
    public void forzarRefresco(RangoReporte rango) {
        refrescarInterno(rango, true);
    }

    private void refrescarInterno(RangoReporte rango, boolean forzar) {
        String clave = rango.name();
        if (!forzar) {
            ReporteVentasEntity cabecera = reporteDao.cabeceraSincrona(clave);
            if (cabecera != null && !ReglasReporte.esVieja(cabecera.getGeneradoEn(), reloj.get())) {
                // B1: instantánea fresca, no se llama al remoto.
                return;
            }
        }
        estadoSincronizacion.postValue(new EstadoSincronizacion(true, null));
        ResultadoRed<ReporteVentasDto> resultado = remoto.reporteVentas(clave);
        if (!resultado.isExitoso()) {
            // B3: el fallo no borra la instantánea previa, solo publica el error.
            estadoSincronizacion.postValue(new EstadoSincronizacion(false, resultado.getMensaje()));
            return;
        }
        ReporteVentasDto dto = resultado.getValor();
        reporteDao.reemplazarRango(clave,
                ReporteMapper.cabeceraDesdeDto(clave, dto),
                ReporteMapper.topPlatillosDesdeDto(clave, dto),
                ReporteMapper.desempenoDesdeDto(clave, dto));
        estadoSincronizacion.postValue(new EstadoSincronizacion(false, null));
    }
}
