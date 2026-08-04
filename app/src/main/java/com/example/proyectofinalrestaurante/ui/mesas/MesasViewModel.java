package com.example.proyectofinalrestaurante.ui.mesas;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.example.proyectofinalrestaurante.domain.model.EstadoMesa;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.Mesa;
import com.example.proyectofinalrestaurante.domain.model.NuevaMesa;
import com.example.proyectofinalrestaurante.domain.repository.MesaRepository;
import com.example.proyectofinalrestaurante.domain.repository.Result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * ViewModel del módulo Mesas (Fase 2c).
 *
 * <p>Mismo patrón que {@link com.example.proyectofinalrestaurante.ui.menu.MenuViewModel}:
 * fusiona {@link LiveData} del repositorio en un único {@link EstadoMesas} inmutable.
 * El filtro por estado y la búsqueda viven acá — no en el Fragment — para sobrevivir
 * rotaciones.</p>
 */
public class MesasViewModel extends ViewModel {

    private static final String MESA_CREADA = "Mesa agregada.";
    private static final String MESA_ACTUALIZADA = "Mesa actualizada.";
    private static final String ESTADO_CAMBIADO = "Estado de la mesa actualizado.";
    private static final String MESA_DESACTIVADA = "Mesa dada de baja.";
    private static final String MESA_REACTIVADA = "Mesa reactivada.";

    private final MesaRepository repositorio;
    private final ExecutorService executor;
    private final MediatorLiveData<EstadoMesas> estado = new MediatorLiveData<>();

    private List<Mesa> mesasActuales = Collections.emptyList();
    @Nullable private EstadoMesa filtroEstado = EstadoMesas.SIN_FILTRO;
    private String textoBusqueda = "";
    private boolean sincronizando = false;
    @Nullable private String ultimoErrorSync = null;

    public MesasViewModel(@NonNull MesaRepository repositorio,
                          @NonNull ExecutorService executor) {
        this.repositorio = repositorio;
        this.executor = executor;
        estado.addSource(repositorio.observarMesas(), this::cuandoLleganMesas);
        estado.addSource(repositorio.getEstadoSincronizacion(), this::cuandoCambiaSincronizacion);
        estado.setValue(EstadoMesas.cargando());
    }

    public LiveData<EstadoMesas> getEstado() {
        return estado;
    }

    public void sincronizar() {
        repositorio.sincronizar();
    }

    // ------------------------------------------------------------------ fuentes

    private void cuandoLleganMesas(List<Mesa> mesas) {
        mesasActuales = mesas;
        recalcular();
    }

    private void cuandoCambiaSincronizacion(EstadoSincronizacion estadoSync) {
        sincronizando = estadoSync.isSincronizando();
        ultimoErrorSync = estadoSync.getUltimoError();
        recalcular();
    }

    // ------------------------------------------------------------------ filtros

    public void filtrarPorEstado(@Nullable EstadoMesa estado) {
        filtroEstado = estado;
        recalcular();
    }

    public void buscar(String texto) {
        textoBusqueda = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
        recalcular();
    }

    // ------------------------------------------------------------------ escrituras

    public void crearMesa(NuevaMesa nueva) {
        executor.execute(() -> {
            Result<Long> resultado = repositorio.crearMesa(nueva);
            if (resultado.isSuccess()) {
                terminarConExito(MESA_CREADA);
            } else {
                estado.postValue(EstadoMesas.error(resultado.getError()));
            }
        });
    }

    public void actualizarMesa(int idLocal, NuevaMesa datos) {
        executor.execute(() -> {
            Result<Void> resultado = repositorio.actualizarMesa(idLocal, datos);
            if (resultado.isSuccess()) {
                terminarConExito(MESA_ACTUALIZADA);
            } else {
                estado.postValue(EstadoMesas.error(resultado.getError()));
            }
        });
    }

    public void cambiarEstadoMesa(int idLocal, EstadoMesa nuevoEstado) {
        executor.execute(() -> {
            Result<Void> resultado = repositorio.cambiarEstadoMesa(idLocal, nuevoEstado);
            if (resultado.isSuccess()) {
                descartarFiltroQueEsconde(nuevoEstado);
                terminarConExito(ESTADO_CAMBIADO);
            } else {
                estado.postValue(EstadoMesas.error(resultado.getError()));
            }
        });
    }

    public void darDeBajaMesa(int idLocal) {
        executor.execute(() -> {
            Result<Void> resultado = repositorio.darDeBajaMesa(idLocal);
            if (resultado.isSuccess()) {
                terminarConExito(MESA_DESACTIVADA);
            } else {
                estado.postValue(EstadoMesas.error(resultado.getError()));
            }
        });
    }

    public void reactivarMesa(int idLocal) {
        executor.execute(() -> {
            Result<Void> resultado = repositorio.reactivarMesa(idLocal);
            if (resultado.isSuccess()) {
                terminarConExito(MESA_REACTIVADA);
            } else {
                estado.postValue(EstadoMesas.error(resultado.getError()));
            }
        });
    }

    public void onMensajeConsumido() {
        EstadoMesas actual = estado.getValue();
        if (actual != null && actual.getMensajeExito() != null) {
            estado.setValue(actual.sinMensaje());
        }
    }

    // ------------------------------------------------------------------ interno

    /**
     * Suelta el filtro por estado cuando cambiarlo dejaría fuera de la lista a la mesa
     * que se acaba de modificar. Mismo criterio que
     * {@link com.example.proyectofinalrestaurante.ui.menu.MenuViewModel#descartarFiltroQueEsconde}.
     */
    private void descartarFiltroQueEsconde(EstadoMesa nuevoEstado) {
        if (filtroEstado != null && filtroEstado != nuevoEstado) {
            filtroEstado = EstadoMesas.SIN_FILTRO;
        }
    }

    private void terminarConExito(String mensajeExito) {
        estado.postValue(estadoConDatos().conMensaje(mensajeExito));
    }

    private void recalcular() {
        estado.setValue(estadoConDatos());
    }

    private EstadoMesas estadoConDatos() {
        return EstadoMesas.conDatos(
                filtrados(), filtroEstado, textoBusqueda,
                sincronizando, cambiosSinSubir(), ultimoErrorSync);
    }

    private List<Mesa> filtrados() {
        List<Mesa> resultado = new ArrayList<>();
        for (Mesa mesa : mesasActuales) {
            if (coincideEstado(mesa) && coincideBusqueda(mesa)) {
                resultado.add(mesa);
            }
        }
        return resultado;
    }

    private boolean coincideEstado(Mesa mesa) {
        return filtroEstado == null || mesa.getEstadoMesa() == filtroEstado;
    }

    private boolean coincideBusqueda(Mesa mesa) {
        if (textoBusqueda.isEmpty()) {
            return true;
        }
        String ubicacion = mesa.getUbicacion() == null ? "" : mesa.getUbicacion();
        return String.valueOf(mesa.getNumeroMesa()).contains(textoBusqueda)
                || ubicacion.toLowerCase(Locale.ROOT).contains(textoBusqueda);
    }

    private int cambiosSinSubir() {
        int cambios = 0;
        for (Mesa mesa : mesasActuales) {
            if (mesa.getEstadoSync() != EstadoSync.SINCRONIZADO) {
                cambios++;
            }
        }
        return cambios;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
