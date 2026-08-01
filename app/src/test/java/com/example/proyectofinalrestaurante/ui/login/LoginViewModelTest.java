package com.example.proyectofinalrestaurante.ui.login;

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
 * Ver P-005 en Deuda Técnica - Pendientes: antes el {@code Executor} se creaba adentro
 * del ViewModel y ningún test podía forzar ejecución síncrona. Ahora se inyecta.
 */
public class LoginViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Test
    public void login_camposVacios_muestraErrorSinLlamarAlRepositorio() {
        FakeAuthRepository repositorio = new FakeAuthRepository();
        LoginViewModel viewModel = new LoginViewModel(repositorio, new ExecutorServiceSincrono());

        viewModel.login("", "");

        EstadoLogin estado = viewModel.getEstado().getValue();
        assertEquals("Completá correo y contraseña", estado.getError());
        assertFalse(repositorio.loginLlamado);
    }

    @Test
    public void login_exitoso_publicaEstadoConSesion() {
        Sesion sesion = new Sesion("id-1", "ana@restaurante.hn", "token-123", "Ana", "mesero");
        FakeAuthRepository repositorio = new FakeAuthRepository();
        repositorio.resultadoLogin = Result.ok(sesion);
        LoginViewModel viewModel = new LoginViewModel(repositorio, new ExecutorServiceSincrono());

        viewModel.login("ana@restaurante.hn", "Clave123!");

        EstadoLogin estado = viewModel.getEstado().getValue();
        assertNull(estado.getError());
        assertEquals(sesion, estado.getSesion());
        assertTrue(repositorio.loginLlamado);
    }

    @Test
    public void login_credencialesInvalidas_publicaEstadoConError() {
        FakeAuthRepository repositorio = new FakeAuthRepository();
        repositorio.resultadoLogin = Result.fail("Correo o contraseña incorrectos");
        LoginViewModel viewModel = new LoginViewModel(repositorio, new ExecutorServiceSincrono());

        viewModel.login("ana@restaurante.hn", "claveIncorrecta");

        EstadoLogin estado = viewModel.getEstado().getValue();
        assertEquals("Correo o contraseña incorrectos", estado.getError());
        assertNull(estado.getSesion());
    }

    /** Fake de {@link AuthRepository}: sin red, sin Retrofit — solo lo que el test necesita. */
    private static final class FakeAuthRepository implements AuthRepository {

        boolean loginLlamado = false;
        Result<Sesion> resultadoLogin = Result.fail("no configurado");

        @Override
        public Result<Sesion> login(String correo, String contrasenia) {
            loginLlamado = true;
            return resultadoLogin;
        }

        @Override
        public Result<Void> solicitarCodigo(String correo) {
            throw new UnsupportedOperationException("No usado en este test");
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

    /**
     * Ejecuta cada tarea en el mismo hilo que la envía, en vez de en un hilo de fondo real.
     * Junto con {@link InstantTaskExecutorRule} (que hace síncrono el postValue de LiveData),
     * el resultado de {@code login()} queda disponible apenas retorna la llamada.
     */
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
