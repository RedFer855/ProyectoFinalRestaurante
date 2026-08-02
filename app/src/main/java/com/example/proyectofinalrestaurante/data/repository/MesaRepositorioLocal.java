package com.example.proyectofinalrestaurante.data.repository;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.proyectofinalrestaurante.data.local.AppDatabase;
import com.example.proyectofinalrestaurante.data.local.dao.EstadoMesaDao;
import com.example.proyectofinalrestaurante.data.local.dao.MesaDao;
import com.example.proyectofinalrestaurante.data.local.entity.EstadoMesaEntity;
import com.example.proyectofinalrestaurante.data.local.entity.MesaEntity;
import com.example.proyectofinalrestaurante.data.local.mapper.MesaMapper;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.sync.ObservadorSincronizacion;
import com.example.proyectofinalrestaurante.data.sync.SyncScheduler;
import com.example.proyectofinalrestaurante.domain.ReglasMesa;
import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.EstadoMesa;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.Mesa;
import com.example.proyectofinalrestaurante.domain.model.NuevaMesa;
import com.example.proyectofinalrestaurante.domain.repository.MesaRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementación local-first de {@link MesaRepository} (Plan Fase 2c, E4). Mismo patrón que
 * {@code MenuRepositorioLocal}: la UI lee por {@link LiveData} desde Room y las escrituras son
 * optimistas — se escriben en la base, se encola la operación en el {@link Outbox} y se
 * dispara el {@code SyncScheduler}; el {@code SyncWorker} sube en segundo plano.
 *
 * <p><b>El catálogo {@code estado_mesa}</b> (§5.4 del plan) no tiene un endpoint propio en el
 * contrato HTTP de la Parte B (§3 solo define cinco rutas, ninguna es "listar catálogo"): se
 * siembra localmente con los tres valores fijos de {@link EstadoMesa} — que son exactamente
 * los que la Parte A inserta por contrato (Plan Fase 2c, §2.2) — en vez de inventar una
 * llamada de red que el plan no pidió. Si el servidor llega a exponer un catálogo dinámico
 * más adelante, este sembrado se reemplaza por un delta real sin tocar el resto del módulo.</p>
 */
public final class MesaRepositorioLocal implements MesaRepository, ObservadorSincronizacion {

    private static final String NO_SE_ENCONTRO_MESA = "No se encontró la mesa en la base local.";
    private static final String MESA_DE_BAJA =
            "Esa mesa está dada de baja: no se le puede cambiar el estado.";

    private final MesaDao mesaDao;
    private final EstadoMesaDao estadoMesaDao;
    private final Outbox outbox;
    private final Context contexto;

    private final MutableLiveData<EstadoSincronizacion> estadoSincronizacion =
            new MutableLiveData<>(new EstadoSincronizacion(false, null));

    public MesaRepositorioLocal(AppDatabase base, Outbox outbox, Context contexto) {
        this.mesaDao = base.mesaDao();
        this.estadoMesaDao = base.estadoMesaDao();
        this.outbox = outbox;
        this.contexto = contexto;
        sembrarCatalogoEstados();
    }

    // ------------------------------------------------------------------ lecturas

    @Override
    public LiveData<List<Mesa>> observarMesas() {
        return Transformations.map(mesaDao.observarTodas(), MesaRepositorioLocal::aDominio);
    }

    @Override
    public LiveData<List<EstadoMesa>> observarEstadosMesa() {
        return Transformations.map(estadoMesaDao.observarTodos(), MesaRepositorioLocal::estadosADominio);
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
        MesaEntity fila = new MesaEntity();
        fila.setNumeroMesa(nueva.getNumeroMesa());
        fila.setCapacidad(nueva.getCapacidad());
        fila.setUbicacion(nueva.getUbicacion());
        // Default del servidor: Libre y activa (Plan Fase 2c, §2.2). CrearMesaDto no manda
        // estos valores; se guardan igual en la fila local para que la UI los muestre ya.
        fila.setIdEstadoMesa(EstadoMesa.LIBRE.getId());
        fila.setIdEstado(1);
        fila.setActivo(true);
        fila.setEstadoSync(EstadoSync.PENDIENTE.name());
        long idLocal = mesaDao.insertar(fila);

        outbox.encolar(TipoOperacion.CREAR_MESA, idLocal, null, null);
        sincronizar();
        return Result.ok(idLocal);
    }

