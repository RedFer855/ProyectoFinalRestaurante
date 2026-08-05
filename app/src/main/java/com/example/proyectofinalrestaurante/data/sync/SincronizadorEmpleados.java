package com.example.proyectofinalrestaurante.data.sync;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.data.local.dao.EmpleadoDao;
import com.example.proyectofinalrestaurante.data.local.dao.SincronizacionDao;
import com.example.proyectofinalrestaurante.data.local.entity.EmpleadoEntity;
import com.example.proyectofinalrestaurante.data.local.entity.OperacionPendienteEntity;
import com.example.proyectofinalrestaurante.data.local.entity.SincronizacionEntity;
import com.example.proyectofinalrestaurante.data.local.mapper.EmpleadoMapper;
import com.example.proyectofinalrestaurante.data.outbox.ClasificadorDeError;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.remote.dto.EmpleadoDto;
import com.example.proyectofinalrestaurante.data.repository.EmpleadoRemoto;
import com.example.proyectofinalrestaurante.data.repository.ResultadoRed;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;

import java.util.List;

/**
 * El sincronizador del módulo Empleados. Mismo contrato que {@link SincronizadorMenu}:
 * primero <b>drena su parte del outbox</b> (FIFO) y después <b>baja el delta</b> con la marca
 * de agua. Si el drenado se corta por un error transitorio, no se baja el delta: el reintento
 * lo va a repetir todo.
 *
 * <p>Es más simple que el del Menú por dos razones, y las dos vienen de que <b>el alta exige
 * conexión</b>:</p>
 *
 * <ul>
 *   <li>No hay {@code CREAR} en la cola, así que no existe el caso "fila local sin id de
 *       servidor" ni el plegado de ediciones sobre un CREAR pendiente.</li>
 *   <li>La PK local <b>es</b> el {@code id_empleado} del servidor, así que no hay que
 *       resolver ninguna correspondencia al aplicar el delta.</li>
 * </ul>
 *
 * <p>No conoce la red ni la UI: usa {@link EmpleadoRemoto} y devuelve un {@link ResultadoSync}
 * que el {@link SyncWorker} traduce a {@code retry()}/{@code success()}.</p>
 *
 * <p>El delta pagina por {@code offset} con la marca fija durante toda la pasada (P-029,
 * 2026-08-04) — mismo bucle que {@link SincronizadorMenu#bajarPlatillos()}.</p>
 */
public final class SincronizadorEmpleados implements Sincronizador {

    static final int MAX_INTENTOS = 3;
    static final int LOTE = 50;
    static final String TABLA = "empleados";

    /** Tope de páginas por pasada del delta; ver el Javadoc de {@link SincronizadorMenu}. */
    static final int MAX_PAGINAS = 200;

    /** Mensaje con que el conflicto LWW avisa que un cambio local se perdió. */
    static final String CAMBIO_LOCAL_PERDIDO =
            "Un cambio de empleado se perdió: el servidor tenía una versión más reciente.";

    private final EmpleadoRemoto remoto;
    private final Outbox outbox;
    private final EmpleadoDao empleadoDao;
    private final SincronizacionDao sincronizacionDao;
    private final EjecutorDeTransaccion transacciones;

    public SincronizadorEmpleados(EmpleadoRemoto remoto, Outbox outbox,
                                  EmpleadoDao empleadoDao,
                                  SincronizacionDao sincronizacionDao,
                                  EjecutorDeTransaccion transacciones) {
        this.remoto = remoto;
        this.outbox = outbox;
        this.empleadoDao = empleadoDao;
        this.sincronizacionDao = sincronizacionDao;
        this.transacciones = transacciones;
    }

    @Override
    public String modulo() {
        return TipoOperacion.Modulo.EMPLEADOS;
    }

    @Override
    public ResultadoSync sincronizar() {
        ResultadoSync drenado = drenarOutbox();
        if (drenado.esTransitorio()) {
            return drenado;
        }
        return bajarDelta(drenado.getMensaje());
    }

    // ------------------------------------------------------------------ drenado

    private ResultadoSync drenarOutbox() {
        String ultimoErrorPermanente = null;
        while (true) {
            List<OperacionPendienteEntity> lote = outbox.primeras(LOTE);
            if (lote.isEmpty()) {
                break;
            }
            for (OperacionPendienteEntity operacion : lote) {
                ResultadoSync resultado = procesar(operacion);
                if (resultado.esTransitorio()) {
                    return resultado;
                }
                if (resultado.esPermanente() && resultado.getMensaje() != null) {
                    ultimoErrorPermanente = resultado.getMensaje();
                }
            }
            if (lote.size() < LOTE) {
                break;
            }
        }
        return ultimoErrorPermanente == null
                ? ResultadoSync.ok()
                : ResultadoSync.permanente(ultimoErrorPermanente);
    }

