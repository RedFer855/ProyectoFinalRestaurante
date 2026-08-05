package com.example.proyectofinalrestaurante.data.repository;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.proyectofinalrestaurante.data.local.AppDatabase;
import com.example.proyectofinalrestaurante.data.local.dao.ClienteDao;
import com.example.proyectofinalrestaurante.data.local.dao.DetallePedidoDao;
import com.example.proyectofinalrestaurante.data.local.dao.EstadoPedidoDao;
import com.example.proyectofinalrestaurante.data.local.dao.PedidoDao;
import com.example.proyectofinalrestaurante.data.local.dao.PlatilloDao;
import com.example.proyectofinalrestaurante.data.local.entity.ClienteEntity;
import com.example.proyectofinalrestaurante.data.local.entity.DetallePedidoEntity;
import com.example.proyectofinalrestaurante.data.local.entity.EstadoPedidoEntity;
import com.example.proyectofinalrestaurante.data.local.entity.PedidoEntity;
import com.example.proyectofinalrestaurante.data.local.entity.PlatilloEntity;
import com.example.proyectofinalrestaurante.data.local.mapper.DetallePedidoMapper;
import com.example.proyectofinalrestaurante.data.local.mapper.EstadoPedidoMapper;
import com.example.proyectofinalrestaurante.data.local.mapper.PedidoMapper;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.sync.ObservadorSincronizacion;
import com.example.proyectofinalrestaurante.data.sync.payload.PayloadOperacion;
import com.example.proyectofinalrestaurante.data.sync.SyncScheduler;
import com.example.proyectofinalrestaurante.data.sync.payload.PayloadCrearPedido;
import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.EstadoPedido;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.LineaCarrito;
import com.example.proyectofinalrestaurante.domain.model.LineaPedido;
import com.example.proyectofinalrestaurante.domain.model.NuevoPedido;
import com.example.proyectofinalrestaurante.domain.model.Pedido;
import com.example.proyectofinalrestaurante.domain.repository.PedidoRepository;
import com.example.proyectofinalrestaurante.domain.ValidadorPedido;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    private final DetallePedidoDao detallePedidoDao;
    private final PlatilloDao platilloDao;
    private final ClienteDao clienteDao;
    private final EstadoPedidoDao estadoPedidoDao;
    private final Outbox outbox;
    private final Context contexto;
    private final AppDatabase base;

    private final MutableLiveData<EstadoSincronizacion> estadoSincronizacion =
            new MutableLiveData<>(new EstadoSincronizacion(false, null));

    public PedidoRepositorioLocal(AppDatabase base, Outbox outbox, Context contexto) {
        this.pedidoDao = base.pedidoDao();
        this.detallePedidoDao = base.detallePedidoDao();
        this.platilloDao = base.platilloDao();
        this.clienteDao = base.clienteDao();
        this.estadoPedidoDao = base.estadoPedidoDao();
        this.outbox = outbox;
        this.contexto = contexto;
        this.base = base;
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

    @Override
    public Result<Long> crear(NuevoPedido nuevo) {
        if (!ValidadorPedido.esValido(nuevo)) {
            return Result.fail("El pedido no tiene líneas válidas.");
        }
        // La cabecera y las N líneas entran en UNA transacción de Room (ADR-009): o queda
        // el pedido completo o no queda nada. El id local de la cabecera se usa en las
        // líneas apenas se inserta (autoincrement dentro de la transacción).
        final long[] idLocal = {0};
        base.runInTransaction(() -> {
            PedidoEntity cabecera = new PedidoEntity();
            cabecera.setFecha(Instant.now().toString());
            cabecera.setIdEstadoPedido(EstadoPedido.PENDIENTE.getId());
            cabecera.setClaveIdempotencia(nuevo.getClaveIdempotencia());
            cabecera.setNumeroMesa(nuevo.getIdLocalMesa());
            cabecera.setCliente(clienteDe(nuevo));
            cabecera.setTotal(0);
            cabecera.setCantidadItems(0);
            cabecera.setEstadoSync(EstadoSync.PENDIENTE.name());
            idLocal[0] = pedidoDao.insertar(cabecera);
            if (idLocal[0] <= 0) {
                throw new IllegalStateException("No se pudo insertar la cabecera del pedido.");
            }
            List<DetallePedidoEntity> lineas = new ArrayList<>(nuevo.getCarrito().getLineas().size());
            for (LineaCarrito linea : nuevo.getCarrito().getLineas()) {
                // El total local es una estimación (ADR-010): el servidor sella el real.
                lineas.add(DetallePedidoMapper.aEntidad(idLocal[0],
                        linea.getIdLocalPlatillo(), linea.getCantidad(), linea.subtotal()));
            }
            detallePedidoDao.insertarTodos(lineas);
        });
        outbox.encolar(TipoOperacion.CREAR_PEDIDO, idLocal[0],
                payloadDe(nuevo), null);
        sincronizar();
        return Result.ok(idLocal[0]);
    }

    // ------------------------------------------------------------------ helpers

    @Override
    public LiveData<List<LineaPedido>> observarDetalle(long idLocal) {
        return Transformations.map(
                detallePedidoDao.observarDelPedido(idLocal),
                lineas -> {
                    List<LineaPedido> dominio = new ArrayList<>(lineas.size());
                    for (DetallePedidoEntity detalle : lineas) {
                        StringBuilder nombre = new StringBuilder();
                        PlatilloEntity platillo = platilloDao.porIdLocal(detalle.getIdPlatilloLocal());
                        if (platillo != null) {
                            nombre.append(platillo.getNombre());
                        }
                        dominio.add(DetallePedidoMapper.aDominio(detalle, nombre.toString()));
                    }
                    return dominio;
                });
    }

    /**
     * Payload del {@code CREAR_PEDIDO}: el JSONB del RPC {@code crear_pedido} (§5.3). El
     * {@code id_platillo} es el del servidor (un platillo sin subir no puede pedirse:
     * B6 y {@code ReglasPedido.puedePedirse}); la clave de idempotencia hace idempotente al
     * reintento (B2).
     */
    private String payloadDe(NuevoPedido nuevo) {
        List<PayloadCrearPedido.Linea> lineas =
                new ArrayList<>(nuevo.getCarrito().getLineas().size());
        for (LineaCarrito linea : nuevo.getCarrito().getLineas()) {
            PlatilloEntity platillo = platilloDao.porIdLocal(linea.getIdLocalPlatillo());
            int idPlatillo = platillo == null || platillo.getIdServidor() == null
                    ? 0 : platillo.getIdServidor();
            // Un id nulo manda línea inválida; la prevención es ReglasPedido.puedePedirse,
            // y el RPC rechaza (raise exception → 400 → permanente) como cinturón de seguridad.
            lineas.add(new PayloadCrearPedido.Linea(idPlatillo, linea.getCantidad()));
        }
        return PayloadCrearPedido.serializar(
                nuevo.getClaveIdempotencia(),
                Instant.now().toString(),
                nuevo.getTipoPedido().getId(),
                nuevo.getIdLocalMesa(),
                nuevo.getIdLocalCliente(),
                lineas);
    }

    /** Nombre legible del cliente para la cabecera local (la vista del servidor lo trae). */
    @Nullable
    private String clienteDe(NuevoPedido nuevo) {
        if (nuevo.getIdLocalCliente() == null) {
            return null;
        }
        ClienteEntity cliente = clienteDao.porIdLocal(nuevo.getIdLocalCliente());
        if (cliente == null) {
            return null;
        }
        return (cliente.getNombre() + " " + cliente.getApellido()).trim();
    }

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
