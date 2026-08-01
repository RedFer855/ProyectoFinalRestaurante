package com.example.proyectofinalrestaurante.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.proyectofinalrestaurante.data.local.entity.PlatilloEntity;

import java.util.List;

/**
 * DAO de platillos (Plan Fase 2b, E2). Las lecturas son {@link LiveData} sobre Room: es la
 * única fuente de verdad de la UI y nunca falla.
 */
@Dao
public interface PlatilloDao {

    @Query("SELECT * FROM platillos ORDER BY nombre COLLATE NOCASE ASC")
    LiveData<List<PlatilloEntity>> observarTodos();

    @Query("SELECT * FROM platillos WHERE id_local = :idLocal LIMIT 1")
    PlatilloEntity porIdLocal(long idLocal);

    @Query("SELECT * FROM platillos WHERE id_servidor = :idServidor LIMIT 1")
    PlatilloEntity porIdServidor(int idServidor);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertar(PlatilloEntity platillo);

    @Update
    void actualizar(PlatilloEntity platillo);

    @Delete
    void borrar(PlatilloEntity platillo);

    @Query("SELECT COUNT(*) FROM platillos WHERE estado_sync != 'SINCRONIZADO'")
    int contarNoSincronizados();
}
