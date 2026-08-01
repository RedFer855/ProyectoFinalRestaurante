package com.example.proyectofinalrestaurante.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;

import com.example.proyectofinalrestaurante.data.local.dao.SincronizacionDao;
import com.example.proyectofinalrestaurante.data.local.entity.SincronizacionEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * El DAO de la marca de agua del sync delta (Plan Fase 2b, §4.3) contra una base en memoria
 * real. La marca sale de los datos recibidos, nunca del reloj del teléfono, y el upsert por
 * {@code tabla} es lo que permite guardarla tras cada bajada.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SincronizacionDaoTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppDatabase base;
    private SincronizacionDao dao;

    @Before
    public void crearBaseEnMemoria() {
        base = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = base.sincronizacionDao();
    }

    @After
    public void cerrarBase() {
        base.close();
    }

    @Test
    public void guardarYPorTabla_roundTripCompleto() {
        dao.guardar(unaMarca("platillos", "2026-08-01T10:00:00Z"));

        SincronizacionEntity recargada = dao.porTabla("platillos");

        assertNotNull(recargada);
        assertEquals("2026-08-01T10:00:00Z", recargada.getMarcaAgua());
    }

    @Test
    public void porTabla_sinMarca_devuelveNull() {
        assertNull(dao.porTabla("categorias"));
    }

    @Test
    public void guardarConMismaTabla_reemplazaLaMarcaAnterior() {
        dao.guardar(unaMarca("platillos", "2026-08-01T10:00:00Z"));
        dao.guardar(unaMarca("platillos", "2026-08-01T11:30:00Z"));

        assertEquals("2026-08-01T11:30:00Z", dao.porTabla("platillos").getMarcaAgua());
    }

    @Test
    public void guardar_persisteElDiagnosticoDelUltimoIntento() {
        SincronizacionEntity marca = unaMarca("platillos", "2026-08-01T10:00:00Z");
        marca.setUltimoIntento(123456789L);
        marca.setUltimoError("timeout");
        dao.guardar(marca);

        SincronizacionEntity recargada = dao.porTabla("platillos");

        assertEquals(123456789L, recargada.getUltimoIntento());
        assertEquals("timeout", recargada.getUltimoError());
    }

    private static SincronizacionEntity unaMarca(String tabla, String marcaAgua) {
        SincronizacionEntity marca = new SincronizacionEntity();
        marca.setTabla(tabla);
        marca.setMarcaAgua(marcaAgua);
        return marca;
    }
}
