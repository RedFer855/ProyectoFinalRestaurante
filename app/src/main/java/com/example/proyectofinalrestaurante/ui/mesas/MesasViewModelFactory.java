package com.example.proyectofinalrestaurante.ui.mesas;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.proyectofinalrestaurante.core.SyncApplication;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.repository.MesaRepositorioLocal;

import java.util.concurrent.Executors;

/**
 * Composition root del módulo Mesas (DI manual — ver P-002). Mismo patrón que
 * {@code EmpleadosViewModelFactory}: se construye con la base de {@link SyncApplication}
 * (singleton del proceso) y su partición del {@link Outbox}, y el repositorio se registra
 * como {@code ObservadorSincronizacion} para que el {@code SyncWorker} le avise el estado
 * global de la sincronización.
 */
public class MesasViewModelFactory implements ViewModelProvider.Factory {

    private final Application aplicacion;

    public MesasViewModelFactory(@NonNull Application aplicacion) {
        this.aplicacion = aplicacion;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        SyncApplication app = (SyncApplication) aplicacion;
        Outbox outbox = new Outbox(app.baseDeDatos().operacionPendienteDao(),
                TipoOperacion.Modulo.MESAS);
        MesaRepositorioLocal repositorio =
                new MesaRepositorioLocal(app.baseDeDatos(), outbox, app);
        SyncApplication.registrarObservador(TipoOperacion.Modulo.MESAS, repositorio);
        return (T) new MesasViewModel(repositorio, Executors.newSingleThreadExecutor());
    }
}
