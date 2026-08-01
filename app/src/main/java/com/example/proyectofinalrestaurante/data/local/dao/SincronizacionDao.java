package com.example.proyectofinalrestaurante.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.proyectofinalrestaurante.data.local.entity.SincronizacionEntity;

/**
 * DAO de la marca de agua del sync delta, por tabla (Plan Fase 2b, §4.3).
 */
@Dao
public interface SincronizacionDao {

    @Query("SELECT * FROM sincronizacion WHERE tabla = :tabla LIMIT 1")
    SincronizacionEntity porTabla(String tabla);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void guardar(SincronizacionEntity marca);
}
