package com.example.proyectofinalrestaurante.ui.mesas;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.EstadoMesa;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.Mesa;
import com.example.proyectofinalrestaurante.domain.model.NuevaMesa;
import com.example.proyectofinalrestaurante.domain.repository.MesaRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * ViewModel del módulo Mesas (Plan Fase 2c, E5). Mismo patrón que {@code EmpleadosViewModel}
 * y {@code MenuViewModel}: el {@link ExecutorService} se inyecta por constructor (P-005), la
 * fuente de datos es Room vía {@link LiveData} y las escrituras son optimistas.
 *
 * <p>El filtro por estado operativo y el texto de búsqueda (por número o ubicación) viven
 * acá, no en el Fragment, para sobrevivir a una rotación de pantalla.</p>
 */
public class MesasViewModel extends ViewModel {

    private static final String MESA_CREADA = "Mesa creada.";
    private static final String MESA_ACTUALIZADA = "Mesa actualizada.";
    private static final String MESA_ACTIVADA = "Mesa reactivada.";
    private static final String MESA_DESACTIVADA = "Mesa dada de baja.";

    private final MesaRepository repositorio;
    private final ExecutorService executor;
    private final MediatorLiveData<EstadoMesas> estado = new MediatorLiveData<>();

    /** Últimas listas emitidas por Room, por referencia: nunca se mutan acá. */
    private List<Mesa> mesasActuales = Collections.emptyList();
    private List<EstadoMesa> estadosDisponibles = Collections.emptyList();
    @Nullable private EstadoMesa filtroEstado = null;
    private String textoBusqueda = "";
    private boolean sincronizando = false;
    @Nullable private String ultimoErrorSync = null;
    @Nullable private String errorDeOperacion = null;
    @Nullable private String mensajeExito = null;

    public MesasViewModel(@NonNull MesaRepository repositorio, @NonNull ExecutorService executor) {
        this.repositorio = repositorio;
        this.executor = executor;
        estado.addSource(repositorio.observarMesas(), this::cuandoLleganMesas);
        estado.addSource(repositorio.observarEstadosMesa(), this::cuandoLleganEstados);
        estado.addSource(repositorio.getEstadoSincronizacion(), this::cuandoCambiaSincronizacion);
        estado.setValue(EstadoMesas.cargando());
        // Sync-on-launch (stale-while-revalidate): la pantalla ya muestra lo que hay en Room
        // arriba, pero nadie debería depender de deslizar hacia abajo para que la primera
        // sincronización se dispare — sobre todo en un dispositivo nuevo, donde la base local
        // está vacía. Reutiliza el mismo indicador que ya alimenta el pull-to-refresh.
        sincronizar();
    }

    public LiveData<EstadoMesas> getEstado() {
        return estado;
    }

    /** Lista completa, sin filtrar: la usa el formulario para chequear números duplicados. */
    public List<Mesa> getTodasLasMesas() {
        return new ArrayList<>(mesasActuales);
    }

    public void sincronizar() {
        repositorio.sincronizar();
    }

    // ------------------------------------------------------------------ fuentes de datos

    private void cuandoLleganMesas(List<Mesa> mesas) {
        mesasActuales = mesas;
        recalcular();
    }

