package com.example.proyectofinalrestaurante.data;

import com.example.proyectofinalrestaurante.data.local.dao.OperacionPendienteDao;
import com.example.proyectofinalrestaurante.data.local.entity.OperacionPendienteEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Fake en memoria del outbox con la semántica exacta del DAO real: id autoincremental, FIFO
 * por id y <b>filtrado por módulo</b>.
 *
 * <p>Estaba duplicado en {@code OutboxTest} y {@code SincronizadorMenuTest}. Se extrajo al
 * agregar la partición por módulo: si el filtro se implementa distinto en cada copia, los
 * tests pueden pasar mientras el bug real —un módulo comiéndose las operaciones del otro—
 * queda sin cubrir.</p>
 */
public final class FakeOperacionPendienteDao implements OperacionPendienteDao {

    private final List<OperacionPendienteEntity> filas = new ArrayList<>();
    private long siguienteId = 1;

    /** Acceso directo a las filas, para armar escenarios en los tests. */
    public List<OperacionPendienteEntity> filas() {
        return filas;
    }

    @Override
    public long encolar(OperacionPendienteEntity operacion) {
        operacion.setId(siguienteId++);
        filas.add(operacion);
        return operacion.getId();
    }

    @Override
    public List<OperacionPendienteEntity> primeras(String modulo, int limite) {
        List<OperacionPendienteEntity> resultado = new ArrayList<>();
        for (OperacionPendienteEntity fila : filas) {
            if (modulo.equals(fila.getModulo())) {
                resultado.add(fila);
                if (resultado.size() == limite) {
                    break;
                }
            }
        }
        return resultado;
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
    public List<OperacionPendienteEntity> deFila(String modulo, long idLocal) {
        List<OperacionPendienteEntity> resultado = new ArrayList<>();
        for (OperacionPendienteEntity fila : filas) {
            if (modulo.equals(fila.getModulo()) && fila.getIdLocal() == idLocal) {
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
    public int contar(String modulo) {
        int cuenta = 0;
        for (OperacionPendienteEntity fila : filas) {
            if (modulo.equals(fila.getModulo())) {
                cuenta++;
            }
        }
        return cuenta;
    }

    @Override
    public int contarTodas() {
        return filas.size();
    }

    @Override
    public void borrar(OperacionPendienteEntity operacion) {
        filas.remove(operacion);
    }
}
