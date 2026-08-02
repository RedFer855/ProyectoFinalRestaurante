package com.example.proyectofinalrestaurante.domain.repository;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.Cliente;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.NuevoCliente;

import java.util.List;

/**
 * Contrato del módulo Clientes (Domain Layer). {@code data} lo implementa; es la única cara
 * que {@code ui} ve de los clientes.
 *
 * <p>Misma forma offline-first que {@link MesaRepository}: lecturas por {@link LiveData}
 * sobre Room, escrituras optimistas que encolan en el outbox. La excepción es
 * {@link #buscarOCrearCliente}: por diseño (Plan Fase 2d, §5.1) <b>exige conexión</b> — el id
 * que devuelve lo genera el servidor, y este plan solo la deja expuesta para que la Fase 4
 * (Pedidos) decida cómo usarla.</p>
 */
public interface ClienteRepository {

    LiveData<List<Cliente>> observarClientes();

    LiveData<EstadoSincronizacion> getEstadoSincronizacion();

    void sincronizar();

    /** Crea el cliente en Room y encola su subida. @return el {@code idLocal} de la fila. */
    Result<Long> crearCliente(NuevoCliente nuevo);

    Result<Void> actualizarCliente(int idLocal, NuevoCliente datos);

    /** Baja ({@code activo = false}) o reactiva un cliente. */
    Result<Void> cambiarBajaCliente(int idLocal, boolean activo);

    /**
     * Borra el cliente de verdad. Solo tiene sentido si
     * {@link com.example.proyectofinalrestaurante.domain.ReglasCliente#puedeBorrarse} —el
     * servidor lo rechaza igual si tiene pedidos, pero no vale la pena gastar el viaje.
     */
    Result<Void> borrarCliente(int idLocal);

    /**
     * RPC {@code buscar_o_crear_cliente}: reusa el cliente si la identidad ya existe
     * (normalizada), o lo crea. <b>Exige conexión</b> — no pasa por Room ni por el outbox.
     *
     * @return el {@code id_cliente} del servidor.
     */
    Result<Integer> buscarOCrearCliente(String nombre, String apellido,
                                        @Nullable String identidad, @Nullable String telefono);
}
