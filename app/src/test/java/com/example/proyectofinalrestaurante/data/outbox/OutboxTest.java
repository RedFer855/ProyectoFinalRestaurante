package com.example.proyectofinalrestaurante.data.outbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.data.local.dao.OperacionPendienteDao;
import com.example.proyectofinalrestaurante.data.local.entity.OperacionPendienteEntity;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * El outbox (Plan Fase 2b, §4.4): encolar en FIFO, sacar por fila, marcar éxito,
 * registrar errores transitorios con contador y descartar permanentes. El DAO es un fake
 * en memoria con la misma semántica de {@code OperacionPendienteDao}, así el test es JUnit
 * puro sin Room.
 */
public class OutboxTest {

    private Outbox outbox;
    private FakeOperacionPendienteDao dao;

    @Before
    public void crearOutbox() {
        dao = new FakeOperacionPendienteDao();
        outbox = new Outbox(dao);
    }

    @Test
    public void encolar_camposCorrectosYDevuelveId() {
        long id = outbox.encolar(TipoOperacion.CREAR_PLATILLO, 7, "{\"nombre\":\"Baleada\"}", null);

        OperacionPendienteEntity encolada = dao.porId(id);
        assertEquals(TipoOperacion.CREAR_PLATILLO, encolada.getTipo());
        assertEquals(7, encolada.getIdLocal());
        assertEquals("{\"nombre\":\"Baleada\"}", encolada.getPayloadJson());
        assertEquals(0, encolada.getIntentos());
        assertNull(encolada.getRutaImagenLocal());
        assertTrue(encolada.getCreadoEn() > 0);
    }

    @Test
    public void primeras_devuelveFifoPorIdYRespetaLimite() {
        outbox.encolar(TipoOperacion.CREAR_PLATILLO, 1, "a", null);
        outbox.encolar(TipoOperacion.CREAR_PLATILLO, 2, "b", null);
        outbox.encolar(TipoOperacion.CREAR_PLATILLO, 3, "c", null);

        List<OperacionPendienteEntity> primeras = outbox.primeras(2);

        assertEquals(2, primeras.size());
        assertEquals("a", primeras.get(0).getPayloadJson());
        assertEquals("b", primeras.get(1).getPayloadJson());
    }

    @Test
    public void primeras_sinOperaciones_devuelveListaVacia() {
        assertTrue(outbox.primeras(10).isEmpty());
    }

    @Test
    public void deFila_devuelveSoloLasDeEsaFilaEnOrden() {
        outbox.encolar(TipoOperacion.CREAR_PLATILLO, 1, "a", null);
        outbox.encolar(TipoOperacion.CREAR_PLATILLO, 2, "b", null);
        outbox.encolar(TipoOperacion.ACTUALIZAR_PLATILLO, 1, "a2", null);

        List<OperacionPendienteEntity> deFila = outbox.deFila(1);

        assertEquals(2, deFila.size());
        assertEquals("a", deFila.get(0).getPayloadJson());
        assertEquals("a2", deFila.get(1).getPayloadJson());
    }

    @Test
    public void marcarExito_eliminaLaOperacionDeLaCola() {
        long id = outbox.encolar(TipoOperacion.CREAR_PLATILLO, 1, "a", null);
        assertEquals(1, outbox.contar());

        outbox.marcarExito(id);

        assertEquals(0, outbox.contar());
        assertNull(dao.porId(id));
    }

    @Test
    public void descartar_eliminaLaOperacionDeLaCola() {
        long id = outbox.encolar(TipoOperacion.CREAR_PLATILLO, 1, "a", null);

        outbox.descartar(id);

        assertEquals(0, outbox.contar());
    }

