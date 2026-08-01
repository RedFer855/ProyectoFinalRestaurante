package com.example.proyectofinalrestaurante.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.room.Room;

import com.example.proyectofinalrestaurante.data.local.dao.CategoriaDao;
import com.example.proyectofinalrestaurante.data.local.entity.CategoriaEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * El DAO de categorías (Plan Fase 2b, E2) contra una base en memoria real. Mismo esquema de
 * pruebas que {@link PlatilloDaoTest}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class CategoriaDaoTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppDatabase base;
    private CategoriaDao dao;

    @Before
    public void crearBaseEnMemoria() {
        base = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = base.categoriaDao();
    }

    @After
    public void cerrarBase() {
        base.close();
    }

    @Test
    public void insertarYPorIdLocal_roundTripCompleto() {
        long id = dao.insertar(unaCategoria("Desayunos", "SINCRONIZADO"));

        CategoriaEntity recargada = dao.porIdLocal(id);

        assertNotNull(recargada);
        assertEquals("Desayunos", recargada.getDescripcion());
        assertEquals("SINCRONIZADO", recargada.getEstadoSync());
    }

    @Test
    public void porIdServidor_buscaPorLaFilaDelServidor() {
        CategoriaEntity categoria = unaCategoria("Desayunos", "SINCRONIZADO");
        categoria.setIdServidor(10);
        long id = dao.insertar(categoria);

        CategoriaEntity recargada = dao.porIdServidor(10);

        assertNotNull(recargada);
        assertEquals(id, recargada.getIdLocal());
    }

    @Test
    public void insertarConMismoIdServidor_reemplazaEnVezDeDuplicar() {
        dao.insertar(conIdServidor(unaCategoria("Desayunos", "SINCRONIZADO"), 10));
        dao.insertar(conIdServidor(unaCategoria("Cenas", "SINCRONIZADO"), 10));

        CategoriaEntity recargada = dao.porIdServidor(10);

        assertNotNull(recargada);
        assertEquals("Cenas", recargada.getDescripcion());
    }

    @Test
    public void actualizar_persisteLosCambios() {
        long id = dao.insertar(unaCategoria("Desayunos", "SINCRONIZADO"));
        CategoriaEntity categoria = dao.porIdLocal(id);
        categoria.setDescripcion("Desayunos típicos");
        categoria.setEstadoSync("PENDIENTE");

        dao.actualizar(categoria);

        assertEquals("Desayunos típicos", dao.porIdLocal(id).getDescripcion());
        assertEquals("PENDIENTE", dao.porIdLocal(id).getEstadoSync());
    }

    @Test
    public void borrar_eliminaLaFila() {
        long id = dao.insertar(unaCategoria("Desayunos", "SINCRONIZADO"));

        dao.borrar(dao.porIdLocal(id));

        assertNull(dao.porIdLocal(id));
    }

    @Test
    public void contarNoSincronizadas_cuentaSoloLasQueNoEstanSincronizadas() {
        dao.insertar(unaCategoria("Sincronizada", "SINCRONIZADO"));
        dao.insertar(unaCategoria("Pendiente", "PENDIENTE"));
        dao.insertar(unaCategoria("Con error", "ERROR"));

        assertEquals(2, dao.contarNoSincronizadas());
    }

    @Test
    public void observarTodas_emiteEnOrdenAlfabetico() {
        AtomicReference<List<CategoriaEntity>> ultimo = new AtomicReference<>();
        LiveData<List<CategoriaEntity>> liveData = dao.observarTodas();
        liveData.observeForever(ultimo::set);

        dao.insertar(unaCategoria("Z", "SINCRONIZADO"));
        dao.insertar(unaCategoria("A", "SINCRONIZADO"));

        List<CategoriaEntity> categorias = ultimo.get();
        assertNotNull(categorias);
        assertEquals(2, categorias.size());
        assertEquals("A", categorias.get(0).getDescripcion());
        assertEquals("Z", categorias.get(1).getDescripcion());
    }

    private static CategoriaEntity unaCategoria(String descripcion, String estadoSync) {
        CategoriaEntity categoria = new CategoriaEntity();
        categoria.setDescripcion(descripcion);
        categoria.setEstadoSync(estadoSync);
        categoria.setActivo(true);
        return categoria;
    }

    private static CategoriaEntity conIdServidor(CategoriaEntity categoria, Integer idServidor) {
        categoria.setIdServidor(idServidor);
        return categoria;
    }
}
