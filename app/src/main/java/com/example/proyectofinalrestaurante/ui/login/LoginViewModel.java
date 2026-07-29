package com.example.proyectofinalrestaurante.ui.login;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.Sesion;
import com.example.proyectofinalrestaurante.domain.repository.AuthRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** ViewModel de la pantalla de login. Solo conoce la interfaz AuthRepository (domain). */
public class LoginViewModel extends ViewModel {

    private final AuthRepository authRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<EstadoLogin> estado = new MutableLiveData<>(EstadoLogin.inicial());

    public LoginViewModel(@NonNull AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    public LiveData<EstadoLogin> getEstado() {
        return estado;
    }

    public void login(String correo, String contrasenia) {
        if (correo == null || correo.trim().isEmpty() || contrasenia == null || contrasenia.isEmpty()) {
            estado.setValue(EstadoLogin.error("Completá correo y contraseña"));
            return;
        }

        estado.setValue(EstadoLogin.cargando());
        executor.execute(() -> {
            Result<Sesion> resultado = authRepository.login(correo.trim(), contrasenia);
            if (resultado.isSuccess()) {
                estado.postValue(EstadoLogin.exito(resultado.getValue()));
            } else {
                estado.postValue(EstadoLogin.error(resultado.getError()));
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
