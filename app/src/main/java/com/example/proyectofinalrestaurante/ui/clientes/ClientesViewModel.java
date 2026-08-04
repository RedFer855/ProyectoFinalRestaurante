package com.example.proyectofinalrestaurante.ui.clientes;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.example.proyectofinalrestaurante.domain.model.Cliente;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.NuevoCliente;
import com.example.proyectofinalrestaurante.domain.repository.ClienteRepository;
import com.example.proyectofinalrestaurante.domain.repository.Result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * ViewModel del módulo Clientes (Fase 2d).
 *
 * <p>Mismo patrón que {@link com.example.proyectofinalrestaurante.ui.mesas.MesasViewModel}:
 * fusiona {@link LiveData} del repositorio en un único {@link EstadoClientes} inmutable.
 * El filtro y la búsqueda viven acá — no en el Fragment — para sobrevivir rotaciones.</p>
 */
public class ClientesViewModel extends ViewModel {

    private static final String CLIENTE_CREADO = "Cliente agregado.";
    private static final String CLIENTE_ACTUALIZADO = "Cliente actualizado.";
    private static final String ESTADO_CAMBIADO = "Estado del cliente actualizado.";
    private static final String CLIENTE_BORRADO = "Cliente eliminado.";

    private final ClienteRepository repositorio;
    private final ExecutorService executor;
    private final MediatorLiveData<EstadoClientes> estado = new MediatorLiveData<>();

    private List<Cliente> clientesActuales = Collections.emptyList();
    @Nullable private Boolean filtroActivo = EstadoClientes.SIN_FILTRO;
    private String textoBusqueda = "";
    private boolean sincronizando = false;
    @Nullable private String ultimoErrorSync = null;

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

    public void sincronizar() {
        repositorio.sincronizar();
    }

    // ------------------------------------------------------------------ fuentes

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

    public void filtrarPorEstado(@Nullable Boolean activo) {
        filtroActivo = activo;
        recalcular();
    }

    public void buscar(String texto) {
        textoBusqueda = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
        recalcular();
    }

    // ------------------------------------------------------------------ escrituras

    public void crearCliente(NuevoCliente nuevo) {
        executor.execute(() -> {
            Result<Long> resultado = repositorio.crearCliente(nuevo);
            if (resultado.isSuccess()) {
                terminarConExito(CLIENTE_CREADO);
            } else {
                estado.postValue(EstadoClientes.error(resultado.getError()));
            }
        });
    }

    public void actualizarCliente(int idLocal, NuevoCliente datos) {
        executor.execute(() -> {
            Result<Void> resultado = repositorio.actualizarCliente(idLocal, datos);
            if (resultado.isSuccess()) {
                terminarConExito(CLIENTE_ACTUALIZADO);
            } else {
                estado.postValue(EstadoClientes.error(resultado.getError()));
            }
        });
    }

    public void cambiarEstadoCliente(int idLocal, boolean activo) {
        executor.execute(() -> {
            Result<Void> resultado = repositorio.cambiarEstadoCliente(idLocal, activo);
            if (resultado.isSuccess()) {
                terminarConExito(ESTADO_CAMBIADO);
            } else {
                estado.postValue(EstadoClientes.error(resultado.getError()));
            }
        });
    }

    public void borrarCliente(int idLocal) {
        executor.execute(() -> {
            Result<Void> resultado = repositorio.borrarCliente(idLocal);
            if (resultado.isSuccess()) {
                terminarConExito(CLIENTE_BORRADO);
            } else {
                estado.postValue(EstadoClientes.error(resultado.getError()));
            }
        });
    }

    public void onMensajeConsumido() {
        EstadoClientes actual = estado.getValue();
        if (actual != null && actual.getMensajeExito() != null) {
            estado.setValue(actual.sinMensaje());
        }
    }

    // ------------------------------------------------------------------ interno

    private void terminarConExito(String mensajeExito) {
        estado.postValue(estadoConDatos().conMensaje(mensajeExito));
    }

    private void recalcular() {
        estado.setValue(estadoConDatos());
    }

    private EstadoClientes estadoConDatos() {
        return EstadoClientes.conDatos(
                filtrados(), filtroActivo, textoBusqueda,
                sincronizando, cambiosSinSubir(), ultimoErrorSync);
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
        if (filtroActivo == null) {
            return true;
        }
        return cliente.isActivo() == filtroActivo;
    }

    private boolean coincideBusqueda(Cliente cliente) {
        if (textoBusqueda.isEmpty()) {
            return true;
        }
        String nombre = cliente.getNombre() == null ? "" : cliente.getNombre();
        String apellido = cliente.getApellido() == null ? "" : cliente.getApellido();
        String identidad = cliente.getIdentidad() == null ? "" : cliente.getIdentidad();
        String telefono = cliente.getTelefono() == null ? "" : cliente.getTelefono();
        return nombre.toLowerCase(Locale.ROOT).contains(textoBusqueda)
                || apellido.toLowerCase(Locale.ROOT).contains(textoBusqueda)
                || identidad.contains(textoBusqueda)
                || telefono.contains(textoBusqueda);
    }

    private int cambiosSinSubir() {
        int cambios = 0;
        for (Cliente cliente : clientesActuales) {
            if (cliente.getEstadoSync() != EstadoSync.SINCRONIZADO) {
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
