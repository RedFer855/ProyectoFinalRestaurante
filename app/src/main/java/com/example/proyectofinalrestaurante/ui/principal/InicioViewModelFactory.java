package com.example.proyectofinalrestaurante.ui.principal;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.proyectofinalrestaurante.core.SyncApplication;
import com.example.proyectofinalrestaurante.data.repository.ResumenRepositorioLocal;
import com.example.proyectofinalrestaurante.ui.permisos.VistaPorPermiso;

/**
 * Composition root de Inicio (DI manual — ver P-002). Sin {@code Outbox} ni executor: el
 * módulo no escribe nada y {@code ResumenRepositorioLocal} no hace I/O de red (Plan Fase 3c,
 * §7.2).
 */
public class InicioViewModelFactory implements ViewModelProvider.Factory {

    private final Application aplicacion;

    public InicioViewModelFactory(@NonNull Application aplicacion) {
        this.aplicacion = aplicacion;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        SyncApplication app = (SyncApplication) aplicacion;
        ResumenRepositorioLocal repositorio = new ResumenRepositorioLocal(app.baseDeDatos());
        return (T) new InicioViewModel(repositorio, VistaPorPermiso.rolActual());
    }
}
