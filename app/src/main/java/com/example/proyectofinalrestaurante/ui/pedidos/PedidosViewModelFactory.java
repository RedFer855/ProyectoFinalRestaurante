package com.example.proyectofinalrestaurante.ui.pedidos;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.proyectofinalrestaurante.core.SesionActual;
import com.example.proyectofinalrestaurante.core.SyncApplication;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.repository.MesaRepositorioLocal;
import com.example.proyectofinalrestaurante.data.repository.PedidoRepositorioLocal;
import com.example.proyectofinalrestaurante.domain.model.Sesion;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Composition root del módulo Pedidos (DI manual — ver P-002). Mismo patrón que
 * {@code ClientesViewModelFactory}. El rol de la sesión entra por acá (vía
 * {@link SesionActual}) para que el ViewModel pueda validar transiciones con
 * {@code ReglasPedido}.
 */
public class PedidosViewModelFactory implements ViewModelProvider.Factory {

    private final Application aplicacion;

    public PedidosViewModelFactory(@NonNull Application aplicacion) {
        this.aplicacion = aplicacion;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        SyncApplication app = (SyncApplication) aplicacion;
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Outbox outboxMesas = new Outbox(app.baseDeDatos().operacionPendienteDao(),
                TipoOperacion.Modulo.MESAS);
        MesaRepositorioLocal mesaRepositorio =
                new MesaRepositorioLocal(app.baseDeDatos(), outboxMesas, app, executor);
        SyncApplication.registrarObservador(TipoOperacion.Modulo.MESAS, mesaRepositorio);

        Outbox outbox = new Outbox(app.baseDeDatos().operacionPendienteDao(),
                TipoOperacion.Modulo.PEDIDOS);
        PedidoRepositorioLocal repositorio =
                new PedidoRepositorioLocal(app.baseDeDatos(), outbox, app, mesaRepositorio);
        SyncApplication.registrarObservador(TipoOperacion.Modulo.PEDIDOS, repositorio);

        Sesion sesion = SesionActual.obtener();
        String rol = sesion == null ? null : sesion.getRol();
        return (T) new PedidosViewModel(repositorio, executor, rol);
    }
}
