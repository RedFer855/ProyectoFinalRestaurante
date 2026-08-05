package com.example.proyectofinalrestaurante.ui.buzon;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.proyectofinalrestaurante.core.SesionActual;
import com.example.proyectofinalrestaurante.core.SyncApplication;
import com.example.proyectofinalrestaurante.data.repository.NotificacionRepositorioLocal;
import com.example.proyectofinalrestaurante.domain.model.Sesion;

import java.util.concurrent.Executors;

/**
 * Composition root del buzón (DI manual — ver P-002). La sesión actual entra por acá: el
 * repositorio filtra {@code rol}/{@code idAuth} (Plan Fase 3, §4.6) sin acoplarse a
 * {@code SesionActual}.
 */
public class BuzonViewModelFactory implements ViewModelProvider.Factory {

    private final Application aplicacion;

    public BuzonViewModelFactory(@NonNull Application aplicacion) {
        this.aplicacion = aplicacion;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        SyncApplication app = (SyncApplication) aplicacion;
        Sesion sesion = SesionActual.obtener();
        NotificacionRepositorioLocal repositorio = new NotificacionRepositorioLocal(
                app.baseDeDatos(),
                sesion == null ? null : sesion.getRol(),
                sesion == null ? null : sesion.getIdUsuario());
        return (T) new BuzonViewModel(repositorio, Executors.newSingleThreadExecutor());
    }
}