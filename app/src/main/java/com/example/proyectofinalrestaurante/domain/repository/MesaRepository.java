package com.example.proyectofinalrestaurante.domain.repository;

import androidx.lifecycle.LiveData;

import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.EstadoMesa;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.Mesa;
import com.example.proyectofinalrestaurante.domain.model.NuevaMesa;

import java.util.List;

/**
 * Contrato del módulo Mesas (Domain Layer). {@code data} lo implementa; es la única cara que
 * {@code ui} ve de las mesas.
 *
 * <p>Misma forma que {@link MenuRepository}: desde la Fase 2b las <b>lecturas</b> son
 * {@link LiveData} sobre Room y nunca fallan; las <b>escrituras</b> son optimistas — escriben
 * en Room, encolan en el outbox y devuelven {@code Result} aunque no haya red; la subida al
 * servidor la hace en segundo plano el {@code SyncWorker}. Ver Plan Fase 2c, §4.</p>
 *
 * <p>Los ids que la UI le pasa a las escrituras son siempre <b>locales</b>
 * ({@code idLocal}); el mapeo a {@code id_servidor} vive en {@code data}.</p>
 */
public interface MesaRepository {

    LiveData<List<Mesa>> observarMesas();

    /** Catálogo {@code estado_mesa} cacheado en Room desde la primera sincronización (Plan Fase 2c, §5.4). */
    LiveData<List<EstadoMesa>> observarEstadosMesa();

    /** Sincronizando en este momento y, si algo se cayó de forma permanente, el error. */
    LiveData<EstadoSincronizacion> getEstadoSincronizacion();

    /**
     * Pide una sincronización (drenar el outbox y bajar el delta). No bloquea: dispara el
     * trabajo único de {@code SyncWorker}, que ya corre con constraint de red.
     */
    void sincronizar();

    /**
     * Crea la mesa en Room y encola su subida.
     *
     * @return el {@code idLocal} de la fila creada
     */
    Result<Long> crearMesa(NuevaMesa nueva);

    /** Actualiza los datos de una mesa existente. Solo admin: la RLS niega al resto. */
    Result<Void> actualizarMesa(int idLocal, NuevaMesa datos);

    /**
     * Cambia el estado operativo (libre/ocupada/reservada). Es la única vía de escritura del
     * mesero: en el servidor va por el RPC {@code cambiar_estado_mesa()}, no por un
     * {@code PATCH} directo (Plan Fase 2c, §3 y §2.4).
     */
    Result<Void> cambiarEstadoMesa(int idLocal, EstadoMesa nuevoEstado);

    /** Da de baja ({@code activo = false}) o reactiva una mesa. Solo admin. Nunca se borra. */
    Result<Void> cambiarBajaMesa(int idLocal, boolean activo);
}
