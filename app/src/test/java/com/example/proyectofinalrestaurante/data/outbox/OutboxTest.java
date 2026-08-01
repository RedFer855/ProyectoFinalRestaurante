package com.example.proyectofinalrestaurante.data.outbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.proyectofinalrestaurante.data.FakeOperacionPendienteDao;
import com.example.proyectofinalrestaurante.data.local.entity.OperacionPendienteEntity;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

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
        outbox = new Outbox(dao, TipoOperacion.Modulo.MENU);
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

    // ------------------------------------------------------------------ partición por módulo

    @Test
    public void encolar_marcaElModuloDeLaInstancia() {
        Outbox deEmpleados = new Outbox(dao, TipoOperacion.Modulo.EMPLEADOS);

        long id = deEmpleados.encolar(TipoOperacion.CAMBIAR_ROL_EMPLEADO, 3, null, null);

        assertEquals(TipoOperacion.Modulo.EMPLEADOS, dao.porId(id).getModulo());
    }

    @Test
    public void primeras_noDevuelveOperacionesDeOtroModulo() {
        Outbox deEmpleados = new Outbox(dao, TipoOperacion.Modulo.EMPLEADOS);
        outbox.encolar(TipoOperacion.CREAR_PLATILLO, 1, null, null);
        deEmpleados.encolar(TipoOperacion.CAMBIAR_ROL_EMPLEADO, 1, null, null);

        // Si el Menú viera esta operación, su `default` la descartaría por "tipo
        // desconocido" y el cambio de rol se perdería en silencio.
        List<OperacionPendienteEntity> delMenu = outbox.primeras(10);
        assertEquals(1, delMenu.size());
        assertEquals(TipoOperacion.CREAR_PLATILLO, delMenu.get(0).getTipo());

        List<OperacionPendienteEntity> deEmpl = deEmpleados.primeras(10);
        assertEquals(1, deEmpl.size());
        assertEquals(TipoOperacion.CAMBIAR_ROL_EMPLEADO, deEmpl.get(0).getTipo());
    }

    @Test
    public void deFila_noConfundeElPlatillo3ConElEmpleado3() {
        Outbox deEmpleados = new Outbox(dao, TipoOperacion.Modulo.EMPLEADOS);
        outbox.encolar(TipoOperacion.ACTUALIZAR_PLATILLO, 3, null, null);
        deEmpleados.encolar(TipoOperacion.ACTUALIZAR_EMPLEADO, 3, null, null);

        // `id_local` es la PK de la tabla local de cada módulo: el 3 existe en las dos.
        assertEquals(1, outbox.deFila(3).size());
        assertEquals(TipoOperacion.ACTUALIZAR_PLATILLO, outbox.deFila(3).get(0).getTipo());
        assertEquals(1, deEmpleados.deFila(3).size());
        assertEquals(TipoOperacion.ACTUALIZAR_EMPLEADO, deEmpleados.deFila(3).get(0).getTipo());
    }

    @Test
    public void contar_soloCuentaLoDelPropioModulo() {
        Outbox deEmpleados = new Outbox(dao, TipoOperacion.Modulo.EMPLEADOS);
        outbox.encolar(TipoOperacion.CREAR_PLATILLO, 1, null, null);
        outbox.encolar(TipoOperacion.CREAR_PLATILLO, 2, null, null);
        deEmpleados.encolar(TipoOperacion.CAMBIAR_ESTADO_EMPLEADO, 1, null, null);

        assertEquals(2, outbox.contar());
        assertEquals(1, deEmpleados.contar());
        assertEquals(3, dao.contarTodas());
    }
}