    private ResultadoSync procesar(OperacionPendienteEntity operacion) {
        switch (operacion.getTipo()) {
            case TipoOperacion.ACTUALIZAR_EMPLEADO:
                return actualizarDatos(operacion);
            case TipoOperacion.CAMBIAR_ROL_EMPLEADO:
                return cambiarRol(operacion);
            case TipoOperacion.CAMBIAR_ESTADO_EMPLEADO:
                return cambiarEstado(operacion);
            default:
                // Un tipo que esta versión no conoce no puede bloquear la cola. Ojo: acá
                // solo llegan operaciones del módulo EMPLEADOS — el outbox está particionado
                // justamente para que este `default` no se coma las del Menú.
                outbox.descartar(operacion.getId());
                return ResultadoSync.permanente("Operación desconocida en la cola.");
        }
    }

    private ResultadoSync actualizarDatos(OperacionPendienteEntity operacion) {
        EmpleadoEntity fila = empleadoDao.porId((int) operacion.getIdLocal());
        if (fila == null) {
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        ResultadoRed<Void> resultado = remoto.actualizarDatos(
                fila.getIdEmpleado(), fila.getIdAuthUser(), fila.getNombres(),
                fila.getApellidos(), fila.getIdentidad(), fila.getTelefono(), fila.getCorreo());
        return aplicar(operacion, resultado, fila);
    }

    private ResultadoSync cambiarRol(OperacionPendienteEntity operacion) {
        EmpleadoEntity fila = empleadoDao.porId((int) operacion.getIdLocal());
        if (fila == null) {
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        ResultadoRed<Void> resultado = remoto.cambiarRol(fila.getIdAuthUser(), fila.getRol());
        return aplicar(operacion, resultado, fila);
    }

    private ResultadoSync cambiarEstado(OperacionPendienteEntity operacion) {
        EmpleadoEntity fila = empleadoDao.porId((int) operacion.getIdLocal());
        if (fila == null) {
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        ResultadoRed<Void> resultado =
                remoto.cambiarEstado(fila.getIdAuthUser(), fila.isActivo());
        return aplicar(operacion, resultado, fila);
    }

    private ResultadoSync aplicar(OperacionPendienteEntity operacion,
                                  ResultadoRed<Void> resultado, EmpleadoEntity fila) {
        if (!resultado.isExitoso()) {
            return manejarFallo(operacion, resultado, fila);
        }
        fila.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        empleadoDao.actualizar(fila);
        outbox.marcarExito(operacion.getId());
        return ResultadoSync.ok();
    }

    /**
     * Transitorio → se registra el error y, si quedan intentos, corta la pasada; si se
     * agotaron, se descarta como si fuera permanente. Permanente → se descarta para no
     * bloquear la cola y la fila queda en {@code ERROR} para que la UI lo pinte.
     *
     * <p>Acá el caso permanente típico no es un bug: es el trigger {@code proteger_admins()}
     * rechazando que un admin toque a otro, con un mensaje escrito para que lo lea una
     * persona. Por eso el texto del servidor se propaga tal cual.</p>
     */
    private ResultadoSync manejarFallo(OperacionPendienteEntity operacion,
                                       ResultadoRed<?> resultado,
                                       @Nullable EmpleadoEntity fila) {
        String mensaje = resultado.getMensaje() == null
                ? "No se pudo sincronizar un cambio de empleado." : resultado.getMensaje();
        if (ClasificadorDeError.esTransitorio(resultado.getCodigoHttp())) {
            boolean quedanIntentos =
                    outbox.registrarErrorTransitorio(operacion.getId(), mensaje, MAX_INTENTOS);
            if (quedanIntentos) {
                return ResultadoSync.transitorio(mensaje);
            }
        }
        outbox.descartar(operacion.getId());
        if (fila != null) {
            fila.setEstadoSync(EstadoSync.ERROR.name());
            empleadoDao.actualizar(fila);
        }
        return ResultadoSync.permanente(mensaje);
    }

    // ------------------------------------------------------------------ delta

    private ResultadoSync bajarDelta(@Nullable String errorPermanenteDelDrenado) {
        // La marca queda FIJA durante toda la pasada y las páginas avanzan por offset (ver
        // el Javadoc de la clase y de SincronizadorMenu.bajarPlatillos()).
        String marcaInicial = leerMarca();
        String marcaMaxima = marcaInicial;
        boolean huboConflictoLocal = false;
        int desplazamiento = 0;

        for (int pagina = 0; pagina < MAX_PAGINAS; pagina++) {
            ResultadoRed<List<EmpleadoDto>> resultado =
                    remoto.listarDesde(marcaInicial, desplazamiento);
            if (!resultado.isExitoso()) {
                return convertirFalloDelta(resultado);
            }
            List<EmpleadoDto> filas = resultado.getValor();
            for (EmpleadoDto dto : filas) {
                if (dto.getActualizadoEn() != null) {
                    marcaMaxima = mayor(marcaMaxima, dto.getActualizadoEn());
                }
            }
            huboConflictoLocal |= aplicarPagina(filas);
            desplazamiento += filas.size();
            if (filas.size() < EmpleadoRemoto.LIMITE_DELTA) {
                break;
            }
        }

        guardarMarca(marcaMaxima);
        if (errorPermanenteDelDrenado != null) {
            return ResultadoSync.permanente(errorPermanenteDelDrenado);
        }
        return huboConflictoLocal
                ? ResultadoSync.permanente(CAMBIO_LOCAL_PERDIDO)
                : ResultadoSync.ok();
    }

    /**
     * Aplica una página entera en <b>una sola transacción</b> — ver el Javadoc de
     * {@link EjecutorDeTransaccion} y de {@code SincronizadorMenu.aplicarPagina}.
     *
     * @return {@code true} si alguna fila perdió un cambio local por conflicto LWW
     */
    private boolean aplicarPagina(List<EmpleadoDto> pagina) {
        boolean[] huboConflicto = {false};
        transacciones.enTransaccion(() -> {
            for (EmpleadoDto dto : pagina) {
                huboConflicto[0] |= aplicarEmpleado(dto);
            }
        });
        return huboConflicto[0];
    }

    /** @return {@code true} si el servidor pisó un cambio local que no se había subido. */
    private boolean aplicarEmpleado(EmpleadoDto dto) {
        EmpleadoEntity existente = empleadoDao.porId(dto.getIdEmpleado());
        if (existente == null) {
            empleadoDao.insertar(EmpleadoMapper.desdeServidor(dto));
            return false;
        }
        if (ganaElServidor(existente.getEstadoSync(), existente.getActualizadoEn(),
                dto.getActualizadoEn())) {
            descartarOperacionesDe(existente.getIdEmpleado());
            empleadoDao.insertar(EmpleadoMapper.desdeServidor(dto));
            return !EstadoSync.SINCRONIZADO.name().equals(existente.getEstadoSync());
        }
        // Gana el local (fila pendiente/error con marca más nueva): no se pisa.
        return false;
    }

    /**
     * Last-write-wins: el servidor gana si la fila local está sincronizada (es la misma
     * versión, pisarla es inofensivo) o, estando pendiente/error, trae una marca más nueva
     * que la local.
     */
    private boolean ganaElServidor(@Nullable String estadoLocal,
                                   @Nullable String marcaLocal,
                                   @Nullable String marcaServidor) {
        if (EstadoSync.SINCRONIZADO.name().equals(estadoLocal)) {
            return true;
        }
        if (marcaServidor == null) {
            return false;
        }
        if (marcaLocal == null) {
            return true;
        }
        return marcaServidor.compareTo(marcaLocal) > 0;
    }

    private void descartarOperacionesDe(long idLocal) {
        for (OperacionPendienteEntity pendiente : outbox.deFila(idLocal)) {
            outbox.descartar(pendiente.getId());
        }
    }

    private ResultadoSync convertirFalloDelta(ResultadoRed<?> resultado) {
        String mensaje = resultado.getMensaje() == null
                ? "No se pudo sincronizar la lista de empleados." : resultado.getMensaje();
        if (ClasificadorDeError.esTransitorio(resultado.getCodigoHttp())) {
            return ResultadoSync.transitorio(mensaje);
        }
        return ResultadoSync.permanente(mensaje);
    }

    // ------------------------------------------------------------------ marca de agua

    @Nullable
    private String leerMarca() {
        SincronizacionEntity marca = sincronizacionDao.porTabla(TABLA);
        return marca == null ? null : marca.getMarcaAgua();
    }

    private void guardarMarca(@Nullable String marca) {
        SincronizacionEntity entidad = new SincronizacionEntity();
        entidad.setTabla(TABLA);
        entidad.setMarcaAgua(marca);
        entidad.setUltimoIntento(System.currentTimeMillis());
        entidad.setUltimoError(null);
        sincronizacionDao.guardar(entidad);
    }

    /** Comparación lexicográfica: los timestamps vienen del servidor en el mismo formato. */
    private static String mayor(@Nullable String a, @Nullable String b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.compareTo(b) >= 0 ? a : b;
    }
}
