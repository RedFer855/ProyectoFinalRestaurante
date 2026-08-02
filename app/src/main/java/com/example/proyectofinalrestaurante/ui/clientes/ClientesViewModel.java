package com.example.proyectofinalrestaurante.ui.clientes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.Cliente;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.NuevoCliente;
import com.example.proyectofinalrestaurante.domain.repository.ClienteRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * ViewModel del módulo Clientes (Plan Fase 2d, E5). Mismo patrón que {@code MesasViewModel}:
 * {@link ExecutorService} inyectado (P-005), fuente de datos Room vía {@link LiveData},
 * escrituras optimistas. La búsqueda (por nombre, apellido o identidad) y el filtro por
 * estado viven acá, no en el Fragment.
 */
public class ClientesViewModel extends ViewModel {

    private static final String CLIENTE_CREADO = "Cliente registrado.";
    private static final String CLIENTE_ACTUALIZADO = "Cliente actualizado.";
    private static final String CLIENTE_ACTIVADO = "Cliente reactivado.";
    private static final String CLIENTE_DESACTIVADO = "Cliente dado de baja.";
    private static final String CLIENTE_BORRADO = "Cliente borrado.";

    private final ClienteRepository repositorio;
    private final ExecutorService executor;
    private final MediatorLiveData<EstadoClientes> estado = new MediatorLiveData<>();

    private List<Cliente> clientesActuales = Collections.emptyList();
    @Nullable private Boolean filtroActivo = null;
    private String textoBusqueda = "";
    private boolean sincronizando = false;
    @Nullable private String ultimoErrorSync = null;
    @Nullable private String errorDeOperacion = null;
    @Nullable private String mensajeExito = null;

    public ClientesViewModel(@NonNull ClienteRepository repositorio,
                             @NonNull ExecutorService executor) {
        this.repositorio = repositorio;
        this.executor = executor;
        estado.addSource(repositorio.observarClientes(), this::cuandoLleganClientes);
        estado.addSource(repositorio.getEstadoSincronizacion(), this::cuandoCambiaSincronizacion);
        estado.setValue(EstadoClientes.cargando());
    }

    public LiveData<EstadoClientes> getEstado() {
        return estado;
    }

    public List<Cliente> getTodosLosClientes() {
        return new ArrayList<>(clientesActuales);
    }

    public void sincronizar() {
        repositorio.sincronizar();
    }

    // ------------------------------------------------------------------ fuentes de datos

    private void cuandoLleganClientes(List<Cliente> clientes) {
        clientesActuales = clientes;
        recalcular();
    }

    private void cuandoCambiaSincronizacion(EstadoSincronizacion estadoSync) {
        sincronizando = estadoSync.isSincronizando();
        ultimoErrorSync = estadoSync.getUltimoError();
        recalcular();
    }

    // ------------------------------------------------------------------ filtros

    public void filtrarPorActivo(@Nullable Boolean activo) {
        filtroActivo = activo;
        recalcular();
    }

    public void buscar(String texto) {
        textoBusqueda = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
        recalcular();
    }

    // ------------------------------------------------------------------ operaciones

    public void crear(NuevoCliente nuevo) {
        executor.execute(() -> {
            Result<Long> resultado = repositorio.crearCliente(nuevo);
            if (resultado.isSuccess()) {
                publicarExito(CLIENTE_CREADO);
            } else {
                publicarError(resultado.getError());
            }
        });
    }

    public void actualizar(int idLocal, NuevoCliente datos) {
        executor.execute(() -> ejecutar(
                repositorio.actualizarCliente(idLocal, datos), CLIENTE_ACTUALIZADO));
    }

    public void cambiarBaja(Cliente cliente, boolean activo) {
        executor.execute(() -> {
            Result<Void> resultado = repositorio.cambiarBajaCliente(cliente.getIdLocal(), activo);
            if (resultado.isSuccess()) {
                descartarFiltroQueEsconde(activo);
                publicarExito(activo ? CLIENTE_ACTIVADO : CLIENTE_DESACTIVADO);
            } else {
                publicarError(resultado.getError());
            }
        });
    }

    public void borrar(Cliente cliente) {
        executor.execute(() -> ejecutar(
                repositorio.borrarCliente(cliente.getIdLocal()), CLIENTE_BORRADO));
    }

    /**
     * Suelta el filtro por estado cuando dejaría fuera de la lista al cliente que se acaba de
     * dar de baja/reactivar (Plan Fase 2d, §5.5) — mismo criterio que
     * {@code MesasViewModel.descartarFiltroQueEsconde}.
     */
    private void descartarFiltroQueEsconde(boolean activoDelCliente) {
        if (filtroActivo != null && filtroActivo != activoDelCliente) {
            filtroActivo = null;
        }
    }

    public void onMensajeConsumido() {
        mensajeExito = null;
        EstadoClientes actual = estado.getValue();
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

    private EstadoClientes construirEstado() {
        EstadoClientes nuevo = EstadoClientes.conDatos(filtrados(), filtroActivo, textoBusqueda,
                sincronizando, contarCambiosSinSubir(), ultimoErrorSync, clientesActuales.size());
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
        for (Cliente cliente : clientesActuales) {
            if (cliente.getEstadoSync() != EstadoSync.SINCRONIZADO) {
                cuenta++;
            }
        }
        return cuenta;
    }

    private List<Cliente> filtrados() {
        List<Cliente> resultado = new ArrayList<>();
        for (Cliente cliente : clientesActuales) {
            if (coincideEstado(cliente) && coincideBusqueda(cliente)) {
                resultado.add(cliente);
            }
        }
        return resultado;
    }

    private boolean coincideEstado(Cliente cliente) {
        return filtroActivo == null || cliente.isActivo() == filtroActivo;
    }

    private boolean coincideBusqueda(Cliente cliente) {
        if (textoBusqueda.isEmpty()) {
            return true;
        }
        return contiene(cliente.nombreCompleto()) || contiene(cliente.getIdentidad());
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
