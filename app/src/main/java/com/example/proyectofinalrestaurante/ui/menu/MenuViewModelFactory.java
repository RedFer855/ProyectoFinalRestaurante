package com.example.proyectofinalrestaurante.ui.menu;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.proyectofinalrestaurante.core.SyncApplication;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.repository.MenuRepositorioLocal;

import java.util.concurrent.Executors;

/**
 * Composition root del módulo Menú (DI manual — ver P-002).
 *
 * <p>Desde la Fase 2b el repositorio es local-first: lee de Room y escribe optimista. Se
 * construye con la base de {@link SyncApplication} (singleton del proceso), el {@link Outbox}
 * y la carpeta de imágenes. El repositorio implementa
 * {@link com.example.proyectofinalrestaurante.data.sync.ObservadorSincronizacion}; acá se
 * registra para que el {@code SyncWorker} le avise el estado global de la sincronización.</p>
 */
public class MenuViewModelFactory implements ViewModelProvider.Factory {

    private final Application aplicacion;

    public MenuViewModelFactory(@NonNull Application aplicacion) {
        this.aplicacion = aplicacion;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        SyncApplication app = (SyncApplication) aplicacion;
        Outbox outbox = new Outbox(app.baseDeDatos().operacionPendienteDao(),
                TipoOperacion.Modulo.MENU);
        MenuRepositorioLocal repositorio = new MenuRepositorioLocal(
                app.baseDeDatos(), outbox, app.getFilesDir(), app);
        SyncApplication.registrarObservador(TipoOperacion.Modulo.MENU, repositorio);
        return (T) new MenuViewModel(repositorio, Executors.newSingleThreadExecutor());
    }
}
