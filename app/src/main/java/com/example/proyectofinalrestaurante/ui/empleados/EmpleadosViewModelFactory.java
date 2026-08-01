package com.example.proyectofinalrestaurante.ui.empleados;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.proyectofinalrestaurante.core.SesionActual;
import com.example.proyectofinalrestaurante.core.SupabaseClient;
import com.example.proyectofinalrestaurante.core.SyncApplication;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.repository.EmpleadoRemoto;
import com.example.proyectofinalrestaurante.data.repository.EmpleadoRepositorioLocal;
import com.example.proyectofinalrestaurante.domain.model.Sesion;

import java.util.concurrent.Executors;

/**
 * Composition root del módulo Empleados (DI manual — ver P-002).
 *
 * <p>Desde la migración a offline-first el repositorio es local-first: lee de Room y escribe
 * optimista. Se construye con la base de {@link SyncApplication} (singleton del proceso), su
 * partición del {@link Outbox} y el cliente remoto, que solo usa el alta.</p>
 *
 * <p>El repositorio implementa {@code ObservadorSincronizacion}; acá se registra para que el
 * {@code SyncWorker} le avise el estado global de la sincronización.</p>
 */
public class EmpleadosViewModelFactory implements ViewModelProvider.Factory {

    private final Application aplicacion;

    public EmpleadosViewModelFactory(@NonNull Application aplicacion) {
        this.aplicacion = aplicacion;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        SyncApplication app = (SyncApplication) aplicacion;
        // El token se lee en cada llamada, no una sola vez: si la sesión cambia
        // (cerrar sesión, o el selector de rol de debug), se usa la vigente.
        EmpleadoRemoto remoto = new EmpleadoRemoto(
                SupabaseClient.getEmpleadoApi(),
                () -> {
                    Sesion sesion = SesionActual.obtener();
                    return sesion == null ? null : sesion.getAccessToken();
                });
        Outbox outbox = new Outbox(app.baseDeDatos().operacionPendienteDao(),
                TipoOperacion.Modulo.EMPLEADOS);
        EmpleadoRepositorioLocal repositorio =
                new EmpleadoRepositorioLocal(app.baseDeDatos(), remoto, outbox, app);
        SyncApplication.registrarObservador(TipoOperacion.Modulo.EMPLEADOS, repositorio);
        return (T) new EmpleadosViewModel(repositorio, Executors.newSingleThreadExecutor());
    }
}
