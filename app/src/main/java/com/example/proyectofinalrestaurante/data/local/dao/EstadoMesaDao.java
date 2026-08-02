package com.example.proyectofinalrestaurante.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.example.proyectofinalrestaurante.data.local.entity.EstadoMesaEntity;

import java.util.List;

/**
 * DAO del catálogo {@code estado_mesa} (Plan Fase 2c, E3). Se reemplaza completo con cada
 * sincronización: {@code REPLACE} solo actualiza las filas que ya existen y deja las que el
 * servidor eliminó, así que {@link #reemplazarTodos} borra el catálogo viejo e inserta el
 * nuevo en una transacción — el servidor es la única fuente de verdad para su contenido.
 */
@Dao
public interface EstadoMesaDao {

    @Query("SELECT * FROM estados_mesa ORDER BY orden ASC")
    LiveData<List<EstadoMesaEntity>> observarTodos();

    @Query("DELETE FROM estados_mesa")
    void vaciar();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertarTodos(List<EstadoMesaEntity> estados);

    /** Reemplaza el catálogo completo en una transacción (borra lo viejo, inserta lo nuevo). */
    @Transaction
    default void reemplazarTodos(List<EstadoMesaEntity> estados) {
        vaciar();
        insertarTodos(estados);
    }
}
