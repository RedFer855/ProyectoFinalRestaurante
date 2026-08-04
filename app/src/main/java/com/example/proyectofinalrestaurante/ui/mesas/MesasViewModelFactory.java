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
 * Composition root del módulo Mesas (DI manual — ver P-002).
 *
 * <p>Mismo patrón que {@link com.example.proyectofinalrestaurante.ui.menu.MenuViewModelFactory}:
 * construye el repositorio local-first con la base de {@link SyncApplication}, el
 * {@link Outbox} y registra el observador de sincronización.</p>
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
        MesaRepositorioLocal repositorio = new MesaRepositorioLocal(
                app.baseDeDatos().mesaDao(), outbox, app);
        SyncApplication.registrarObservador(TipoOperacion.Modulo.MESAS, repositorio);
        return (T) new MesasViewModel(repositorio, Executors.newSingleThreadExecutor());
    }
}
