package com.example.proyectofinalrestaurante.ui.login;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.proyectofinalrestaurante.core.SupabaseClient;
import com.example.proyectofinalrestaurante.data.repository.SupabaseAuthRepository;
import com.example.proyectofinalrestaurante.domain.repository.AuthRepository;

/**
 * Composition root manual (sin Hilt/Koin todavía — ver Deuda Técnica P-002).
 * Construye el AuthRepository concreto e inyecta la interfaz en el ViewModel.
 */
public class LoginViewModelFactory implements ViewModelProvider.Factory {

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        AuthRepository authRepository = new SupabaseAuthRepository(SupabaseClient.getAuthApi());
        return (T) new LoginViewModel(authRepository);
    }
}
