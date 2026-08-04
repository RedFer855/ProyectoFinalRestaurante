package com.example.proyectofinalrestaurante.data.repository;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.proyectofinalrestaurante.data.local.dao.ClienteDao;
import com.example.proyectofinalrestaurante.data.local.entity.ClienteEntity;
import com.example.proyectofinalrestaurante.data.local.mapper.ClienteMapper;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.sync.ObservadorSincronizacion;
import com.example.proyectofinalrestaurante.data.sync.SyncScheduler;
import com.example.proyectofinalrestaurante.domain.model.Cliente;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.NuevoCliente;
import com.example.proyectofinalrestaurante.domain.repository.ClienteRepository;
import com.example.proyectofinalrestaurante.domain.repository.Result;

import java.util.List;

/**
 * Implementación local-first de {@link ClienteRepository} (Fase 2d).
 *
 * <p>La UI lee por {@link LiveData} desde Room (que nunca falla) y las escrituras son
 * optimistas — se escriben en la base, se encola la operación en el {@link Outbox} y se
 * dispara el {@link SyncScheduler}; el {@code SyncWorker} la sube en segundo plano cuando
 * haya red.</p>
 *
 * <p>Implementa {@link ObservadorSincronizacion} para que el worker le avise el estado
 * global de la sincronización.</p>
 */
public final class ClienteRepositorioLocal implements ClienteRepository, ObservadorSincronizacion {

    private static final String NO_SE_ENCONTRO_CLIENTE =
            "No se encontró el cliente en la base local.";

    private final ClienteDao clienteDao;
    private final Outbox outbox;
    private final Context contexto;

    private final MutableLiveData<EstadoSincronizacion> estadoSincronizacion =
            new MutableLiveData<>(new EstadoSincronizacion(false, null));

    public ClienteRepositorioLocal(ClienteDao clienteDao, Outbox outbox, Context contexto) {
        this.clienteDao = clienteDao;
        this.outbox = outbox;
        this.contexto = contexto;
    }

    // ------------------------------------------------------------------ lecturas

    @Override
    public LiveData<List<Cliente>> observarClientes() {
        return Transformations.map(clienteDao.observarTodos(),
                ClienteRepositorioLocal::aDominio);
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
        ClienteEntity entidad = new ClienteEntity();
        entidad.setNombre(nuevo.getNombre());
        entidad.setApellido(nuevo.getApellido());
        entidad.setIdentidad(nuevo.getIdentidad());
        entidad.setTelefono(nuevo.getTelefono());
        entidad.setActivo(true);
        entidad.setCantidadPedidos(0);
        entidad.setEstadoSync(EstadoSync.SINCRONIZAR.name());
        entidad.setActualizadoEn(null);
        entidad.setIdServidor(null);

        long idLocal = clienteDao.insertar(entidad);

        outbox.encolar(
                TipoOperacion.CREAR_CLIENTE,
                idLocal,
                null,
                null);

        SyncScheduler.solicitar(contexto);

        return Result.exito(idLocal);
    }

    @Override
    public Result<Void> actualizarCliente(int idLocal, NuevoCliente datos) {
        ClienteEntity existente = clienteDao.porIdLocal(idLocal);
        if (existente == null) {
            return Result.fallo(NO_SE_ENCONTRO_CLIENTE);
        }

        existente.setNombre(datos.getNombre());
        existente.setApellido(datos.getApellido());
        existente.setIdentidad(datos.getIdentidad());
        existente.setTelefono(datos.getTelefono());
        existente.setEstadoSync(EstadoSync.SINCRONIZAR.name());
        clienteDao.actualizar(existente);

        if (existente.getIdServidor() != null) {
            outbox.encolar(
                    TipoOperacion.ACTUALIZAR_CLIENTE,
                    existente.getIdLocal(),
                    null,
                    null);
            SyncScheduler.solicitar(contexto);
        }

        return Result.exito(null);
    }

    @Override
    public Result<Void> cambiarEstadoCliente(int idLocal, boolean activo) {
        ClienteEntity existente = clienteDao.porIdLocal(idLocal);
        if (existente == null) {
            return Result.fallo(NO_SE_ENCONTRO_CLIENTE);
        }

        existente.setActivo(activo);
        existente.setEstadoSync(EstadoSync.SINCRONIZAR.name());
        clienteDao.actualizar(existente);

        if (existente.getIdServidor() != null) {
            outbox.encolar(
                    TipoOperacion.CAMBIAR_ESTADO_CLIENTE,
                    existente.getIdLocal(),
                    null,
                    activo ? "1" : "0");
            SyncScheduler.solicitar(contexto);
        }

        return Result.exito(null);
    }

    @Override
    public Result<Void> borrarCliente(int idLocal) {
        ClienteEntity existente = clienteDao.porIdLocal(idLocal);
        if (existente == null) {
            return Result.fallo(NO_SE_ENCONTRO_CLIENTE);
        }

        if (existente.getIdServidor() == null) {
            clienteDao.borrar(existente);
            return Result.exito(null);
        }

        Result<Void> validacion = com.example.proyectofinalrestaurante.domain.ReglasCliente
                .validarBorrado(existente.getCantidadPedidos(), existente.getNombre());
        if (!validacion.esExito()) {
            return validacion;
        }

        outbox.encolar(
                TipoOperacion.BORRAR_CLIENTE,
                existente.getIdLocal(),
                null,
                String.valueOf(existente.getIdServidor()));
        SyncScheduler.solicitar(contexto);

        return Result.exito(null);
    }

    // ------------------------------------------------------------------ helpers

    private static List<Cliente> aDominio(List<ClienteEntity> entidades) {
        return ClienteMapper.aDominioLista(entidades);
    }
}
