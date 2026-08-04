package com.example.proyectofinalrestaurante.data.repository;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.proyectofinalrestaurante.data.local.dao.MesaDao;
import com.example.proyectofinalrestaurante.data.local.entity.MesaEntity;
import com.example.proyectofinalrestaurante.data.local.mapper.MesaMapper;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.sync.ObservadorSincronizacion;
import com.example.proyectofinalrestaurante.data.sync.SyncScheduler;
import com.example.proyectofinalrestaurante.domain.model.EstadoMesa;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.Mesa;
import com.example.proyectofinalrestaurante.domain.model.NuevaMesa;
import com.example.proyectofinalrestaurante.domain.repository.MesaRepository;
import com.example.proyectofinalrestaurante.domain.repository.Result;

import java.util.List;

/**
 * Implementación local-first de {@link MesaRepository} (Fase 2c).
 *
 * <p>La UI lee por {@link LiveData} desde Room (que nunca falla) y las escrituras son
 * optimistas — se escriben en la base, se encola la operación en el {@link Outbox} y se
 * dispara el {@link SyncScheduler}; el {@code SyncWorker} la sube en segundo plano cuando
 * haya red.</p>
 *
 * <p>Implementa {@link ObservadorSincronizacion} para que el worker le avise el estado
 * global de la sincronización.</p>
 */
public final class MesaRepositorioLocal implements MesaRepository, ObservadorSincronizacion {

    private static final String NO_SE_ENCONTRO_MESA = "No se encontró la mesa en la base local.";

    private final MesaDao mesaDao;
    private final Outbox outbox;
    private final Context contexto;

    private final MutableLiveData<EstadoSincronizacion> estadoSincronizacion =
            new MutableLiveData<>(new EstadoSincronizacion(false, null));

    public MesaRepositorioLocal(MesaDao mesaDao, Outbox outbox, Context contexto) {
        this.mesaDao = mesaDao;
        this.outbox = outbox;
        this.contexto = contexto;
    }

    // ------------------------------------------------------------------ lecturas

    @Override
    public LiveData<List<Mesa>> observarMesas() {
        return Transformations.map(mesaDao.observarTodas(),
                MesaRepositorioLocal::aDominio);
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
    public Result<Long> crearMesa(NuevaMesa nueva) {
        MesaEntity entidad = new MesaEntity();
        entidad.setNumeroMesa(nueva.getNumeroMesa());
        entidad.setCapacidad(nueva.getCapacidad());
        entidad.setUbicacion(nueva.getUbicacion());
        entidad.setEstadoMesa(EstadoMesa.LIBRE.name());
        entidad.setActivo(true);
        entidad.setEstadoSync(EstadoSync.SINCRONIZAR.name());
        entidad.setActualizadoEn(null);
        entidad.setIdServidor(null);

        long idLocal = mesaDao.insertar(entidad);

        outbox.encolar(
                TipoOperacion.CREAR_MESA,
                idLocal,
                null,
                null);

        SyncScheduler.solicitar(contexto);

        return Result.exito(idLocal);
    }

    @Override
    public Result<Void> actualizarMesa(int idLocal, NuevaMesa datos) {
        MesaEntity existente = mesaDao.porIdLocal(idLocal);
        if (existente == null) {
            return Result.fallo(NO_SE_ENCONTRO_MESA);
        }

        existente.setNumeroMesa(datos.getNumeroMesa());
        existente.setCapacidad(datos.getCapacidad());
        existente.setUbicacion(datos.getUbicacion());
        existente.setEstadoSync(EstadoSync.SINCRONIZAR.name());
        mesaDao.actualizar(existente);

        if (existente.getIdServidor() != null) {
            outbox.encolar(
                    TipoOperacion.ACTUALIZAR_MESA,
                    existente.getIdLocal(),
                    null,
                    null);
            SyncScheduler.solicitar(contexto);
        }

        return Result.exito(null);
    }

    @Override
    public Result<Void> cambiarEstadoMesa(int idLocal, EstadoMesa nuevoEstado) {
        MesaEntity existente = mesaDao.porIdLocal(idLocal);
        if (existente == null) {
            return Result.fallo(NO_SE_ENCONTRO_MESA);
        }

        existente.setEstadoMesa(nuevoEstado.name());
        existente.setEstadoSync(EstadoSync.SINCRONIZAR.name());
        mesaDao.actualizar(existente);

        if (existente.getIdServidor() != null) {
            outbox.encolar(
                    TipoOperacion.CAMBIAR_ESTADO_MESA,
                    existente.getIdLocal(),
                    null,
                    String.valueOf(nuevoEstado.getIdServidor()));
            SyncScheduler.solicitar(contexto);
        }

        return Result.exito(null);
    }

    @Override
    public Result<Void> darDeBajaMesa(int idLocal) {
        MesaEntity existente = mesaDao.porIdLocal(idLocal);
        if (existente == null) {
            return Result.fallo(NO_SE_ENCONTRO_MESA);
        }

        existente.setActivo(false);
        existente.setEstadoSync(EstadoSync.SINCRONIZAR.name());
        mesaDao.actualizar(existente);

        if (existente.getIdServidor() != null) {
            outbox.encolar(
                    TipoOperacion.CAMBIAR_BAJA_MESA,
                    existente.getIdLocal(),
                    null,
                    "0");
            SyncScheduler.solicitar(contexto);
        }

        return Result.exito(null);
    }

    @Override
    public Result<Void> reactivarMesa(int idLocal) {
        MesaEntity existente = mesaDao.porIdLocal(idLocal);
        if (existente == null) {
            return Result.fallo(NO_SE_ENCONTRO_MESA);
        }

        existente.setActivo(true);
        existente.setEstadoSync(EstadoSync.SINCRONIZAR.name());
        mesaDao.actualizar(existente);

        if (existente.getIdServidor() != null) {
            outbox.encolar(
                    TipoOperacion.CAMBIAR_BAJA_MESA,
                    existente.getIdLocal(),
                    null,
                    "1");
            SyncScheduler.solicitar(contexto);
        }

        return Result.exito(null);
    }

    // ------------------------------------------------------------------ helpers

    private static List<Mesa> aDominio(List<MesaEntity> entidades) {
        return MesaMapper.aDominioLista(entidades);
    }
}
