package com.example.proyectofinalrestaurante.data.repository;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.proyectofinalrestaurante.data.local.AppDatabase;
import com.example.proyectofinalrestaurante.data.local.dao.ClienteDao;
import com.example.proyectofinalrestaurante.data.local.entity.ClienteEntity;
import com.example.proyectofinalrestaurante.data.local.mapper.ClienteMapper;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.sync.ObservadorSincronizacion;
import com.example.proyectofinalrestaurante.data.sync.PayloadOperacion;
import com.example.proyectofinalrestaurante.data.sync.SyncScheduler;
import com.example.proyectofinalrestaurante.domain.ReglasCliente;
import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.Cliente;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.NuevoCliente;
import com.example.proyectofinalrestaurante.domain.repository.ClienteRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementación local-first de {@link ClienteRepository} (Plan Fase 2d, E4). Mismo patrón
 * que {@code MesaRepositorioLocal}, con dos diferencias: {@link #borrarCliente} es un borrado
 * real (mismo criterio que {@code MenuRepositorioLocal.borrarCategoria}) y
 * {@link #buscarOCrearCliente} no toca Room ni el outbox —exige conexión, por diseño
 * (Plan Fase 2d, §5.1)—, sino que llama directo al {@link ClienteRemoto}.
 */
public final class ClienteRepositorioLocal implements ClienteRepository, ObservadorSincronizacion {

    private static final String NO_SE_ENCONTRO_CLIENTE =
            "No se encontró el cliente en la base local.";
    private static final String NO_SE_PUEDE_BORRAR =
            "Ese cliente tiene pedidos: no se puede borrar, dalo de baja.";

    private final ClienteDao clienteDao;
    private final Outbox outbox;
    private final ClienteRemoto remoto;
    private final Context contexto;

    private final MutableLiveData<EstadoSincronizacion> estadoSincronizacion =
            new MutableLiveData<>(new EstadoSincronizacion(false, null));

    public ClienteRepositorioLocal(AppDatabase base, Outbox outbox, ClienteRemoto remoto,
                                   Context contexto) {
        this.clienteDao = base.clienteDao();
        this.outbox = outbox;
        this.remoto = remoto;
        this.contexto = contexto;
    }

    // ------------------------------------------------------------------ lecturas

    @Override
    public LiveData<List<Cliente>> observarClientes() {
        return Transformations.map(clienteDao.observarTodos(), ClienteRepositorioLocal::aDominio);
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
    public Result<Long> crearCliente(NuevoCliente nuevo) {
        ClienteEntity fila = new ClienteEntity();
        fila.setNombre(nuevo.getNombre());
        fila.setApellido(nuevo.getApellido());
        fila.setIdentidad(nuevo.getIdentidad());
        fila.setTelefono(nuevo.getTelefono());
        fila.setIdEstado(1);
        fila.setActivo(true);
        fila.setCantidadPedidos(0);
        fila.setEstadoSync(EstadoSync.PENDIENTE.name());
        long idLocal = clienteDao.insertar(fila);

        outbox.encolar(TipoOperacion.CREAR_CLIENTE, idLocal, null, null);
        sincronizar();
        return Result.ok(idLocal);
    }

    @Override
    public Result<Void> actualizarCliente(int idLocal, NuevoCliente datos) {
        ClienteEntity fila = clienteDao.porIdLocal(idLocal);
        if (fila == null) {
            return Result.fail(NO_SE_ENCONTRO_CLIENTE);
        }
        fila.setNombre(datos.getNombre());
        fila.setApellido(datos.getApellido());
        fila.setIdentidad(datos.getIdentidad());
        fila.setTelefono(datos.getTelefono());
        fila.setEstadoSync(EstadoSync.PENDIENTE.name());
        clienteDao.actualizar(fila);

        if (fila.getIdServidor() == null) {
            return Result.ok(null);
        }
        outbox.encolar(TipoOperacion.ACTUALIZAR_CLIENTE, idLocal, null, null);
        sincronizar();
        return Result.ok(null);
    }

    @Override
    public Result<Void> cambiarBajaCliente(int idLocal, boolean activo) {
        ClienteEntity fila = clienteDao.porIdLocal(idLocal);
        if (fila == null) {
            return Result.fail(NO_SE_ENCONTRO_CLIENTE);
        }
        fila.setIdEstado(activo ? 1 : 2);
        fila.setActivo(activo);
        fila.setEstadoSync(EstadoSync.PENDIENTE.name());
        clienteDao.actualizar(fila);

        if (fila.getIdServidor() == null) {
            // El CREAR pendiente todavía no subió; SincronizadorClientes.crearCliente
            // corrige la baja con un segundo viaje si hace falta.
            return Result.ok(null);
        }
        outbox.encolar(TipoOperacion.CAMBIAR_ESTADO_CLIENTE, idLocal, null, null);
        sincronizar();
        return Result.ok(null);
    }

    @Override
    public Result<Void> borrarCliente(int idLocal) {
        ClienteEntity fila = clienteDao.porIdLocal(idLocal);
        if (fila == null) {
            return Result.ok(null);
        }
        if (!ReglasCliente.puedeBorrarse(ClienteMapper.aDominio(fila))) {
            return Result.fail(NO_SE_PUEDE_BORRAR);
        }
        Integer idServidor = fila.getIdServidor();
        clienteDao.borrar(fila);

        if (idServidor == null) {
            // Nunca se subió: no hay nada que borrar en el servidor.
            return Result.ok(null);
        }
        // La fila local ya no existe: el id_servidor viaja en el payload (mismo patrón que
        // BORRAR_CATEGORIA del Menú).
        outbox.encolar(TipoOperacion.BORRAR_CLIENTE, idLocal,
                PayloadOperacion.borrarCategoria(idServidor), null);
        sincronizar();
        return Result.ok(null);
    }

    @Override
    public Result<Integer> buscarOCrearCliente(String nombre, String apellido,
                                                @Nullable String identidad,
                                                @Nullable String telefono) {
        ResultadoRed<Integer> resultado = remoto.buscarOCrear(nombre, apellido, identidad, telefono);
        if (!resultado.isExitoso()) {
            return Result.fail(resultado.getMensaje());
        }
        return Result.ok(resultado.getValor());
    }

    // ------------------------------------------------------------------ helpers

    private static List<Cliente> aDominio(List<ClienteEntity> entidades) {
        List<Cliente> dominio = new ArrayList<>(entidades.size());
        for (ClienteEntity entidad : entidades) {
            dominio.add(ClienteMapper.aDominio(entidad));
        }
        return dominio;
    }
}
