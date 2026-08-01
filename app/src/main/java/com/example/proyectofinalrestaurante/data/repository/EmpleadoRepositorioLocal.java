package com.example.proyectofinalrestaurante.data.repository;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.proyectofinalrestaurante.data.local.AppDatabase;
import com.example.proyectofinalrestaurante.data.local.dao.EmpleadoDao;
import com.example.proyectofinalrestaurante.data.local.entity.EmpleadoEntity;
import com.example.proyectofinalrestaurante.data.local.mapper.EmpleadoMapper;
import com.example.proyectofinalrestaurante.data.outbox.ClasificadorDeError;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.remote.dto.CrearEmpleadoResponseDto;
import com.example.proyectofinalrestaurante.data.sync.ObservadorSincronizacion;
import com.example.proyectofinalrestaurante.data.sync.SyncScheduler;
import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.Empleado;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.NuevoEmpleado;
import com.example.proyectofinalrestaurante.domain.repository.EmpleadoRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementación local-first de {@link EmpleadoRepository}.
 *
 * <p>Mismo patrón que {@code MenuRepositorioLocal}: la UI lee por {@link LiveData} desde Room
 * (que nunca falla) y las escrituras son optimistas — se escriben en la base, se encola la
 * operación en el {@link Outbox} y se dispara el {@link SyncScheduler}; el {@code SyncWorker}
 * la sube cuando haya red.</p>
 *
 * <p><b>El alta es la excepción declarada:</b> {@link #crear} llama al servidor de forma
 * síncrona porque crea una cuenta en Supabase Auth. Es el único método que puede devolver un
 * error de red, y la UI lo trata como tal.</p>
 */
public final class EmpleadoRepositorioLocal
        implements EmpleadoRepository, ObservadorSincronizacion {

    static final String NO_SE_ENCONTRO =
            "No se encontró el empleado en la base local.";
    static final String ALTA_NECESITA_CONEXION =
            "Para crear un empleado necesitás conexión: hay que darle de alta su cuenta de "
                    + "acceso. El resto de los cambios sí se guardan sin internet.";

    private final EmpleadoDao empleadoDao;
    private final EmpleadoRemoto remoto;
    private final Outbox outbox;
    private final Context contexto;

    private final MutableLiveData<EstadoSincronizacion> estadoSincronizacion =
            new MutableLiveData<>(new EstadoSincronizacion(false, null));

    public EmpleadoRepositorioLocal(AppDatabase base, EmpleadoRemoto remoto, Outbox outbox,
                                    Context contexto) {
        this.empleadoDao = base.empleadoDao();
        this.remoto = remoto;
        this.outbox = outbox;
        this.contexto = contexto;
    }

    // ------------------------------------------------------------------ lecturas

    @Override
    public LiveData<List<Empleado>> observarEmpleados() {
        return Transformations.map(empleadoDao.observarTodos(),
                EmpleadoRepositorioLocal::aDominio);
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

    // ------------------------------------------------------------------ alta (online)

    /**
     * Alta contra el servidor, sin outbox. Si sale bien, la fila se inserta ya sincronizada
     * en la base local para que aparezca de inmediato, sin esperar al delta.
     */
    @Override
    public Result<Integer> crear(NuevoEmpleado nuevo) {
        ResultadoRed<CrearEmpleadoResponseDto> resultado = remoto.crear(nuevo);
        if (!resultado.isExitoso()) {
            // Sin código HTTP = ni siquiera salió la petición. Decirle "sin conexión" a
            // secas invitaría a reintentar hasta que aparezca la señal; el mensaje explica
            // por qué justo esta operación no se puede encolar como las demás.
            if (resultado.getCodigoHttp() == ClasificadorDeError.SIN_CODIGO) {
                return Result.fail(ALTA_NECESITA_CONEXION);
            }
            return Result.fail(resultado.getMensaje());
        }
        CrearEmpleadoResponseDto creado = resultado.getValor();

        EmpleadoEntity fila = new EmpleadoEntity();
        fila.setIdEmpleado(creado.getIdEmpleado());
        fila.setNombres(nuevo.getNombres());
        fila.setApellidos(nuevo.getApellidos());
        fila.setIdentidad(nuevo.getIdentidad());
        fila.setTelefono(nuevo.getTelefono());
        fila.setCorreo(nuevo.getCorreo());
        fila.setApodoUsuario(creado.getApodoUsuario());
        fila.setIdAuthUser(creado.getIdAuthUser());
        fila.setRol(nuevo.getRol());
        fila.setActivo(true);
        fila.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        // Sin `actualizado_en`: la respuesta del alta no lo trae. Queda null a propósito, y
        // el próximo delta la va a bajar completa — es una fila de más en una sola pasada,
        // a cambio de no inventar una marca que el servidor no dio.
        empleadoDao.insertar(fila);

        sincronizar();
        return Result.ok(creado.getIdEmpleado());
    }

    // ------------------------------------------------------------------ escrituras optimistas

    @Override
    public Result<Void> actualizarDatos(Empleado empleado) {
        EmpleadoEntity fila = empleadoDao.porId(empleado.getIdEmpleado());
        if (fila == null) {
            return Result.fail(NO_SE_ENCONTRO);
        }
        fila.setNombres(empleado.getNombres());
        fila.setApellidos(empleado.getApellidos());
        fila.setIdentidad(empleado.getIdentidad());
        fila.setTelefono(empleado.getTelefono());
        fila.setCorreo(empleado.getCorreo());
        fila.setEstadoSync(EstadoSync.PENDIENTE.name());
        empleadoDao.actualizar(fila);

        outbox.encolar(TipoOperacion.ACTUALIZAR_EMPLEADO, fila.getIdEmpleado(), null, null);
        sincronizar();
        return Result.ok(null);
    }

    @Override
    public Result<Void> cambiarRol(int idEmpleado, String nuevoRol) {
        EmpleadoEntity fila = empleadoDao.porId(idEmpleado);
        if (fila == null) {
            return Result.fail(NO_SE_ENCONTRO);
        }
        fila.setRol(nuevoRol);
        fila.setEstadoSync(EstadoSync.PENDIENTE.name());
        empleadoDao.actualizar(fila);

        outbox.encolar(TipoOperacion.CAMBIAR_ROL_EMPLEADO, idEmpleado, null, null);
        sincronizar();
        return Result.ok(null);
    }

    @Override
    public Result<Void> cambiarEstado(int idEmpleado, boolean activo) {
        EmpleadoEntity fila = empleadoDao.porId(idEmpleado);
        if (fila == null) {
            return Result.fail(NO_SE_ENCONTRO);
        }
        fila.setActivo(activo);
        fila.setEstadoSync(EstadoSync.PENDIENTE.name());
        empleadoDao.actualizar(fila);

        outbox.encolar(TipoOperacion.CAMBIAR_ESTADO_EMPLEADO, idEmpleado, null, null);
        sincronizar();
        return Result.ok(null);
    }

    // ------------------------------------------------------------------ helpers

    private static List<Empleado> aDominio(List<EmpleadoEntity> entidades) {
        List<Empleado> dominio = new ArrayList<>(entidades.size());
        for (EmpleadoEntity entidad : entidades) {
            dominio.add(EmpleadoMapper.aDominio(entidad));
        }
        return dominio;
    }
}
