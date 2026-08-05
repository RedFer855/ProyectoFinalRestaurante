package com.example.proyectofinalrestaurante.data.local;

import static org.junit.Assert.assertEquals;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;

import com.example.proyectofinalrestaurante.data.local.dao.EstadoPedidoDao;
import com.example.proyectofinalrestaurante.data.local.entity.EstadoPedidoEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

/**
 * El DAO del catálogo {@code estado_pedido} (Plan Fase 3, E3) contra una base en memoria
 * real: se reemplaza completo con cada sincronización y ordena por {@code orden}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class EstadoPedidoDaoTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private AppDatabase base;
    private EstadoPedidoDao dao;

    @Before
    public void crearBaseEnMemoria() {
        base = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = base.estadoPedidoDao();
    }

    @After
    public void cerrarBase() {
        base.close();
    }

    @Test
    public void reemplazarTodos_borraLoViejoEInsertaElNuevo() {
        dao.reemplazarTodos(List.of(estado(1, "Pendiente", 1), estado(2, "En preparación", 2)));

        List<EstadoPedidoEntity> actuales = dao.todosSincrono();

        assertEquals(2, actuales.size());
        assertEquals("Pendiente", actuales.get(0).getDescripcion());
    }

    @Test
    public void reemplazarTodos_conEstadoQueYaNoExiste_loDescarta() {
        dao.reemplazarTodos(List.of(estado(1, "Pendiente", 1)));
        dao.reemplazarTodos(List.of(estado(1, "Pendiente", 1), estado(2, "En preparación", 2)));

        List<EstadoPedidoEntity> actuales = dao.todosSincrono();

        assertEquals(2, actuales.size());
    }

    @Test
    public void todosSincrono_ordenaPorOrden() {
        dao.reemplazarTodos(List.of(estado(3, "Listo", 3), estado(1, "Pendiente", 1)));

        List<EstadoPedidoEntity> actuales = dao.todosSincrono();

        assertEquals(1, actuales.get(0).getIdEstadoPedido());
        assertEquals(3, actuales.get(1).getIdEstadoPedido());
    }

    private static EstadoPedidoEntity estado(int id, String descripcion, int orden) {
        EstadoPedidoEntity e = new EstadoPedidoEntity();
        e.setIdEstadoPedido(id);
        e.setDescripcion(descripcion);
        e.setOrden(orden);
        return e;
    }
}