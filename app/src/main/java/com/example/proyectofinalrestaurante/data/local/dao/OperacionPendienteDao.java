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
 *
 * <p>Desde la v2 del esquema la tabla es compartida entre módulos, así que <b>toda</b>
 * consulta que devuelva operaciones filtra por {@code modulo}. El filtro de {@code deFila}
 * es especialmente importante: {@code id_local} es la PK de la tabla local de cada módulo,
 * y el platillo 3 y el empleado 3 comparten número.</p>
 */
@Dao
public interface OperacionPendienteDao {

    @Insert
    long encolar(OperacionPendienteEntity operacion);

    @Query("SELECT * FROM operaciones_pendientes WHERE modulo = :modulo "
            + "ORDER BY id ASC LIMIT :limite")
    List<OperacionPendienteEntity> primeras(String modulo, int limite);

    @Query("SELECT * FROM operaciones_pendientes WHERE id = :id LIMIT 1")
    OperacionPendienteEntity porId(long id);

    @Query("SELECT * FROM operaciones_pendientes WHERE modulo = :modulo "
            + "AND id_local = :idLocal ORDER BY id ASC")
    List<OperacionPendienteEntity> deFila(String modulo, long idLocal);

    @Update
    void actualizar(OperacionPendienteEntity operacion);

    @Query("DELETE FROM operaciones_pendientes WHERE id = :id")
    void eliminar(long id);

    @Query("SELECT COUNT(*) FROM operaciones_pendientes WHERE modulo = :modulo")
    int contar(String modulo);

    /** Total de la cola, sin importar el módulo. Para diagnóstico, no para drenar. */
    @Query("SELECT COUNT(*) FROM operaciones_pendientes")
    int contarTodas();

    @Delete
    void borrar(OperacionPendienteEntity operacion);
}
