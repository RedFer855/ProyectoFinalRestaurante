package com.example.proyectofinalrestaurante.ui.menu;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.proyectofinalrestaurante.core.SesionActual;
import com.example.proyectofinalrestaurante.core.SupabaseClient;
import com.example.proyectofinalrestaurante.data.repository.SupabaseMenuRepository;
import com.example.proyectofinalrestaurante.domain.model.Sesion;
import com.example.proyectofinalrestaurante.domain.repository.MenuRepository;

import java.util.concurrent.Executors;

/** Composition root del módulo Menú (DI manual — ver P-002). */
public class MenuViewModelFactory implements ViewModelProvider.Factory {

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        // El token se lee en cada llamada, no una sola vez: si la sesión cambia
        // (cerrar sesión, o el selector de rol de debug), el repositorio usa la vigente.
        MenuRepository repositorio = new SupabaseMenuRepository(
                SupabaseClient.getMenuApi(),
                SupabaseClient.getStorageApi(),
                () -> {
                    Sesion sesion = SesionActual.obtener();
                    return sesion == null ? null : sesion.getAccessToken();
                });
        return (T) new MenuViewModel(repositorio, Executors.newSingleThreadExecutor());
    }
}
