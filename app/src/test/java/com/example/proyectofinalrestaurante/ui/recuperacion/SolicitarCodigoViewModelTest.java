package com.example.proyectofinalrestaurante.ui.recuperacion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.Sesion;
import com.example.proyectofinalrestaurante.domain.repository.AuthRepository;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Paso 1 de recuperación. El caso importante es el fallo del envío: si el correo no salió,
 * la pantalla del código no debe abrirse — antes se navegaba igual y el usuario se quedaba
 * esperando un código inexistente.
 */
public class SolicitarCodigoViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Test
    public void solicitarCodigo_correoInvalido_muestraErrorSinLlamarAlRepositorio() {
        FakeAuthRepository repositorio = new FakeAuthRepository();
        SolicitarCodigoViewModel viewModel =
                new SolicitarCodigoViewModel(repositorio, new ExecutorServiceSincrono());

        viewModel.solicitarCodigo("sin-arroba");

        EstadoSolicitudCodigo estado = viewModel.getEstado().getValue();
        assertEquals("Ingresá un correo válido", estado.getError());
        assertNull(estado.getCorreoConfirmado());
        assertFalse(repositorio.solicitarCodigoLlamado);
    }

    @Test
    public void solicitarCodigo_exitoso_confirmaElCorreoNormalizado() {
        FakeAuthRepository repositorio = new FakeAuthRepository();
        repositorio.resultadoSolicitarCodigo = Result.ok(null);
        SolicitarCodigoViewModel viewModel =
                new SolicitarCodigoViewModel(repositorio, new ExecutorServiceSincrono());

        viewModel.solicitarCodigo("  ana@restaurante.hn  ");

        EstadoSolicitudCodigo estado = viewModel.getEstado().getValue();
        assertNull(estado.getError());
        assertEquals("ana@restaurante.hn", estado.getCorreoConfirmado());
        assertEquals("ana@restaurante.hn", repositorio.correoRecibido);
    }

    @Test
    public void solicitarCodigo_limiteDeCorreos_muestraElErrorYNoNavega() {
        FakeAuthRepository repositorio = new FakeAuthRepository();
        repositorio.resultadoSolicitarCodigo =
                Result.fail("Se alcanzó el límite de correos por hora. Esperá un momento y volvé a intentar.");
        SolicitarCodigoViewModel viewModel =
                new SolicitarCodigoViewModel(repositorio, new ExecutorServiceSincrono());

        viewModel.solicitarCodigo("ana@restaurante.hn");

        EstadoSolicitudCodigo estado = viewModel.getEstado().getValue();
        assertEquals("Se alcanzó el límite de correos por hora. Esperá un momento y volvé a intentar.",
                estado.getError());
        assertNull("sin correo enviado no se abre la pantalla del código", estado.getCorreoConfirmado());
        assertFalse(estado.isCargando());
        assertTrue(repositorio.solicitarCodigoLlamado);
    }

    /** Fake de {@link AuthRepository}: sin red, sin Retrofit — solo lo que el test necesita. */
    private static final class FakeAuthRepository implements AuthRepository {

        boolean solicitarCodigoLlamado = false;
        String correoRecibido;
        Result<Void> resultadoSolicitarCodigo = Result.fail("no configurado");

        @Override
        public Result<Sesion> login(String correo, String contrasenia) {
            throw new UnsupportedOperationException("No usado en este test");
        }

        @Override
        public Result<Void> solicitarCodigo(String correo) {
            solicitarCodigoLlamado = true;
            correoRecibido = correo;
            return resultadoSolicitarCodigo;
        }

        @Override
        public Result<String> verificarCodigo(String correo, String codigo) {
            throw new UnsupportedOperationException("No usado en este test");
        }

        @Override
        public Result<Void> cambiarContrasenia(String accessToken, String nuevaContrasenia) {
            throw new UnsupportedOperationException("No usado en este test");
        }
    }

    /** Mismo patrón que {@code LoginViewModelTest}: ejecuta en el hilo que envía la tarea. */
    private static final class ExecutorServiceSincrono extends AbstractExecutorService {

        private volatile boolean cerrado = false;

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
            cerrado = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            cerrado = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return cerrado;
        }

        @Override
        public boolean isTerminated() {
            return cerrado;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
