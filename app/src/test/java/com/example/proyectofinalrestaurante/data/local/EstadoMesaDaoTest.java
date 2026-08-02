package com.example.proyectofinalrestaurante.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.room.Room;

import com.example.proyectofinalrestaurante.data.local.dao.EstadoMesaDao;
import com.example.proyectofinalrestaurante.data.local.entity.EstadoMesaEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * El DAO del catálogo {@code estado_mesa} (Plan Fase 2c, E3) contra una base en memoria.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class EstadoMesaDaoTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppDatabase base;
    private EstadoMesaDao dao;

    @Before
    public void crearBaseEnMemoria() {
        base = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = base.estadoMesaDao();
    }

    @After
    public void cerrarBase() {
        base.close();
    }

    @Test
    public void insertarTodos_yObservarTodos_emiteEnOrdenDelCatalogo() {
        dao.insertarTodos(Arrays.asList(unEstado(1, "Libre", 1), unEstado(2, "Ocupada", 2)));

        AtomicReference<List<EstadoMesaEntity>> ultimo = new AtomicReference<>();
        LiveData<List<EstadoMesaEntity>> liveData = dao.observarTodos();
        liveData.observeForever(ultimo::set);

        List<EstadoMesaEntity> estados = ultimo.get();
        assertNotNull(estados);
        assertEquals(2, estados.size());
        assertEquals(1, estados.get(0).getIdEstadoMesa());
        assertEquals(2, estados.get(1).getIdEstadoMesa());
    }

    @Test
    public void reemplazarTodos_borraElCatalogoViejoEInsertaElNuevo() {
        dao.reemplazarTodos(Arrays.asList(unEstado(1, "Libre", 1), unEstado(2, "Ocupada", 2)));
        dao.reemplazarTodos(Collections.singletonList(unEstado(3, "Reservada", 3)));

        AtomicReference<List<EstadoMesaEntity>> ultimo = new AtomicReference<>();
        LiveData<List<EstadoMesaEntity>> liveData = dao.observarTodos();
        liveData.observeForever(ultimo::set);

        List<EstadoMesaEntity> estados = ultimo.get();
        assertNotNull(estados);
        assertEquals(1, estados.size());
        assertEquals("Reservada", estados.get(0).getDescripcion());
    }

    private static EstadoMesaEntity unEstado(int idEstadoMesa, String descripcion, int orden) {
        EstadoMesaEntity estado = new EstadoMesaEntity();
        estado.setIdEstadoMesa(idEstadoMesa);
        estado.setDescripcion(descripcion);
        estado.setOrden(orden);
        return estado;
    }
}
