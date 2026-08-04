package com.example.proyectofinalrestaurante.domain.repository;

import androidx.lifecycle.LiveData;

import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.EstadoMesa;
import com.example.proyectofinalrestaurante.domain.model.Mesa;
import com.example.proyectofinalrestaurante.domain.model.NuevaMesa;

import java.util.List;

/**
 * Contrato del módulo Mesas (Domain Layer). {@code data} lo implementa; es la única cara
 * que {@code ui} ve del módulo.
 *
 * <p>Desde la Fase 2b (offline-first) las <b>lecturas</b> son {@link LiveData} sobre Room:
 * nunca fallan y siempre reflejan lo que hay en disco al instante. Las <b>escrituras</b> son
 * optimistas — escriben en Room, encolan en el outbox y devuelven {@code Result} aunque no
 * haya red; la subida al servidor la hace en segundo plano el {@code SyncWorker}.</p>
 */
public interface MesaRepository {

    LiveData<List<Mesa>> observarMesas();

    LiveData<EstadoSincronizacion> getEstadoSincronizacion();

    /** Sincronizar: drenar el outbox y bajar el delta. No bloquea. */
    void sincronizar();

    /** Crea la mesa en Room y encola su subida. Devuelve el {@code idLocal} de la fila creada. */
    Result<Long> crearMesa(NuevaMesa nueva);

    /** Actualiza los datos de una mesa existente (número, capacidad, ubicación). */
    Result<Void> actualizarMesa(int idLocal, NuevaMesa datos);

    /**
     * Cambia el estado operativo de una mesa (Libre ↔ Ocupada ↔ Reservada) via RPC.
     * El mesero solo puede hacer esto — no puede crear ni editar.
     */
    Result<Void> cambiarEstadoMesa(int idLocal, EstadoMesa nuevoEstado);

    /** Da de baja lógica una mesa (activo = false). Solo admin. */
    Result<Void> darDeBajaMesa(int idLocal);

    /** Reactiva una mesa dada de baja. Solo admin. */
    Result<Void> reactivarMesa(int idLocal);
}
