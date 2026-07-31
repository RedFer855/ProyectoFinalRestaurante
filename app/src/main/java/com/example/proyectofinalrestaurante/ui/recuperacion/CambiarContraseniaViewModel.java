package com.example.proyectofinalrestaurante.ui.recuperacion;

import android.os.CountDownTimer;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.proyectofinalrestaurante.domain.RequisitoContrasenia;
import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.ResultadoValidacion;
import com.example.proyectofinalrestaurante.domain.ValidadorContrasenia;
import com.example.proyectofinalrestaurante.domain.repository.AuthRepository;

import java.util.concurrent.ExecutorService;

/**
 * ViewModel del paso 2 de recuperación: verificar el código OTP y fijar la contraseña
 * nueva. El {@link ExecutorService} se inyecta (no se replica P-005). El contador de
 * 60 s para "Reenviar código" vive acá, no en la Activity, para sobrevivir a la
 * rotación de pantalla. Nunca se loguea el código ni el access_token.
 */
public class CambiarContraseniaViewModel extends ViewModel {

    private static final long REENVIO_MS = 60_000L;
    private static final long INTERVALO_TICK_MS = 1_000L;
    private static final int LONGITUD_CODIGO = 6;

    private final AuthRepository authRepository;
    private final ExecutorService executor;
    private final MutableLiveData<EstadoCambioContrasenia> estado =
            new MutableLiveData<>(EstadoCambioContrasenia.builder().build());

    private String correo;
    private String codigo = "";
    private String nuevaContrasenia = "";
    private String confirmacion = "";
    private boolean cargando;
    private boolean cambioExitoso;
    private int segundosRestantes;
    private CountDownTimer contador;

    public CambiarContraseniaViewModel(
            @NonNull AuthRepository authRepository,
            @NonNull ExecutorService executor) {
        this.authRepository = authRepository;
        this.executor = executor;
    }

    public LiveData<EstadoCambioContrasenia> getEstado() {
        return estado;
    }

    /** Fija el correo al que se envió el código (viene del paso 1). Idempotente. */
    public void setCorreo(String correo) {
        if (this.correo == null && correo != null) {
            this.correo = correo;
            iniciarContador();
        }
    }

    public void onCodigoCambiado(String codigo) {
        this.codigo = codigo == null ? "" : codigo;
        publicar();
    }

    public void onNuevaContraseniaCambiada(String nueva) {
        this.nuevaContrasenia = nueva == null ? "" : nueva;
        publicar();
    }

    public void onConfirmacionCambiada(String confirmacion) {
        this.confirmacion = confirmacion == null ? "" : confirmacion;
        publicar();
    }

    public void reenviarCodigo() {
        if (cargando || correo == null) {
            return;
        }
        executor.execute(() -> authRepository.solicitarCodigo(correo));
        iniciarContador();
        publicar();
    }

    public void cambiarContrasenia() {
        if (!estado.getValue().isPuedeCambiar()) {
            return;
        }

        cargando = true;
        publicar();
        executor.execute(() -> {
            Result<String> verificacion = authRepository.verificarCodigo(correo, codigo);
            if (!verificacion.isSuccess()) {
                cargando = false;
                estado.postValue(estadoActual().error(verificacion.getError()).build());
                return;
            }

            String accessToken = verificacion.getValue();
            Result<Void> cambio = authRepository.cambiarContrasenia(accessToken, nuevaContrasenia);
            cargando = false;
            if (cambio.isSuccess()) {
                cambioExitoso = true;
                estado.postValue(estadoActual().build());
            } else {
                estado.postValue(estadoActual().error(cambio.getError()).build());
            }
        });
    }

    /** Marca el evento de éxito como consumido (patrón one-shot, ver P-013). */
    public void onNavegacionConsumida() {
        cambioExitoso = false;
        estado.setValue(estadoActual().build());
    }

    private void iniciarContador() {
        if (contador != null) {
            contador.cancel();
        }
        segundosRestantes = (int) (REENVIO_MS / INTERVALO_TICK_MS);
        contador = new CountDownTimer(REENVIO_MS, INTERVALO_TICK_MS) {
            @Override
            public void onTick(long millisHastaFinalizar) {
                segundosRestantes = (int) (millisHastaFinalizar / INTERVALO_TICK_MS);
                publicar();
            }

            @Override
            public void onFinish() {
                segundosRestantes = 0;
                publicar();
            }
        };
        contador.start();
    }

    private void publicar() {
        estado.setValue(estadoActual().build());
    }

    private EstadoCambioContrasenia.Builder estadoActual() {
        ResultadoValidacion validacion = ValidadorContrasenia.validar(nuevaContrasenia);
        boolean coincide = nuevaContrasenia.equals(confirmacion) && !nuevaContrasenia.isEmpty();

        boolean codigoCompleto = codigo.length() == LONGITUD_CODIGO;
        boolean puedeCambiar = !cargando && codigoCompleto
                && validacion.esValida() && coincide;

        return EstadoCambioContrasenia.builder()
                .cargando(cargando)
                .codigo(codigo)
                .incumplidos(validacion.getIncumplidos())
                .contrasenasCoinciden(coincide)
                .segundosRestantes(segundosRestantes)
                .puedeReenviar(!cargando && segundosRestantes == 0)
                .puedeCambiar(puedeCambiar)
                .cambioExitoso(cambioExitoso);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (contador != null) {
            contador.cancel();
        }
        executor.shutdown();
    }
}
