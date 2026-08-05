package com.example.proyectofinalrestaurante.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.proyectofinalrestaurante.data.local.entity.PedidoEntity;

import java.util.List;

/**
 * DAO de pedidos (Plan Fase 3, E3).
 *
 * <p>La lista del tablero es una <b>ventana creciente</b>, no páginas con {@code OFFSET}
 * (Plan Fase 3, §4.5): una sola consulta observada cuyo {@code LIMIT} crece con cada
 * {@code cargarMas()} del ViewModel. Cuando el delta escribe filas nuevas, Room re-emite y la
 * ventana se actualiza sola — el tiempo real y la paginación no se pelean.</p>
 *
 * <p>{@code contarTotal} alimenta {@code hayMas}: el scroll pide otra ventana solo si
 * {@code total > ventana} (regla 3 del protocolo: nada de banderas sueltas que puedan
 * contradecir a la lista).</p>
 *
 * <p><b>No hay {@code @Delete} a propósito</b>: los pedidos no se borran en esta fase
 * (§1.2) — se cancelan con {@code AVANZAR_ESTADO_PEDIDO}.</p>
 */
@Dao
public interface PedidoDao {

    @Query("SELECT * FROM pedidos ORDER BY fecha ASC, id_local ASC LIMIT :ventana")
    LiveData<List<PedidoEntity>> observarVentana(int ventana);

    @Query("SELECT COUNT(*) FROM pedidos")
    LiveData<Integer> contarTotal();

    /** Cuántos pedidos hay en un estado del catálogo (Plan Fase 3c, §5): dashboard de Inicio. */
    @Query("SELECT COUNT(*) FROM pedidos WHERE id_estado_pedido = :idEstadoPedido")
    LiveData<Integer> observarConteoPorEstado(int idEstadoPedido);

    @Query("SELECT COUNT(*) FROM pedidos WHERE estado_sync != 'SINCRONIZADO'")
    int contarNoSincronizadas();

    @Query("SELECT * FROM pedidos WHERE id_local = :idLocal LIMIT 1")
    PedidoEntity porIdLocal(long idLocal);

    @Query("SELECT * FROM pedidos WHERE id_servidor = :idServidor LIMIT 1")
    PedidoEntity porIdServidor(int idServidor);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertar(PedidoEntity pedido);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertarTodos(List<PedidoEntity> pedidos);

    @Update
    void actualizar(PedidoEntity pedido);
}