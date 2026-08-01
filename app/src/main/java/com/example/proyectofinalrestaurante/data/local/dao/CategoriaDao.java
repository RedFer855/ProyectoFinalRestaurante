package com.example.proyectofinalrestaurante.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.proyectofinalrestaurante.data.local.entity.CategoriaEntity;

import java.util.List;

/**
 * DAO de categorías (Plan Fase 2b, E2). Igual que {@link PlatilloDao}: lecturas como
 * {@link LiveData}, escrituras por fila local.
 */
@Dao
public interface CategoriaDao {

    @Query("SELECT * FROM categorias ORDER BY descripcion COLLATE NOCASE ASC")
    LiveData<List<CategoriaEntity>> observarTodas();

    @Query("SELECT * FROM categorias WHERE id_local = :idLocal LIMIT 1")
    CategoriaEntity porIdLocal(long idLocal);

    @Query("SELECT * FROM categorias WHERE id_servidor = :idServidor LIMIT 1")
    CategoriaEntity porIdServidor(int idServidor);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertar(CategoriaEntity categoria);

    @Update
    void actualizar(CategoriaEntity categoria);

    @Delete
    void borrar(CategoriaEntity categoria);

    @Query("SELECT COUNT(*) FROM categorias WHERE estado_sync != 'SINCRONIZADO'")
    int contarNoSincronizadas();
}
