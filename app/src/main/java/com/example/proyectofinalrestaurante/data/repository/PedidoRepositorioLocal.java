package com.example.proyectofinalrestaurante.data.repository;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.proyectofinalrestaurante.data.local.AppDatabase;
import com.example.proyectofinalrestaurante.data.local.dao.EstadoPedidoDao;
import com.example.proyectofinalrestaurante.data.local.dao.PedidoDao;
import com.example.proyectofinalrestaurante.data.local.entity.EstadoPedidoEntity;
import com.example.proyectofinalrestaurante.data.local.entity.PedidoEntity;
import com.example.proyectofinalrestaurante.data.local.mapper.EstadoPedidoMapper;
import com.example.proyectofinalrestaurante.data.local.mapper.PedidoMapper;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.sync.ObservadorSincronizacion;
import com.example.proyectofinalrestaurante.data.sync.PayloadOperacion;
import com.example.proyectofinalrestaurante.data.sync.SyncScheduler;
import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.EstadoPedido;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.Pedido;
import com.example.proyectofinalrestaurante.domain.repository.PedidoRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementación local-first de {@link PedidoRepository} (Plan Fase 3, E7). Mismo patrón que
 * {@code ClienteRepositorioLocal}: lecturas {@link LiveData} sobre Room, escrituras optimistas
 * que encolan en el outbox y devuelven {@code Result} sin red.
 *
 * <p>La escritura del módulo es <b>solo</b> {@code avanzarEstado} (no hay {@code CREAR_PEDIDO}
 * en esta fase, §1.2): se reescribe {@code id_estado_pedido} local al optimista, se encola
 * {@code AVANZAR_ESTADO_PEDIDO} con el payload que el {@code SincronizadorPedidos} ya sabe
 * leer, y se pide sync. La validación de la transición (matriz de {@code ReglasPedido}) es de
 * la UI; acá no se duplica.</p>
 */
public final class PedidoRepositorioLocal implements PedidoRepository, ObservadorSincronizacion {

    private static final String NO_SE_ENCONTRO_PEDIDO = "No se encontró el pedido en la base local.";
    private static final String PEDIDO_SIN_SUBIR =
            "Ese pedido todavía no se sincronizó con el servidor.";

    private final PedidoDao pedidoDao;
    private final EstadoPedidoDao estadoPedidoDao;
    private final Outbox outbox;
    private final Context contexto;

    private final MutableLiveData<EstadoSincronizacion> estadoSincronizacion =
            new MutableLiveData<>(new EstadoSincronizacion(false, null));

    public PedidoRepositorioLocal(AppDatabase base, Outbox outbox, Context contexto) {
        this.pedidoDao = base.pedidoDao();
        this.estadoPedidoDao = base.estadoPedidoDao();
        this.outbox = outbox;
        this.contexto = contexto;
    }

    // ------------------------------------------------------------------ lecturas

    @Override
    public LiveData<List<Pedido>> observarVentana(int ventana) {
        return Transformations.map(
                pedidoDao.observarVentana(ventana), PedidoRepositorioLocal::aDominio);
    }

    @Override
    public LiveData<Integer> contarPedidos() {
        return pedidoDao.contarTotal();
    }

    @Override
    public LiveData<List<EstadoPedido>> observarEstadosPedido() {
        return Transformations.map(estadoPedidoDao.observarTodos(),
                PedidoRepositorioLocal::estadosADominio);
    }

    @Override
    public LiveData<EstadoSincronizacion> getEstadoSincronizacion() {
        return estadoSincronizacion;
    }

    // ------------------------------------------------------------------ sincronización

    @Override
    public void sincronizar() {
        SyncScheduler.solicitar(contexto);
    }

    @Override
    public void alIniciar() {
        estadoSincronizacion.postValue(new EstadoSincronizacion(true, null));
    }

    @Override
    public void alTerminar(@Nullable String ultimoError) {
        estadoSincronizacion.postValue(new EstadoSincronizacion(false, ultimoError));
    }

    // ------------------------------------------------------------------ escrituras

    @Override
    public Result<Void> avanzarEstado(long idLocal, EstadoPedido nuevo) {
        PedidoEntity fila = pedidoDao.porIdLocal(idLocal);
        if (fila == null) {
            return Result.fail(NO_SE_ENCONTRO_PEDIDO);
        }
        if (fila.getIdServidor() == null) {
            // El SincronizadorPedidos descarta operaciones sin id_servidor: no se encola
            // algo que el worker va a tirar.
            return Result.fail(PEDIDO_SIN_SUBIR);
        }
        fila.setIdEstadoPedido(nuevo.getId());
        fila.setEstadoSync(EstadoSync.PENDIENTE.name());
        pedidoDao.actualizar(fila);

        outbox.encolar(TipoOperacion.AVANZAR_ESTADO_PEDIDO, idLocal,
                PayloadOperacion.avanzarEstado(nuevo.getId()), null);
        sincronizar();
        return Result.ok(null);
    }

    // ------------------------------------------------------------------ helpers

    private static List<Pedido> aDominio(List<PedidoEntity> entidades) {
        List<Pedido> dominio = new ArrayList<>(entidades.size());
        for (PedidoEntity entidad : entidades) {
            dominio.add(PedidoMapper.aDominio(entidad));
        }
        return dominio;
    }

    private static List<EstadoPedido> estadosADominio(List<EstadoPedidoEntity> entidades) {
        List<EstadoPedido> dominio = new ArrayList<>(entidades.size());
        for (EstadoPedidoEntity entidad : entidades) {
            EstadoPedido estado = EstadoPedidoMapper.aDominio(entidad);
            if (estado != null) {
                // Estados que este APK no conoce no alimentan los chips del tablero.
                dominio.add(estado);
            }
        }
        return dominio;
    }
}
