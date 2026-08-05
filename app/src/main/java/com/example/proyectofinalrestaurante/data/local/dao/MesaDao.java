package com.example.proyectofinalrestaurante.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.proyectofinalrestaurante.data.local.entity.MesaEntity;

import java.util.List;

/**
 * DAO de mesas (Plan Fase 2c, E3). Las lecturas son {@link LiveData} sobre Room, igual que
 * {@link PlatilloDao} y {@link CategoriaDao}.
 *
 * <p><b>No hay {@code @Delete} a propósito</b>: las mesas no se borran, se dan de baja
 * ({@code ReglasMesa.lasMesasNoSeBorran()}, trigger {@code trg_mesa_no_borrar} del servidor).
 * El DAO expone la baja lógica solo a través de {@link #actualizar}, que pisa
 * {@code id_estado}/{@code activo}.</p>
 */
@Dao
public interface MesaDao {

    @Query("SELECT * FROM mesas ORDER BY numero_mesa COLLATE NOCASE ASC")
    LiveData<List<MesaEntity>> observarTodas();

    @Query("SELECT * FROM mesas WHERE id_local = :idLocal LIMIT 1")
    MesaEntity porIdLocal(long idLocal);

    @Query("SELECT * FROM mesas WHERE id_servidor = :idServidor LIMIT 1")
    MesaEntity porIdServidor(int idServidor);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertar(MesaEntity mesa);

    @Update
    void actualizar(MesaEntity mesa);

    @Query("SELECT COUNT(*) FROM mesas WHERE estado_sync != 'SINCRONIZADO'")
    int contarNoSincronizadas();

    /** Mesas activas en un estado operativo del catálogo (Plan Fase 3c, §5): "Mesas ocupadas". */
    @Query("SELECT COUNT(*) FROM mesas WHERE activo = 1 AND id_estado_mesa = :idEstadoMesa")
    LiveData<Integer> observarConteoPorEstadoOperativo(int idEstadoMesa);

    /** Total de mesas activas (Plan Fase 3c, §5): el "de Y" de "Mesas ocupadas (X de Y)". */
    @Query("SELECT COUNT(*) FROM mesas WHERE activo = 1")
    LiveData<Integer> observarConteoActivas();
}
