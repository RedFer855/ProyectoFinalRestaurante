package com.example.proyectofinalrestaurante.data.local.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.proyectofinalrestaurante.data.local.entity.OperacionPendienteEntity;

import java.util.List;

/**
 * DAO del outbox (Plan Fase 2b, E4). El orden de drenado es FIFO estricto por {@code id},
 * así que las lecturas ordenadas no son un adorno: una operación que bloquea al resto
 * mantiene la cola coherente.
 */
@Dao
public interface OperacionPendienteDao {

    @Insert
    long encolar(OperacionPendienteEntity operacion);

    @Query("SELECT * FROM operaciones_pendientes ORDER BY id ASC LIMIT :limite")
    List<OperacionPendienteEntity> primeras(int limite);

    @Query("SELECT * FROM operaciones_pendientes WHERE id = :id LIMIT 1")
    OperacionPendienteEntity porId(long id);

    @Query("SELECT * FROM operaciones_pendientes WHERE id_local = :idLocal ORDER BY id ASC")
    List<OperacionPendienteEntity> deFila(long idLocal);

    @Update
    void actualizar(OperacionPendienteEntity operacion);

    @Query("DELETE FROM operaciones_pendientes WHERE id = :id")
    void eliminar(long id);

    @Query("SELECT COUNT(*) FROM operaciones_pendientes")
    int contar();

    @Delete
    void borrar(OperacionPendienteEntity operacion);
}
