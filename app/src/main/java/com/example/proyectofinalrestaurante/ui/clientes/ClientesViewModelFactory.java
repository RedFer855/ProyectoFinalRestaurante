package com.example.proyectofinalrestaurante.ui.clientes;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.proyectofinalrestaurante.core.SyncApplication;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.repository.ClienteRepositorioLocal;

import java.util.concurrent.Executors;

/**
 * Composition root del módulo Clientes (DI manual — ver P-002).
 *
 * <p>Mismo patrón que {@link com.example.proyectofinalrestaurante.ui.mesas.MesasViewModelFactory}:
 * construye el repositorio local-first con la base de {@link SyncApplication}, el
 * {@link Outbox} y registra el observador de sincronización.</p>
 */
public class ClientesViewModelFactory implements ViewModelProvider.Factory {

    private final Application aplicacion;

    public ClientesViewModelFactory(@NonNull Application aplicacion) {
        this.aplicacion = aplicacion;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        SyncApplication app = (SyncApplication) aplicacion;
        Outbox outbox = new Outbox(app.baseDeDatos().operacionPendienteDao(),
                TipoOperacion.Modulo.CLIENTES);
        ClienteRepositorioLocal repositorio = new ClienteRepositorioLocal(
                app.baseDeDatos().clienteDao(), outbox, app);
        SyncApplication.registrarObservador(TipoOperacion.Modulo.CLIENTES, repositorio);
        return (T) new ClientesViewModel(repositorio, Executors.newSingleThreadExecutor());
    }
}
