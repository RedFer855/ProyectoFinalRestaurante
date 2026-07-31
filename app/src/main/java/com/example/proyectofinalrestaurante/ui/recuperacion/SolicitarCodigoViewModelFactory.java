package com.example.proyectofinalrestaurante.ui.recuperacion;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.proyectofinalrestaurante.core.SupabaseClient;
import com.example.proyectofinalrestaurante.data.repository.SupabaseAuthRepository;
import com.example.proyectofinalrestaurante.domain.repository.AuthRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Composition root manual del paso 1 de recuperación. Construye el AuthRepository
 * concreto e inyecta un {@link ExecutorService} para que el ViewModel sea testeable
 * (no se replica la deuda P-005).
 */
public class SolicitarCodigoViewModelFactory implements ViewModelProvider.Factory {

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        AuthRepository authRepository =
                new SupabaseAuthRepository(SupabaseClient.getAuthApi(), SupabaseClient.getPerfilApi());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        return (T) new SolicitarCodigoViewModel(authRepository, executor);
    }
}
