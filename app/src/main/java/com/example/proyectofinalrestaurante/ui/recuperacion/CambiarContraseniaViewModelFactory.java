package com.example.proyectofinalrestaurante.ui.recuperacion;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.proyectofinalrestaurante.core.SupabaseClient;
import com.example.proyectofinalrestaurante.data.repository.SupabaseAuthRepository;
import com.example.proyectofinalrestaurante.domain.repository.AuthRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Composition root manual del paso 2 de recuperación (ver deuda P-002). */
public class CambiarContraseniaViewModelFactory implements ViewModelProvider.Factory {

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        AuthRepository authRepository =
                new SupabaseAuthRepository(SupabaseClient.getAuthApi(), SupabaseClient.getPerfilApi());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        return (T) new CambiarContraseniaViewModel(authRepository, executor);
    }
}
