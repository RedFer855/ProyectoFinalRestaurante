package com.example.proyectofinalrestaurante.ui.detallepedido;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.proyectofinalrestaurante.core.SyncApplication;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.repository.PedidoRepositorioLocal;

import java.util.concurrent.Executors;

/**
 * Composition root del detalle de pedido (DI manual — ver P-002). Mismo cableado que
 * {@code PedidosViewModelFactory}: la ocasión reutiliza {@link PedidoRepositorioLocal} del mismo
 * módulo, ya que {@code observarDetalle} vive en ese contrato. El ejecutor se crea acá, no dentro
 * del ViewModel (P-005).
 */
public class DetallePedidoViewModelFactory implements ViewModelProvider.Factory {

    private final Application aplicacion;

    public DetallePedidoViewModelFactory(@NonNull Application aplicacion) {
        this.aplicacion = aplicacion;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        SyncApplication app = (SyncApplication) aplicacion;
        Outbox outbox = new Outbox(app.baseDeDatos().operacionPendienteDao(),
                TipoOperacion.Modulo.PEDIDOS);
        PedidoRepositorioLocal repositorio =
                new PedidoRepositorioLocal(app.baseDeDatos(), outbox, app);
        SyncApplication.registrarObservador(TipoOperacion.Modulo.PEDIDOS, repositorio);
        return (T) new DetallePedidoViewModel(repositorio, Executors.newSingleThreadExecutor());
    }
}