    private void cuandoLleganEstados(List<EstadoMesa> estados) {
        estadosDisponibles = estados;
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

    // ------------------------------------------------------------------ operaciones

    public void crear(NuevaMesa nueva) {
        executor.execute(() -> {
            Result<Long> resultado = repositorio.crearMesa(nueva);
            if (resultado.isSuccess()) {
                publicarExito(MESA_CREADA);
            } else {
                publicarError(resultado.getError());
            }
        });
    }

    public void actualizar(int idLocal, NuevaMesa datos) {
        executor.execute(() -> ejecutar(
                repositorio.actualizarMesa(idLocal, datos), MESA_ACTUALIZADA));
    }

    /**
     * Cambia el estado operativo. Es la acción principal de la pantalla (Plan Fase 2c, §4):
     * un toque en la tarjeta, no una opción escondida en un menú.
     */
    public void cambiarEstado(Mesa mesa, EstadoMesa nuevoEstado) {
        executor.execute(() -> {
            Result<Void> resultado = repositorio.cambiarEstadoMesa(mesa.getIdLocal(), nuevoEstado);
            if (resultado.isSuccess()) {
                descartarFiltroQueEsconde(nuevoEstado);
                publicarExito(null);
            } else {
                publicarError(resultado.getError());
            }
        });
    }

    public void cambiarBaja(Mesa mesa, boolean activo) {
        executor.execute(() -> ejecutar(
                repositorio.cambiarBajaMesa(mesa.getIdLocal(), activo),
                activo ? MESA_ACTIVADA : MESA_DESACTIVADA));
    }

    /**
     * Suelta el filtro por estado cuando dejaría fuera de la lista a la mesa que se acaba de
     * cambiar (Plan Fase 2c, §5.1). Sin esto, con el filtro "Libre" activo el mesero marca
     * una mesa como Ocupada y la mesa desaparece de la pantalla: el servidor guardó, pero el
     * usuario cree que falló y vuelve a tocar. Mismo criterio que
     * {@code MenuViewModel.descartarFiltroQueEsconde}.
     */
    private void descartarFiltroQueEsconde(EstadoMesa estadoDeLaMesa) {
        if (filtroEstado != null && filtroEstado != estadoDeLaMesa) {
            filtroEstado = null;
        }
    }

    public void onMensajeConsumido() {
        mensajeExito = null;
        EstadoMesas actual = estado.getValue();
        if (actual != null && actual.getMensajeExito() != null) {
            estado.setValue(actual.sinMensaje());
        }
    }

    public void onErrorConsumido() {
        errorDeOperacion = null;
        recalcular();
    }

    private void ejecutar(Result<Void> resultado, String mensajeExitoso) {
        if (resultado.isSuccess()) {
            publicarExito(mensajeExitoso);
        } else {
            publicarError(resultado.getError());
        }
    }

    private void publicarExito(@Nullable String mensaje) {
        mensajeExito = mensaje;
        errorDeOperacion = null;
        estado.postValue(construirEstado());
    }

    private void publicarError(String mensaje) {
        errorDeOperacion = mensaje;
        estado.postValue(construirEstado());
    }

    // ------------------------------------------------------------------ estado

    private void recalcular() {
        estado.setValue(construirEstado());
    }

    private EstadoMesas construirEstado() {
        EstadoMesas nuevo = EstadoMesas.conDatos(filtradas(), estadosDisponibles, filtroEstado,
                textoBusqueda, sincronizando, contarCambiosSinSubir(), ultimoErrorSync,
                mesasActuales.size());
        if (errorDeOperacion != null) {
            nuevo = nuevo.conError(errorDeOperacion);
        }
        if (mensajeExito != null) {
            nuevo = nuevo.conMensaje(mensajeExito);
        }
        return nuevo;
    }

    private int contarCambiosSinSubir() {
        int cuenta = 0;
        for (Mesa mesa : mesasActuales) {
            if (mesa.getEstadoSync() != EstadoSync.SINCRONIZADO) {
                cuenta++;
            }
        }
        return cuenta;
    }

    private List<Mesa> filtradas() {
        List<Mesa> resultado = new ArrayList<>();
        for (Mesa mesa : mesasActuales) {
            if (coincideEstado(mesa) && coincideBusqueda(mesa)) {
                resultado.add(mesa);
            }
        }
        return resultado;
    }

    private boolean coincideEstado(Mesa mesa) {
        return filtroEstado == null || mesa.getEstado() == filtroEstado;
    }

    private boolean coincideBusqueda(Mesa mesa) {
        if (textoBusqueda.isEmpty()) {
            return true;
        }
        String numero = String.valueOf(mesa.getNumeroMesa());
        String ubicacion = mesa.getUbicacion() == null ? "" : mesa.getUbicacion().toLowerCase(Locale.ROOT);
        return numero.contains(textoBusqueda) || ubicacion.contains(textoBusqueda);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
