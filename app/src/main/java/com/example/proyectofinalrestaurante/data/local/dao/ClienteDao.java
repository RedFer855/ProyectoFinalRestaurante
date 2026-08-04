package com.example.proyectofinalrestaurante.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.proyectofinalrestaurante.data.local.entity.ClienteEntity;

import java.util.List;

/**
 * DAO de clientes (Fase 2d). Las lecturas son {@link LiveData} sobre Room: es la
 * única fuente de verdad de la UI y nunca falla.
 */
@Dao
public interface ClienteDao {

    @Query("SELECT * FROM clientes ORDER BY nombre ASC, apellido ASC")
    LiveData<List<ClienteEntity>> observarTodos();

    @Query("SELECT * FROM clientes WHERE id_local = :idLocal LIMIT 1")
    ClienteEntity porIdLocal(long idLocal);

    @Query("SELECT * FROM clientes WHERE id_servidor = :idServidor LIMIT 1")
    ClienteEntity porIdServidor(int idServidor);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertar(ClienteEntity cliente);

    @Update
    void actualizar(ClienteEntity cliente);

    @Delete
    void borrar(ClienteEntity cliente);

    @Query("SELECT COUNT(*) FROM clientes WHERE estado_sync != 'SINCRONIZADO'")
    int contarNoSincronizadas();
}
