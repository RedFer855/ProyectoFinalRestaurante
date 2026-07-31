package com.example.proyectofinalrestaurante.ui.recuperacion;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.repository.AuthRepository;

import java.util.concurrent.ExecutorService;

/**
 * ViewModel del paso 1 de recuperación (pedir el código OTP). El {@link ExecutorService}
 * se inyecta por constructor (no se crea adentro — no se replica la deuda P-005).
 */
public class SolicitarCodigoViewModel extends ViewModel {

    private final AuthRepository authRepository;
    private final ExecutorService executor;
    private final MutableLiveData<EstadoSolicitudCodigo> estado =
            new MutableLiveData<>(EstadoSolicitudCodigo.inicial());

    public SolicitarCodigoViewModel(
            @NonNull AuthRepository authRepository,
            @NonNull ExecutorService executor) {
        this.authRepository = authRepository;
        this.executor = executor;
    }

    public LiveData<EstadoSolicitudCodigo> getEstado() {
        return estado;
    }

    public void solicitarCodigo(String correo) {
        String correoNormalizado = correo == null ? "" : correo.trim();
        if (correoNormalizado.isEmpty() || !correoNormalizado.contains("@")) {
            estado.setValue(EstadoSolicitudCodigo.error("Ingresá un correo válido"));
            return;
        }

        estado.setValue(EstadoSolicitudCodigo.cargando());
        executor.execute(() -> {
            Result<Void> resultado = authRepository.solicitarCodigo(correoNormalizado);
            if (resultado.isSuccess()) {
                estado.postValue(EstadoSolicitudCodigo.confirmado(correoNormalizado));
            } else {
                estado.postValue(EstadoSolicitudCodigo.error(resultado.getError()));
            }
        });
    }

    /** Marca el evento de navegación como consumido (patrón de evento one-shot, ver P-013). */
    public void onNavegacionConsumida() {
        estado.setValue(EstadoSolicitudCodigo.inicial());
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
