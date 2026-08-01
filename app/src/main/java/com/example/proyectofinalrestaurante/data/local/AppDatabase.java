package com.example.proyectofinalrestaurante.data.local;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import com.example.proyectofinalrestaurante.data.local.dao.CategoriaDao;
import com.example.proyectofinalrestaurante.data.local.dao.OperacionPendienteDao;
import com.example.proyectofinalrestaurante.data.local.dao.PlatilloDao;
import com.example.proyectofinalrestaurante.data.local.dao.SincronizacionDao;
import com.example.proyectofinalrestaurante.data.local.entity.CategoriaEntity;
import com.example.proyectofinalrestaurante.data.local.entity.OperacionPendienteEntity;
import com.example.proyectofinalrestaurante.data.local.entity.PlatilloEntity;
import com.example.proyectofinalrestaurante.data.local.entity.SincronizacionEntity;

/**
 * Base local offline-first del módulo Menú (Plan Fase 2b, E2).
 *
 * <p>Versión 1, sin migraciones por ahora. <b>Nunca</b> usar
 * {@code fallbackToDestructiveMigration()}: borra los datos del usuario sin avisar y está en
 * la Lista Negra de APIs Android. Cada cambio de esquema lleva su {@code Migration} escrita
 * y su test con {@code MigrationTestHelper}. El esquema se exporta a {@code app/schemas/}
 * (versionado) justamente para poder probar eso.</p>
 */
@Database(entities = {
        PlatilloEntity.class,
        CategoriaEntity.class,
        OperacionPendienteEntity.class,
        SincronizacionEntity.class
}, version = 1, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {

    public abstract PlatilloDao platilloDao();

    public abstract CategoriaDao categoriaDao();

    public abstract OperacionPendienteDao operacionPendienteDao();

    public abstract SincronizacionDao sincronizacionDao();
}
