package com.example.proyectofinalrestaurante.data.outbox;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.data.local.dao.OperacionPendienteDao;
import com.example.proyectofinalrestaurante.data.local.entity.OperacionPendienteEntity;

import java.util.List;

/**
 * El outbox del módulo Menú (Plan Fase 2b, §4.4 y E4).
 *
 * <p>Encapsula la cola de operaciones pendientes: encolar, sacar en orden FIFO, marcar una
 * operación como exitosa (se elimina) o registrarle un error transitorio (queda para el
 * siguiente reintento, con contador). El drenador decide qué es permanente o transitorio
 * con {@link ClasificadorDeError} y cuándo abandonar.</p>
 */
public final class Outbox {

    private final OperacionPendienteDao dao;

    public Outbox(OperacionPendienteDao dao) {
        this.dao = dao;
    }

    /** Encola una operación y devuelve su id (el orden FIFO del drenado). */
    public long encolar(String tipo, long idLocal, String payloadJson,
                        @Nullable String rutaImagenLocal) {
        OperacionPendienteEntity operacion = new OperacionPendienteEntity();
        operacion.setTipo(tipo);
        operacion.setIdLocal(idLocal);
        operacion.setPayloadJson(payloadJson);
        operacion.setRutaImagenLocal(rutaImagenLocal);
        operacion.setIntentos(0);
        operacion.setCreadoEn(System.currentTimeMillis());
        return dao.encolar(operacion);
    }

    /** Las primeras {@code limite} operaciones por orden de llegada (FIFO estricto). */
    public List<OperacionPendienteEntity> primeras(int limite) {
        return dao.primeras(limite);
    }

    /** Las operaciones pendientes de una fila local concreta, en orden. */
    public List<OperacionPendienteEntity> deFila(long idLocal) {
        return dao.deFila(idLocal);
    }

    /** La operación se subió bien: sale de la cola. */
    public void marcarExito(long id) {
        dao.eliminar(id);
    }

    /**
     * Actualiza la foto que va a subir el {@code CREAR} pendiente de una fila (Plan Fase 2b,
     * §5.5): si el platillo todavía no se subió y se edita para agregar o quitar la foto, la
     * ruta viaja en la operación ya encolada — encolar un {@code CREAR} nuevo duplicaría el
     * {@code POST} cuando la cola drene.
     */
    public void actualizarImagenDelCrear(long idLocal, @Nullable String rutaImagenLocal) {
        for (OperacionPendienteEntity operacion : dao.deFila(idLocal)) {
            if (TipoOperacion.CREAR_PLATILLO.equals(operacion.getTipo())) {
                operacion.setRutaImagenLocal(rutaImagenLocal);
                dao.actualizar(operacion);
            }
        }
    }

    /**
     * La operación falló pero el error es transitorio: queda en la cola con el contador
     * incrementado y el último error guardado.
     *
     * @return {@code true} si todavía hay intentos, {@code false} si se agotaron.
     */
    public boolean registrarErrorTransitorio(long id, String mensaje, int maxIntentos) {
        OperacionPendienteEntity operacion = dao.porId(id);
        if (operacion == null) {
            return true;
        }
        operacion.setIntentos(operacion.getIntentos() + 1);
        operacion.setUltimoError(mensaje);
        dao.actualizar(operacion);
        return operacion.getIntentos() <= maxIntentos;
    }

    /**
     * Error permanente: la operación se descarta para que no bloquee a las que vienen
     * detrás. Quien llame a esto decide qué hacer con la fila local (marcarla {@code ERROR}).
     */
    public void descartar(long id) {
        dao.eliminar(id);
    }

    public int contar() {
        return dao.contar();
    }
}
