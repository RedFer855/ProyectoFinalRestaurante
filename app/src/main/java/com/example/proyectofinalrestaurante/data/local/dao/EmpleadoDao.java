package com.example.proyectofinalrestaurante.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.proyectofinalrestaurante.data.local.entity.EmpleadoEntity;

import java.util.List;

/**
 * DAO de empleados. Las lecturas son {@link LiveData} sobre Room: es la única fuente de
 * verdad de la UI y nunca falla, ni sin red.
 *
 * <p>No hay {@code porIdServidor}: acá la PK <b>es</b> el id del servidor, porque el alta de
 * un empleado exige conexión y toda fila local ya vino de la base. Ver {@code EmpleadoEntity}.</p>
 */
@Dao
public interface EmpleadoDao {

    @Query("SELECT * FROM empleados ORDER BY nombres COLLATE NOCASE ASC, "
            + "apellidos COLLATE NOCASE ASC")
    LiveData<List<EmpleadoEntity>> observarTodos();

    @Query("SELECT * FROM empleados WHERE id_empleado = :idEmpleado LIMIT 1")
    EmpleadoEntity porId(int idEmpleado);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertar(EmpleadoEntity empleado);

    @Update
    void actualizar(EmpleadoEntity empleado);

    @Delete
    void borrar(EmpleadoEntity empleado);

    @Query("SELECT COUNT(*) FROM empleados WHERE estado_sync != 'SINCRONIZADO'")
    int contarNoSincronizados();

    /** Empleados activos (Plan Fase 3c, §5): tarjeta "Empleados activos" del dashboard. */
    @Query("SELECT COUNT(*) FROM empleados WHERE activo = 1")
    LiveData<Integer> observarConteoActivos();
}
