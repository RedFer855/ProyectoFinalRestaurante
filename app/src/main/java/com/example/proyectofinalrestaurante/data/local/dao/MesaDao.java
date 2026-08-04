package com.example.proyectofinalrestaurante.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.proyectofinalrestaurante.data.local.entity.MesaEntity;

import java.util.List;

/**
 * DAO de mesas (Fase 2c). Las lecturas son {@link LiveData} sobre Room: es la
 * única fuente de verdad de la UI y nunca falla.
 */
@Dao
public interface MesaDao {

    @Query("SELECT * FROM mesas ORDER BY numero_mesa ASC")
    LiveData<List<MesaEntity>> observarTodas();

    @Query("SELECT * FROM mesas WHERE id_local = :idLocal LIMIT 1")
    MesaEntity porIdLocal(long idLocal);

    @Query("SELECT * FROM mesas WHERE id_servidor = :idServidor LIMIT 1")
    MesaEntity porIdServidor(int idServidor);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertar(MesaEntity mesa);

    @Update
    void actualizar(MesaEntity mesa);

    @Delete
    void borrar(MesaEntity mesa);

    @Query("SELECT COUNT(*) FROM mesas WHERE estado_sync != 'SINCRONIZADO'")
    int contarNoSincronizadas();
}