    @Override
    public Result<Void> actualizarMesa(int idLocal, NuevaMesa datos) {
        MesaEntity fila = mesaDao.porIdLocal(idLocal);
        if (fila == null) {
            return Result.fail(NO_SE_ENCONTRO_MESA);
        }
        fila.setNumeroMesa(datos.getNumeroMesa());
        fila.setCapacidad(datos.getCapacidad());
        fila.setUbicacion(datos.getUbicacion());
        fila.setEstadoSync(EstadoSync.PENDIENTE.name());
        mesaDao.actualizar(fila);

        if (fila.getIdServidor() == null) {
            // Todavía no se subió: la edición ya viaja en el CREAR pendiente, que lee la fila
            // fresca al drenar (mismo pliegue que el Menú).
            return Result.ok(null);
        }
        outbox.encolar(TipoOperacion.ACTUALIZAR_MESA, idLocal, null, null);
        sincronizar();
        return Result.ok(null);
    }

    @Override
    public Result<Void> cambiarEstadoMesa(int idLocal, EstadoMesa nuevoEstado) {
        MesaEntity fila = mesaDao.porIdLocal(idLocal);
        if (fila == null) {
            return Result.fail(NO_SE_ENCONTRO_MESA);
        }
        if (!fila.isActivo()) {
            // Espeja al RPC del servidor: solo actualiza filas con id_estado = 1.
            return Result.fail(MESA_DE_BAJA);
        }
        fila.setIdEstadoMesa(nuevoEstado.getId());
        fila.setEstadoSync(EstadoSync.PENDIENTE.name());
        mesaDao.actualizar(fila);

        if (fila.getIdServidor() == null) {
            // El CREAR pendiente todavía no subió: cuando lo haga, compara este estado
            // contra el default del servidor y lo corrige con un segundo viaje si hace
            // falta (ver SincronizadorMesas.crearMesa). No se encola CAMBIAR_ESTADO_MESA
            // porque no hay id_servidor contra el cual mandarlo todavía.
            return Result.ok(null);
        }
        outbox.encolar(TipoOperacion.CAMBIAR_ESTADO_MESA, idLocal, null, null);
        sincronizar();
        return Result.ok(null);
    }

    @Override
    public Result<Void> cambiarBajaMesa(int idLocal, boolean activo) {
        MesaEntity fila = mesaDao.porIdLocal(idLocal);
        if (fila == null) {
            return Result.fail(NO_SE_ENCONTRO_MESA);
        }
        fila.setIdEstado(activo ? 1 : 2);
        fila.setActivo(activo);
        fila.setEstadoSync(EstadoSync.PENDIENTE.name());
        mesaDao.actualizar(fila);

        if (fila.getIdServidor() == null) {
            return Result.ok(null);
        }
        outbox.encolar(TipoOperacion.CAMBIAR_BAJA_MESA, idLocal, null, null);
        sincronizar();
        return Result.ok(null);
    }

    // ------------------------------------------------------------------ helpers

    /** Ver el Javadoc de la clase: sembrado local, no un fetch de red. */
    private void sembrarCatalogoEstados() {
        List<EstadoMesaEntity> catalogo = new ArrayList<>();
        for (EstadoMesa estado : EstadoMesa.values()) {
            EstadoMesaEntity entidad = new EstadoMesaEntity();
            entidad.setIdEstadoMesa(estado.getId());
            entidad.setDescripcion(estado.name());
            entidad.setOrden(estado.ordinal());
            catalogo.add(entidad);
        }
        estadoMesaDao.insertarTodos(catalogo);
    }

    private static List<Mesa> aDominio(List<MesaEntity> entidades) {
        List<Mesa> dominio = new ArrayList<>(entidades.size());
        for (MesaEntity entidad : entidades) {
            dominio.add(MesaMapper.aDominio(entidad));
        }
        return dominio;
    }

    private static List<EstadoMesa> estadosADominio(List<EstadoMesaEntity> entidades) {
        List<EstadoMesa> dominio = new ArrayList<>();
        for (EstadoMesaEntity entidad : entidades) {
            EstadoMesa estado = EstadoMesa.porId(entidad.getIdEstadoMesa());
            if (estado != null) {
                dominio.add(estado);
            }
        }
        return dominio;
    }
}
