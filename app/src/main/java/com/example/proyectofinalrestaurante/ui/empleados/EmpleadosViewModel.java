package com.example.proyectofinalrestaurante.ui.empleados;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.Empleado;
import com.example.proyectofinalrestaurante.domain.model.NuevoEmpleado;
import com.example.proyectofinalrestaurante.domain.repository.EmpleadoRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * ViewModel del módulo Empleados (Plan Fase 1d, E4).
 *
 * <p>El {@link ExecutorService} se inyecta por constructor — no se crea acá dentro,
 * para no replicar la deuda <b>P-005</b> del login.</p>
 *
 * <p>El filtro de búsqueda vive en el ViewModel y no en el Fragment: la lista completa
 * es del ViewModel, así el texto buscado sobrevive a una rotación de pantalla.</p>
 */
public class EmpleadosViewModel extends ViewModel {

    private final EmpleadoRepository repositorio;
    private final ExecutorService executor;
    private final MutableLiveData<EstadoEmpleados> estado =
            new MutableLiveData<>(EstadoEmpleados.inicial());

    /** Lista completa recibida del servidor; lo publicado es esto ya filtrado. */
    private final List<Empleado> todos = new ArrayList<>();
    private String textoBusqueda = "";

    public EmpleadosViewModel(@NonNull EmpleadoRepository repositorio,
                              @NonNull ExecutorService executor) {
        this.repositorio = repositorio;
        this.executor = executor;
    }

    public LiveData<EstadoEmpleados> getEstado() {
        return estado;
    }

    public void cargar() {
        estado.setValue(EstadoEmpleados.cargando());
        executor.execute(() -> {
            Result<List<Empleado>> resultado = repositorio.listar();
            if (resultado.isSuccess()) {
                todos.clear();
                todos.addAll(resultado.getValue());
                estado.postValue(EstadoEmpleados.conDatos(filtrados()));
            } else {
                estado.postValue(EstadoEmpleados.error(resultado.getError()));
            }
        });
    }

    public void filtrar(String texto) {
        textoBusqueda = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
        estado.setValue(EstadoEmpleados.conDatos(filtrados()));
    }

    public void crear(NuevoEmpleado nuevo) {
        estado.setValue(EstadoEmpleados.cargando());
        executor.execute(() -> {
            Result<Empleado> resultado = repositorio.crear(nuevo);
            if (resultado.isSuccess()) {
                recargarCon("Empleado creado. Ya puede iniciar sesión.");
            } else {
                estado.postValue(EstadoEmpleados.error(resultado.getError()));
            }
        });
    }

    public void actualizarDatos(Empleado empleado) {
        estado.setValue(EstadoEmpleados.cargando());
        executor.execute(() -> ejecutar(
                repositorio.actualizarDatos(empleado), "Datos actualizados."));
    }

    public void cambiarRol(Empleado empleado, String nuevoRol) {
        estado.setValue(EstadoEmpleados.cargando());
        executor.execute(() -> ejecutar(
                repositorio.cambiarRol(empleado.getIdAuthUser(), nuevoRol),
                "Rol cambiado a " + nuevoRol + "."));
    }

    public void cambiarEstado(Empleado empleado, boolean activo) {
        estado.setValue(EstadoEmpleados.cargando());
        executor.execute(() -> ejecutar(
                repositorio.cambiarEstado(empleado.getIdAuthUser(), activo),
                activo ? "Empleado activado." : "Empleado desactivado."));
    }

    /** Marca el aviso como mostrado para que no se repita al rotar la pantalla. */
    public void onMensajeConsumido() {
        EstadoEmpleados actual = estado.getValue();
        if (actual != null && actual.getMensajeExito() != null) {
            estado.setValue(actual.sinMensaje());
        }
    }

    private void ejecutar(Result<Void> resultado, String mensajeExito) {
        if (resultado.isSuccess()) {
            recargarCon(mensajeExito);
        } else {
            estado.postValue(EstadoEmpleados.error(resultado.getError()));
        }
    }

    /**
     * Tras una operación exitosa se vuelve a leer del servidor en vez de retocar la
     * lista en memoria: así lo que se ve es lo que la base realmente aceptó — incluidos
     * los cambios que aplican triggers del lado del servidor.
     */
    private void recargarCon(String mensajeExito) {
        Result<List<Empleado>> relectura = repositorio.listar();
        if (relectura.isSuccess()) {
            todos.clear();
            todos.addAll(relectura.getValue());
            estado.postValue(EstadoEmpleados.conDatos(filtrados()).conMensaje(mensajeExito));
        } else {
            estado.postValue(EstadoEmpleados.error(relectura.getError()));
        }
    }

    private List<Empleado> filtrados() {
        if (textoBusqueda.isEmpty()) {
            return new ArrayList<>(todos);
        }
        List<Empleado> resultado = new ArrayList<>();
        for (Empleado e : todos) {
            if (coincide(e)) {
                resultado.add(e);
            }
        }
        return resultado;
    }

    private boolean coincide(Empleado e) {
        return e.nombreCompleto().toLowerCase(Locale.ROOT).contains(textoBusqueda)
                || e.getIdentidad().toLowerCase(Locale.ROOT).contains(textoBusqueda)
                || e.getCorreo().toLowerCase(Locale.ROOT).contains(textoBusqueda)
                || e.getRol().toLowerCase(Locale.ROOT).contains(textoBusqueda);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
