package com.example.proyectofinalrestaurante.data.local;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.example.proyectofinalrestaurante.data.local.dao.CategoriaDao;
import com.example.proyectofinalrestaurante.data.local.dao.EmpleadoDao;
import com.example.proyectofinalrestaurante.data.local.dao.OperacionPendienteDao;
import com.example.proyectofinalrestaurante.data.local.dao.PlatilloDao;
import com.example.proyectofinalrestaurante.data.local.dao.SincronizacionDao;
import com.example.proyectofinalrestaurante.data.local.entity.CategoriaEntity;
import com.example.proyectofinalrestaurante.data.local.entity.EmpleadoEntity;
import com.example.proyectofinalrestaurante.data.local.entity.OperacionPendienteEntity;
import com.example.proyectofinalrestaurante.data.local.entity.PlatilloEntity;
import com.example.proyectofinalrestaurante.data.local.entity.SincronizacionEntity;

/**
 * Base local offline-first (Plan Fase 2b, E2). Nació con el módulo Menú; la v2 sumó
 * Empleados.
 *
 * <p><b>Nunca</b> usar {@code fallbackToDestructiveMigration()}: borra los datos del usuario
 * sin avisar y está en la Lista Negra de APIs Android. Cada cambio de esquema lleva su
 * {@code Migration} escrita en {@link Migraciones} y su test con
 * {@code MigrationTestHelper}. El esquema se exporta a {@code app/schemas/} (versionado)
 * justamente para poder probar eso.</p>
 *
 * <p>{@code operaciones_pendientes} es una sola tabla para los dos módulos, particionada por
 * su columna {@code modulo}. La alternativa —una cola por módulo— habría necesitado dos
 * workers, y la regla 3 de {@code Offline-First con Room y Outbox} es explícita: un
 * {@code SyncWorker} <b>único</b>, para que nunca haya dos drenando en paralelo.</p>
 */
@Database(entities = {
        PlatilloEntity.class,
        CategoriaEntity.class,
        EmpleadoEntity.class,
        OperacionPendienteEntity.class,
        SincronizacionEntity.class
}, version = 2, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    public abstract PlatilloDao platilloDao();

    public abstract CategoriaDao categoriaDao();

    public abstract EmpleadoDao empleadoDao();

    public abstract OperacionPendienteDao operacionPendienteDao();

    public abstract SincronizacionDao sincronizacionDao();
}
