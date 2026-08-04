package com.example.proyectofinalrestaurante.domain.repository;

import androidx.lifecycle.LiveData;

import com.example.proyectofinalrestaurante.domain.model.Cliente;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.NuevoCliente;

import java.util.List;

/**
 * Contrato del módulo Clientes (Domain Layer). {@code data} lo implementa; es la única cara
 * que {@code ui} ve del módulo.
 *
 * <p>Desde la Fase 2b (offline-first) las <b>lecturas</b> son {@link LiveData} sobre Room:
 * nunca fallan y siempre reflejan lo que hay en disco al instante. Las <b>escrituras</b> son
 * optimistas — escriben en Room, encolan en el outbox y devuelven {@code Result} aunque no
 * haya red; la subida al servidor la hace en segundo plano el {@code SyncWorker}.</p>
 */
public interface ClienteRepository {

    LiveData<List<Cliente>> observarClientes();

    LiveData<EstadoSincronizacion> getEstadoSincronizacion();

    /** Sincronizar: drenar el outbox y bajar el delta. No bloquea. */
    void sincronizar();

    /** Crea el cliente en Room y encola su subida. Devuelve el {@code idLocal}. */
    Result<Long> crearCliente(NuevoCliente nuevo);

    /** Actualiza los datos de un cliente existente. */
    Result<Void> actualizarCliente(int idLocal, NuevoCliente datos);

    /** Da de baja lógica un cliente (activo = false). */
    Result<Void> darDeBajaCliente(int idLocal);

    /** Reactiva un cliente dado de baja. */
    Result<Void> reactivarCliente(int idLocal);

    /** Borra físicamente un cliente (solo si no tiene pedidos). */
    Result<Void> borrarCliente(int idLocal);
}