    @Test
    public void registrarErrorTransitorio_incrementaIntentosYGuardaElError() {
        long id = outbox.encolar(TipoOperacion.CREAR_PLATILLO, 1, "a", null);

        boolean quedanIntentos = outbox.registrarErrorTransitorio(id, "timeout", 3);

        assertTrue(quedanIntentos);
        OperacionPendienteEntity operacion = dao.porId(id);
        assertEquals(1, operacion.getIntentos());
        assertEquals("timeout", operacion.getUltimoError());
        assertEquals(1, outbox.contar());
    }

    @Test
    public void registrarErrorTransitorio_cuandoSeAgotanDevuelveFalse() {
        long id = outbox.encolar(TipoOperacion.CREAR_PLATILLO, 1, "a", null);

        outbox.registrarErrorTransitorio(id, "e1", 2);
        outbox.registrarErrorTransitorio(id, "e2", 2);
        boolean quedanIntentos = outbox.registrarErrorTransitorio(id, "e3", 2);

        assertFalse(quedanIntentos);
        assertEquals(3, dao.porId(id).getIntentos());
    }

    @Test
    public void registrarErrorTransitorio_operacionInexistente_noExplota() {
        assertTrue(outbox.registrarErrorTransitorio(999, "e", 3));
    }

    @Test
    public void actualizarImagenDelCrear_cambiaSoloLaOperacionDeCrear() {
        outbox.encolar(TipoOperacion.CREAR_PLATILLO, 1, "a", "foto-vieja.jpg");
        outbox.encolar(TipoOperacion.ACTUALIZAR_PLATILLO, 1, "a2", null);

        outbox.actualizarImagenDelCrear(1, "foto-nueva.jpg");

        List<OperacionPendienteEntity> deFila = outbox.deFila(1);
        assertEquals("foto-nueva.jpg", deFila.get(0).getRutaImagenLocal());
        assertNull(deFila.get(1).getRutaImagenLocal());
    }

    @Test
    public void actualizarImagenDelCrear_sinCrearPendiente_noTocaNada() {
        outbox.encolar(TipoOperacion.ACTUALIZAR_PLATILLO, 1, "a2", null);

        outbox.actualizarImagenDelCrear(1, "foto-nueva.jpg");

        assertNull(outbox.deFila(1).get(0).getRutaImagenLocal());
    }

    /** Fake en memoria con la semántica exacta del DAO: id autoincremental y FIFO por id. */
    private static final class FakeOperacionPendienteDao implements OperacionPendienteDao {

        private final List<OperacionPendienteEntity> filas = new ArrayList<>();
        private long siguienteId = 1;

        @Override
        public long encolar(OperacionPendienteEntity operacion) {
            operacion.setId(siguienteId++);
            filas.add(operacion);
            return operacion.getId();
        }

        @Override
        public List<OperacionPendienteEntity> primeras(int limite) {
            return new ArrayList<>(filas.subList(0, Math.min(limite, filas.size())));
        }

        @Override
        public OperacionPendienteEntity porId(long id) {
            for (OperacionPendienteEntity fila : filas) {
                if (fila.getId() == id) {
                    return fila;
                }
            }
            return null;
        }

        @Override
        public List<OperacionPendienteEntity> deFila(long idLocal) {
            List<OperacionPendienteEntity> resultado = new ArrayList<>();
            for (OperacionPendienteEntity fila : filas) {
                if (fila.getIdLocal() == idLocal) {
                    resultado.add(fila);
                }
            }
            return resultado;
        }

        @Override
        public void actualizar(OperacionPendienteEntity operacion) {
            for (int i = 0; i < filas.size(); i++) {
                if (filas.get(i).getId() == operacion.getId()) {
                    filas.set(i, operacion);
                    return;
                }
            }
        }

        @Override
        public void eliminar(long id) {
            for (int i = 0; i < filas.size(); i++) {
                if (filas.get(i).getId() == id) {
                    filas.remove(i);
                    return;
                }
            }
        }

        @Override
        public int contar() {
            return filas.size();
        }

        @Override
        public void borrar(OperacionPendienteEntity operacion) {
            filas.remove(operacion);
        }
    }
}
