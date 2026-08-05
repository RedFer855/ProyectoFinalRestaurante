package com.example.proyectofinalrestaurante.ui.empleados;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.Empleado;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.NuevoEmpleado;
import com.example.proyectofinalrestaurante.domain.repository.EmpleadoRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * ViewModel del módulo Empleados (Plan Fase 1d, E4; reescrito para offline-first).
 *
 * <p>El {@link ExecutorService} se inyecta por constructor — no se crea acá dentro, para no
 * replicar la deuda <b>P-005</b> del login.</p>
 *
 * <p>La fuente de datos es Room: el repositorio expone dos {@link LiveData} que este
 * ViewModel fusiona en un único {@link EstadoEmpleados}. Ya no existe {@code cargar()}: la
 * lista llega sola y se mantiene al día por el {@code SyncWorker}. Las escrituras son
 * optimistas y corren en el executor.</p>
 *
 * <p>El filtro de búsqueda vive acá y no en el Fragment: así el texto buscado sobrevive a
 * una rotación de pantalla.</p>
 */
public class EmpleadosViewModel extends ViewModel {

    private static final String EMPLEADO_CREADO = "Empleado creado. Ya puede iniciar sesión.";
    private static final String DATOS_ACTUALIZADOS = "Datos actualizados.";
    private static final String EMPLEADO_ACTIVADO = "Empleado activado.";
    private static final String EMPLEADO_DESACTIVADO = "Empleado desactivado.";

    private final EmpleadoRepository repositorio;
    private final ExecutorService executor;
    private final MediatorLiveData<EstadoEmpleados> estado = new MediatorLiveData<>();

    /** Última lista emitida por Room, por referencia: nunca se muta acá. */
    private List<Empleado> empleadosActuales = Collections.emptyList();
    private String textoBusqueda = "";
    private boolean sincronizando = false;
    @Nullable private String ultimoErrorSync = null;
    /** Error puntual de una operación (el alta sin conexión); se limpia al recalcular. */
    @Nullable private String errorDeOperacion = null;
    @Nullable private String mensajeExito = null;

    public EmpleadosViewModel(@NonNull EmpleadoRepository repositorio,
                              @NonNull ExecutorService executor) {
        this.repositorio = repositorio;
        this.executor = executor;
        estado.addSource(repositorio.observarEmpleados(), this::cuandoLleganEmpleados);
        estado.addSource(repositorio.getEstadoSincronizacion(), this::cuandoCambiaSincronizacion);
        estado.setValue(EstadoEmpleados.cargando());
        // Sync-on-launch: no depender del pull-to-refresh para la primera bajada de datos.
        // Ver el mismo comentario en MesasViewModel.
        sincronizar();
    }

    public LiveData<EstadoEmpleados> getEstado() {
        return estado;
    }

    /** Lista completa, sin filtrar: la usan los diálogos y las reglas de negocio. */
    public List<Empleado> getTodosLosEmpleados() {
        return new ArrayList<>(empleadosActuales);
    }

    /** Pide al repositorio que drene el outbox y baje el delta. */
    public void sincronizar() {
        repositorio.sincronizar();
    }

    // ------------------------------------------------------------------ fuentes de datos

    private void cuandoLleganEmpleados(List<Empleado> empleados) {
        empleadosActuales = empleados;
        recalcular();
    }

    private void cuandoCambiaSincronizacion(EstadoSincronizacion estadoSync) {
        sincronizando = estadoSync.isSincronizando();
        ultimoErrorSync = estadoSync.getUltimoError();
        recalcular();
    }

    // ------------------------------------------------------------------ filtro

    public void filtrar(String texto) {
        textoBusqueda = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
        recalcular();
    }

    // ------------------------------------------------------------------ operaciones

    /**
     * Alta. <b>Es la única operación que necesita conexión</b> (crea la cuenta de acceso), así
     * que es también la única que puede publicar un error de red en el estado.
     */
    public void crear(NuevoEmpleado nuevo) {
        estado.setValue(EstadoEmpleados.cargando());
        executor.execute(() -> {
            Result<Integer> resultado = repositorio.crear(nuevo);
            if (resultado.isSuccess()) {
                publicarExito(EMPLEADO_CREADO);
            } else {
                publicarError(resultado.getError());
            }
        });
    }

    public void actualizarDatos(Empleado empleado) {
        executor.execute(() -> ejecutar(
                repositorio.actualizarDatos(empleado), DATOS_ACTUALIZADOS));
    }

    public void cambiarRol(Empleado empleado, String nuevoRol) {
        executor.execute(() -> ejecutar(
                repositorio.cambiarRol(empleado.getIdEmpleado(), nuevoRol),
                "Rol cambiado a " + nuevoRol + "."));
    }

    public void cambiarEstado(Empleado empleado, boolean activo) {
        executor.execute(() -> ejecutar(
                repositorio.cambiarEstado(empleado.getIdEmpleado(), activo),
                activo ? EMPLEADO_ACTIVADO : EMPLEADO_DESACTIVADO));
    }

    /** Marca el aviso como mostrado para que no se repita al rotar la pantalla. */
    public void onMensajeConsumido() {
        mensajeExito = null;
        EstadoEmpleados actual = estado.getValue();
        if (actual != null && actual.getMensajeExito() != null) {
            estado.setValue(actual.sinMensaje());
        }
    }

    /** Marca el error puntual como mostrado. */
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

    private void publicarExito(String mensaje) {
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

    private EstadoEmpleados construirEstado() {
        EstadoEmpleados nuevo = EstadoEmpleados.conDatos(filtrados(), textoBusqueda,
                sincronizando, contarCambiosSinSubir(), ultimoErrorSync,
                empleadosActuales.size());
        if (errorDeOperacion != null) {
            nuevo = nuevo.conError(errorDeOperacion);
        }
        if (mensajeExito != null) {
            nuevo = nuevo.conMensaje(mensajeExito);
        }
        return nuevo;
    }

    /** Se deriva de las filas, no se guarda: una bandera aparte podría contradecirlas. */
    private int contarCambiosSinSubir() {
        int cuenta = 0;
        for (Empleado empleado : empleadosActuales) {
            if (empleado.getEstadoSync() != EstadoSync.SINCRONIZADO) {
                cuenta++;
            }
        }
        return cuenta;
    }

    private List<Empleado> filtrados() {
        if (textoBusqueda.isEmpty()) {
            return new ArrayList<>(empleadosActuales);
        }
        List<Empleado> resultado = new ArrayList<>();
        for (Empleado e : empleadosActuales) {
            if (coincide(e)) {
                resultado.add(e);
            }
        }
        return resultado;
    }

    private boolean coincide(Empleado e) {
        return contiene(e.nombreCompleto()) || contiene(e.getIdentidad())
                || contiene(e.getCorreo()) || contiene(e.getRol());
    }

    private boolean contiene(@Nullable String valor) {
        return valor != null && valor.toLowerCase(Locale.ROOT).contains(textoBusqueda);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
