package com.example.proyectofinalrestaurante.ui.buzon;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.example.proyectofinalrestaurante.domain.model.Notificacion;
import com.example.proyectofinalrestaurante.domain.repository.NotificacionRepository;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * ViewModel del buzón (Plan Fase 3, E9). Compartido entre MainActivity (para el badge de la
 * Toolbar) y {@link BuzonHoja} (para la lista): una sola fuente, dos consumidores.
 *
 * <p>{@link #abrir()} se llama al abrir la hoja: marca todo como leído (el badge cae solo,
 * porque observa la misma fuente) y purga las leídas de más de 48 h — la retención del buzón
 * de §4.6/§5.2.</p>
 */
public class BuzonViewModel extends ViewModel {

    static final int VENTANA = 50;
    private static final long RETENCION_HORAS = 48;
    private static final long MILLIS_POR_HORA = 60L * 60 * 1000;

    private final NotificacionRepository repositorio;
    private final ExecutorService executor;

    private final MediatorLiveData<EstadoBuzon> estado = new MediatorLiveData<>();

    private List<Notificacion> notificaciones = Collections.emptyList();
    private int noLeidas = 0;

    public BuzonViewModel(@NonNull NotificacionRepository repositorio,
                          @NonNull ExecutorService executor) {
        this.repositorio = repositorio;
        this.executor = executor;
        estado.addSource(repositorio.observarBuzon(VENTANA), this::cuandoLlegan);
        estado.addSource(repositorio.contarNoLeidas(), this::cuandoCambiaElConteo);
        estado.setValue(new EstadoBuzon(Collections.emptyList(), 0));
    }

    public LiveData<EstadoBuzon> getEstado() {
        return estado;
    }

    /** Al abrir la hoja: marca todo leído y purga las leídas de más de 48 h. */
    public void abrir() {
        executor.execute(() -> {
            repositorio.marcarTodasLeidas();
            repositorio.purgarViejas(System.currentTimeMillis() - RETENCION_HORAS * MILLIS_POR_HORA);
        });
    }

    public void marcarLeida(Notificacion notificacion) {
        executor.execute(() -> repositorio.marcarLeida(notificacion.getIdLocal()));
    }

    private void cuandoLlegan(List<Notificacion> nuevas) {
        notificaciones = nuevas == null ? Collections.emptyList() : nuevas;
        estado.setValue(construir());
    }

    private void cuandoCambiaElConteo(Integer conteo) {
        noLeidas = conteo == null ? 0 : conteo;
        estado.setValue(construir());
    }

    private EstadoBuzon construir() {
        return new EstadoBuzon(notificaciones, noLeidas);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}