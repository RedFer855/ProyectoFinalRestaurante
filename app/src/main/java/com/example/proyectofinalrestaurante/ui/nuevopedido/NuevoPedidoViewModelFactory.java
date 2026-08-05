package com.example.proyectofinalrestaurante.ui.nuevopedido;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.proyectofinalrestaurante.core.SesionActual;
import com.example.proyectofinalrestaurante.core.SupabaseClient;
import com.example.proyectofinalrestaurante.core.SyncApplication;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.repository.ClienteRemoto;
import com.example.proyectofinalrestaurante.data.repository.ClienteRepositorioLocal;
import com.example.proyectofinalrestaurante.data.repository.MenuRepositorioLocal;
import com.example.proyectofinalrestaurante.data.repository.MesaRepositorioLocal;
import com.example.proyectofinalrestaurante.data.repository.PedidoRepositorioLocal;
import com.example.proyectofinalrestaurante.domain.model.Sesion;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Composition root de la toma del pedido (DI manual — ver P-002). Mismo patrón que
 * {@code PedidosViewModelFactory}: arma los cuatro repositorios locales sobre la base de
 * {@link SyncApplication} y el rol de la sesión entra vía {@link SesionActual} para el flag
 * {@code puedeTomarPedido}.
 */
public class NuevoPedidoViewModelFactory implements ViewModelProvider.Factory {

    private final Application aplicacion;

    public NuevoPedidoViewModelFactory(@NonNull Application aplicacion) {
        this.aplicacion = aplicacion;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        SyncApplication app = (SyncApplication) aplicacion;
        Outbox outboxPedidos = new Outbox(app.baseDeDatos().operacionPendienteDao(),
                TipoOperacion.Modulo.PEDIDOS);
        PedidoRepositorioLocal pedidoRepositorio =
                new PedidoRepositorioLocal(app.baseDeDatos(), outboxPedidos, app);
        SyncApplication.registrarObservador(TipoOperacion.Modulo.PEDIDOS, pedidoRepositorio);

        Outbox outboxMenu = new Outbox(app.baseDeDatos().operacionPendienteDao(),
                TipoOperacion.Modulo.MENU);
        MenuRepositorioLocal menuRepositorio = new MenuRepositorioLocal(
                app.baseDeDatos(), outboxMenu, app.getFilesDir(), app);
        SyncApplication.registrarObservador(TipoOperacion.Modulo.MENU, menuRepositorio);

        ExecutorService executor = Executors.newSingleThreadExecutor();

        Outbox outboxMesas = new Outbox(app.baseDeDatos().operacionPendienteDao(),
                TipoOperacion.Modulo.MESAS);
        MesaRepositorioLocal mesaRepositorio =
                new MesaRepositorioLocal(app.baseDeDatos(), outboxMesas, app, executor);
        SyncApplication.registrarObservador(TipoOperacion.Modulo.MESAS, mesaRepositorio);

        ClienteRemoto remoto = new ClienteRemoto(
                SupabaseClient.getClienteApi(),
                () -> {
                    Sesion sesion = SesionActual.obtener();
                    return sesion == null ? null : sesion.getAccessToken();
                });
        Outbox outboxClientes = new Outbox(app.baseDeDatos().operacionPendienteDao(),
                TipoOperacion.Modulo.CLIENTES);
        ClienteRepositorioLocal clienteRepositorio =
                new ClienteRepositorioLocal(app.baseDeDatos(), outboxClientes, remoto, app);
        SyncApplication.registrarObservador(TipoOperacion.Modulo.CLIENTES, clienteRepositorio);

        Sesion sesion = SesionActual.obtener();
        String rol = sesion == null ? null : sesion.getRol();
        return (T) new NuevoPedidoViewModel(pedidoRepositorio, menuRepositorio, mesaRepositorio,
                clienteRepositorio, executor, rol);
    }
}