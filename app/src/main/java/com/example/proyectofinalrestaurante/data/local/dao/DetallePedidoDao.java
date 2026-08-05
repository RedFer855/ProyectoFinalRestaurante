package com.example.proyectofinalrestaurante.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.proyectofinalrestaurante.data.local.entity.DetallePedidoEntity;

import java.util.List;

/**
 * DAO del detalle de pedidos (Plan Fase 3b, §6.2). La lectura es <b>bajo demanda</b>: solo
 * cuando el {@code DetallePedidoHoja} se abre (E9). Las N líneas entran en la misma
 * transacción que la cabecera en {@code PedidoRepositorioLocal.crear()}.
 *
 * <p>La búsqueda por {@code id_servidor} la usa el {@code SincronizadorPedidos} para
 * resolver la respuesta del {@code CREAR_PEDIDO}: cuando el RPC devuelve el id real del
 * pedido, se reescribe el {@code id_servidor} de todas las líneas que apuntan al
 * {@code id_local} de la cabecera.</p>
 */
@Dao
public interface DetallePedidoDao {

    @Query("SELECT * FROM detalle_pedido WHERE id_pedido = :idPedidoLocal ORDER BY id_local ASC")
    LiveData<List<DetallePedidoEntity>> observarDelPedido(long idPedidoLocal);

    @Query("SELECT * FROM detalle_pedido WHERE id_pedido = :idPedidoLocal ORDER BY id_local ASC")
    List<DetallePedidoEntity> delPedido(long idPedidoLocal);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertarTodos(List<DetallePedidoEntity> lineas);
}