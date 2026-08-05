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
 * DAO de clientes (Plan Fase 2d, E3). Las lecturas son {@link LiveData} sobre Room, igual que
 * {@link MesaDao}. A diferencia de mesas, sí hay {@code @Delete}: el servidor permite borrar
 * un cliente sin pedidos (ReglasCliente.puedeBorrarse).
 */
@Dao
public interface ClienteDao {

    @Query("SELECT * FROM clientes ORDER BY nombre COLLATE NOCASE ASC, apellido COLLATE NOCASE ASC")
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

    /** Clientes activos (Plan Fase 3c, §5): tarjeta "Clientes registrados" del dashboard. */
    @Query("SELECT COUNT(*) FROM clientes WHERE activo = 1")
    LiveData<Integer> observarConteoActivos();
}
