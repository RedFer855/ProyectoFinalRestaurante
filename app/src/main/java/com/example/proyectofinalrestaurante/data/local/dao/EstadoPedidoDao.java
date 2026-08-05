package com.example.proyectofinalrestaurante.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.example.proyectofinalrestaurante.data.local.entity.EstadoPedidoEntity;

import java.util.List;

/**
 * DAO del catálogo {@code estado_pedido} (Plan Fase 3, E3). Se reemplaza completo con cada
 * sincronización: el servidor es la única fuente de verdad para su contenido y su orden.
 */
@Dao
public interface EstadoPedidoDao {

    @Query("SELECT * FROM estados_pedido ORDER BY orden ASC")
    LiveData<List<EstadoPedidoEntity>> observarTodos();

    @Query("SELECT * FROM estados_pedido ORDER BY orden ASC")
    List<EstadoPedidoEntity> todosSincrono();

    @Query("DELETE FROM estados_pedido")
    void vaciar();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertarTodos(List<EstadoPedidoEntity> estados);

    /** Reemplaza el catálogo completo en una transacción (borra lo viejo, inserta lo nuevo). */
    @Transaction
    default void reemplazarTodos(List<EstadoPedidoEntity> estados) {
        vaciar();
        insertarTodos(estados);
    }
}