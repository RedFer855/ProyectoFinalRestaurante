package com.example.proyectofinalrestaurante.domain.repository;

import androidx.lifecycle.LiveData;

import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.EstadoPedido;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.Pedido;

import java.util.List;

/**
 * Contrato del módulo Pedidos (Domain Layer). {@code data} lo implementa; es la única cara
 * que {@code ui} ve de los pedidos. Misma forma que {@link MesaRepository}: las lecturas
 * son {@link LiveData} sobre Room y nunca fallan; las escrituras son optimistas — escriben
 * en Room, encolan en el outbox y devuelven {@code Result} aunque no haya red.
 *
 * <p>La lectura es una <b>ventana creciente</b> (Plan Fase 3, §4.5): el ViewModel arranca
 * en 20 y pide {@code cargarMas()} para sumar 20 más. La lista crece al deslizar — nunca
 * bajar todos los pedidos de golpe ({@code R6}).</p>
 *
 * <p>Los ids que la UI le pasa a las escrituras son siempre <b>locales</b>
 * ({@code idLocal}); el mapeo a {@code id_servidor} vive en {@code data}.</p>
 */
public interface PedidoRepository {

    /** Los primeros {@code ventana} pedidos, orden FIFO ({@code fecha ASC}), observados. */
    LiveData<List<Pedido>> observarVentana(int ventana);

    /**
     * Total de pedidos en Room, para derivar {@code hayMas}: {@code hayMas} cuando
     * {@code contarTotal() > ventana} — derivado de la lista, nunca una bandera suelta.
     */
    LiveData<Integer> contarPedidos();

    /** Catálogo {@code estado_pedido} para los filtros del tablero. */
    LiveData<List<EstadoPedido>> observarEstadosPedido();

    /** Sincronizando en este momento y, si algo se cayó de forma permanente, el error. */
    LiveData<EstadoSincronizacion> getEstadoSincronizacion();

    /** Pide una sincronización (drenar el outbox y bajar el delta). No bloquea. */
    void sincronizar();

    /**
     * Avanza el estado del pedido (Pendiente → En preparación → Listo → Entregado, o
     * Cancelado). Va por el outbox con la operación {@code AVANZAR_ESTADO_PEDIDO}, que en
     * el servidor se ejecuta contra el RPC {@code avanzar_estado_pedido} — la única vía de
     * escritura del estado (Plan Fase 3, §2.5).
     */
    Result<Void> avanzarEstado(long idLocal, EstadoPedido nuevo);
}
