package com.example.proyectofinalrestaurante.data.sync;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.data.local.dao.CategoriaDao;
import com.example.proyectofinalrestaurante.data.local.dao.PlatilloDao;
import com.example.proyectofinalrestaurante.data.local.dao.SincronizacionDao;
import com.example.proyectofinalrestaurante.data.local.entity.CategoriaEntity;
import com.example.proyectofinalrestaurante.data.local.entity.OperacionPendienteEntity;
import com.example.proyectofinalrestaurante.data.local.entity.PlatilloEntity;
import com.example.proyectofinalrestaurante.data.local.entity.SincronizacionEntity;
import com.example.proyectofinalrestaurante.data.local.mapper.CategoriaMapper;
import com.example.proyectofinalrestaurante.data.local.mapper.PlatilloMapper;
import com.example.proyectofinalrestaurante.data.outbox.ClasificadorDeError;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.remote.dto.CategoriaDto;
import com.example.proyectofinalrestaurante.data.remote.dto.PlatilloDto;
import com.example.proyectofinalrestaurante.data.repository.MenuRemoto;
import com.example.proyectofinalrestaurante.data.repository.ResultadoRed;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.ImagenPlatillo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * El sincronizador del módulo Menú (Plan Fase 2b, E5).
 *
 * <p>Dos trabajos, en orden: primero <b>drena el outbox</b> (sube las escrituras locales en
 * orden FIFO, §4.4) y después <b>baja el delta</b> con la marca de agua (§4.3). Si el drenado
 * se corta por un error transitorio, no se baja el delta: el reintento lo va a repetir todo.</p>
 *
 * <p>No conoce la red ni la UI: usa {@link MenuRemoto} para las llamadas y devuelve un
 * {@link ResultadoSync} que el worker traduce a {@code Result.retry()}/{@code success()}. El
 * token de sesión entra por {@code MenuRemoto}, así que el sincronizador es testeable con
 * fakes (Plan Fase 2b, §5.3: el worker no debe depender de {@code SesionActual}).</p>
 */
public final class SincronizadorMenu {

    static final int MAX_INTENTOS = 3;
    static final int LOTE = 50;
    static final String TABLA_CATEGORIAS = "categorias";
    static final String TABLA_PLATILLOS = "platillos";

    /** Mensaje con que el conflicto LWW (§4.6) avisa que un cambio local se perdió. */
    static final String CAMBIO_LOCAL_PERDIDO =
            "Un cambio local se perdió: el servidor tenía una versión más reciente.";

    private final MenuRemoto remoto;
    private final Outbox outbox;
    private final PlatilloDao platilloDao;
    private final CategoriaDao categoriaDao;
    private final SincronizacionDao sincronizacionDao;
    private final File carpetaImagenes;

    public SincronizadorMenu(MenuRemoto remoto, Outbox outbox,
                             PlatilloDao platilloDao, CategoriaDao categoriaDao,
                             SincronizacionDao sincronizacionDao, File carpetaImagenes) {
        this.remoto = remoto;
        this.outbox = outbox;
        this.platilloDao = platilloDao;
        this.categoriaDao = categoriaDao;
        this.sincronizacionDao = sincronizacionDao;
        this.carpetaImagenes = carpetaImagenes;
    }

    // ------------------------------------------------------------------ orquestación

    /** Una pasada completa: drenar y bajar. Devuelve el resultado para el worker. */
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
            case TipoOperacion.CREAR_PLATILLO:
                return crearPlatillo(operacion);
            case TipoOperacion.ACTUALIZAR_PLATILLO:
                return actualizarPlatillo(operacion);
            case TipoOperacion.CAMBIAR_ESTADO_PLATILLO:
                return cambiarEstadoPlatillo(operacion);
            case TipoOperacion.QUITAR_IMAGEN_PLATILLO:
                return quitarImagenPlatillo(operacion);
            case TipoOperacion.CREAR_CATEGORIA:
                return crearCategoria(operacion);
            case TipoOperacion.RENOMBRAR_CATEGORIA:
                return renombrarCategoria(operacion);
            case TipoOperacion.CAMBIAR_ESTADO_CATEGORIA:
                return cambiarEstadoCategoria(operacion);
            case TipoOperacion.BORRAR_CATEGORIA:
                return borrarCategoria(operacion);
            default:
                // Operación de un tipo que esta versión no conoce: no puede bloquear la cola.
                outbox.descartar(operacion.getId());
                return ResultadoSync.permanente("Operación desconocida en la cola.");
        }
    }

    // ------------------------------------------------------------------ platillos

    private ResultadoSync crearPlatillo(OperacionPendienteEntity operacion) {
        PlatilloEntity fila = platilloDao.porIdLocal(operacion.getIdLocal());
        if (fila == null) {
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        Integer idCategoriaServidor = idCategoriaServidor(fila.getIdCategoriaLocal());
        if (idCategoriaServidor == null) {
            // La categoría todavía no se sincronizó (CREAR_CATEGORIA en cola o falló).
            // Es un bloqueo transitorio: el reintento la encuentra ya sincronizada.
            return ResultadoSync.transitorio(
                    "La categoría del platillo todavía no se sincronizó.");
        }

        ImagenPlatillo imagen = imagenDe(operacion.getRutaImagenLocal());
        ResultadoRed<PlatilloDto> resultado = remoto.crearPlatillo(
                fila.getNombre(), fila.getDescripcion(), fila.getPrecio(),
                idCategoriaServidor, imagen);
        if (!resultado.isExitoso()) {
            return manejarFallo(operacion, resultado, fila, null);
        }

        PlatilloDto creado = resultado.getValor();
        fila.setIdServidor(creado.getIdPlatillo());
        fila.setRutaImagen(creado.getRutaImagen());
        fila.setActualizadoEn(creado.getActualizadoEn());
        fila.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        platilloDao.actualizar(fila);
        outbox.marcarExito(operacion.getId());
        borrarImagenLocal(operacion.getRutaImagenLocal());
        return ResultadoSync.ok();
    }

    private ResultadoSync actualizarPlatillo(OperacionPendienteEntity operacion) {
        PlatilloEntity fila = platilloDao.porIdLocal(operacion.getIdLocal());
        if (fila == null || fila.getIdServidor() == null) {
            // Sin fila, o sin id_servidor (todavía no se creó): la edición ya viajó en el
            // CREAR pendiente (el repo pliega las ediciones, §5.5); no hay nada que PATCHear.
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        Integer idCategoriaServidor = idCategoriaServidor(fila.getIdCategoriaLocal());
        if (idCategoriaServidor == null) {
            return ResultadoSync.transitorio(
                    "La categoría del platillo todavía no se sincronizó.");
        }

        ImagenPlatillo imagenNueva = imagenDe(operacion.getRutaImagenLocal());
        ResultadoRed<String> resultado = remoto.actualizarPlatillo(
                fila.getIdServidor(), fila.getNombre(), fila.getDescripcion(),
                fila.getPrecio(), idCategoriaServidor, fila.isActivo(),
                imagenNueva, fila.getRutaImagen());
        if (!resultado.isExitoso()) {
            return manejarFallo(operacion, resultado, fila, null);
        }

        if (resultado.getValor() != null) {
            fila.setRutaImagen(resultado.getValor());
        }
        fila.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        platilloDao.actualizar(fila);
        outbox.marcarExito(operacion.getId());
        borrarImagenLocal(operacion.getRutaImagenLocal());
        return ResultadoSync.ok();
    }

    private ResultadoSync cambiarEstadoPlatillo(OperacionPendienteEntity operacion) {
        PlatilloEntity fila = platilloDao.porIdLocal(operacion.getIdLocal());
        if (fila == null || fila.getIdServidor() == null) {
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        ResultadoRed<Void> resultado =
                remoto.cambiarEstadoPlatillo(fila.getIdServidor(), fila.isActivo());
        if (!resultado.isExitoso()) {
            return manejarFallo(operacion, resultado, fila, null);
        }
        fila.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        platilloDao.actualizar(fila);
        outbox.marcarExito(operacion.getId());
        return ResultadoSync.ok();
    }

    private ResultadoSync quitarImagenPlatillo(OperacionPendienteEntity operacion) {
        PlatilloEntity fila = platilloDao.porIdLocal(operacion.getIdLocal());
        if (fila == null || fila.getIdServidor() == null) {
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        String rutaVieja = PayloadOperacion.rutaViejaDe(operacion.getPayloadJson());
        ResultadoRed<Void> resultado =
                remoto.quitarImagen(fila.getIdServidor(), rutaVieja);
        if (!resultado.isExitoso()) {
            return manejarFallo(operacion, resultado, fila, null);
        }
        fila.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        platilloDao.actualizar(fila);
        outbox.marcarExito(operacion.getId());
        return ResultadoSync.ok();
    }

    // ------------------------------------------------------------------ categorías

    private ResultadoSync crearCategoria(OperacionPendienteEntity operacion) {
        CategoriaEntity fila = categoriaDao.porIdLocal(operacion.getIdLocal());
        if (fila == null) {
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        ResultadoRed<CategoriaDto> resultado = remoto.crearCategoria(fila.getDescripcion());
        if (!resultado.isExitoso()) {
            return manejarFallo(operacion, resultado, null, fila);
        }
        CategoriaDto creada = resultado.getValor();
        fila.setIdServidor(creada.getIdCategoria());
        fila.setActualizadoEn(creada.getActualizadoEn());
        fila.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        categoriaDao.actualizar(fila);
        outbox.marcarExito(operacion.getId());
        return ResultadoSync.ok();
    }

    private ResultadoSync renombrarCategoria(OperacionPendienteEntity operacion) {
        CategoriaEntity fila = categoriaDao.porIdLocal(operacion.getIdLocal());
        if (fila == null || fila.getIdServidor() == null) {
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        ResultadoRed<Void> resultado =
                remoto.renombrarCategoria(fila.getIdServidor(), fila.getDescripcion());
        if (!resultado.isExitoso()) {
            return manejarFallo(operacion, resultado, null, fila);
        }
        fila.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        categoriaDao.actualizar(fila);
        outbox.marcarExito(operacion.getId());
        return ResultadoSync.ok();
    }

    private ResultadoSync cambiarEstadoCategoria(OperacionPendienteEntity operacion) {
        CategoriaEntity fila = categoriaDao.porIdLocal(operacion.getIdLocal());
        if (fila == null || fila.getIdServidor() == null) {
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        ResultadoRed<Void> resultado =
                remoto.cambiarEstadoCategoria(fila.getIdServidor(), fila.isActivo());
        if (!resultado.isExitoso()) {
            return manejarFallo(operacion, resultado, null, fila);
        }
        fila.setEstadoSync(EstadoSync.SINCRONIZADO.name());
        categoriaDao.actualizar(fila);
        outbox.marcarExito(operacion.getId());
        return ResultadoSync.ok();
    }

    private ResultadoSync borrarCategoria(OperacionPendienteEntity operacion) {
        Integer idServidor = PayloadOperacion.idServidorDe(operacion.getPayloadJson());
        if (idServidor == null) {
            outbox.descartar(operacion.getId());
            return ResultadoSync.ok();
        }
        ResultadoRed<Void> resultado = remoto.borrarCategoria(idServidor);
        if (!resultado.isExitoso()) {
            // La fila local ya se borró al encolar; el error (p. ej. tiene platillos) solo
            // se reporta y el delta de la próxima pasada vuelve a traer la categoría.
            return manejarFallo(operacion, resultado, null, null);
        }
        outbox.marcarExito(operacion.getId());
        return ResultadoSync.ok();
    }

    // ------------------------------------------------------------------ fallos

    /**
     * Clasifica el fallo de una operación. Transitorio → se registra el error y, si quedan
     * intentos, corta la pasada (el worker reintenta); si se agotaron, se descarta como si
     * fuera permanente. Permanente → se descarta para no bloquear la cola y se marca la fila
     * como {@code ERROR} para que la UI lo pinte (§4.4 y §4.5).
     */
    private ResultadoSync manejarFallo(OperacionPendienteEntity operacion,
                                       ResultadoRed<?> resultado,
                                       @Nullable PlatilloEntity platillo,
                                       @Nullable CategoriaEntity categoria) {
        String mensaje = resultado.getMensaje() == null
                ? "No se pudo sincronizar un cambio." : resultado.getMensaje();
        if (ClasificadorDeError.esTransitorio(resultado.getCodigoHttp())) {
            boolean quedanIntentos =
                    outbox.registrarErrorTransitorio(operacion.getId(), mensaje, MAX_INTENTOS);
            if (quedanIntentos) {
                return ResultadoSync.transitorio(mensaje);
            }
        }
        outbox.descartar(operacion.getId());
        marcarError(platillo, categoria);
        return ResultadoSync.permanente(mensaje);
    }

    private void marcarError(@Nullable PlatilloEntity platillo,
                             @Nullable CategoriaEntity categoria) {
        if (platillo != null) {
            platillo.setEstadoSync(EstadoSync.ERROR.name());
            platilloDao.actualizar(platillo);
        }
        if (categoria != null) {
            categoria.setEstadoSync(EstadoSync.ERROR.name());
            categoriaDao.actualizar(categoria);
        }
    }

    // ------------------------------------------------------------------ delta

    /**
     * Baja el delta (§4.3). El mensaje que trae es el de un error permanente del drenado,
     * que debe conservarse si el delta termina bien: la pasada "terminó" pero el usuario
     * tiene que enterarse de que algo no se subió.
     */
    private ResultadoSync bajarDelta(@Nullable String errorPermanenteDelDrenado) {
        ResultadoSync categorias = bajarCategorias();
        if (categorias.esTransitorio()) {
            return categorias;
        }
        ResultadoSync platillos = bajarPlatillos();
        if (platillos.esTransitorio()) {
            return platillos;
        }
        if (errorPermanenteDelDrenado != null) {
            return ResultadoSync.permanente(errorPermanenteDelDrenado);
        }
        if (categorias.esPermanente()) {
            return categorias;
        }
        if (platillos.esPermanente()) {
            return platillos;
        }
        return ResultadoSync.ok();
    }

    private ResultadoSync bajarCategorias() {
        String marca = leerMarca(TABLA_CATEGORIAS);
        boolean huboConflictoLocal = false;
        while (true) {
            ResultadoRed<List<CategoriaDto>> resultado = remoto.listarCategoriasDesde(marca);
            if (!resultado.isExitoso()) {
                return convertirFalloDelta(resultado);
            }
            List<CategoriaDto> pagina = resultado.getValor();
            for (CategoriaDto dto : pagina) {
                huboConflictoLocal |= aplicarCategoria(dto);
                if (dto.getActualizadoEn() != null) {
                    marca = mayor(marca, dto.getActualizadoEn());
                }
            }
            guardarMarca(TABLA_CATEGORIAS, marca);
            if (pagina.size() < MenuRemoto.LIMITE_DELTA) {
                break;
            }
        }
        return huboConflictoLocal
                ? ResultadoSync.permanente(CAMBIO_LOCAL_PERDIDO)
                : ResultadoSync.ok();
    }

    private ResultadoSync bajarPlatillos() {
        String marca = leerMarca(TABLA_PLATILLOS);
        boolean huboConflictoLocal = false;
        while (true) {
            ResultadoRed<List<PlatilloDto>> resultado = remoto.listarPlatillosDesde(marca);
            if (!resultado.isExitoso()) {
                return convertirFalloDelta(resultado);
            }
            List<PlatilloDto> pagina = resultado.getValor();
            for (PlatilloDto dto : pagina) {
                huboConflictoLocal |= aplicarPlatillo(dto);
                if (dto.getActualizadoEn() != null) {
                    marca = mayor(marca, dto.getActualizadoEn());
                }
            }
            guardarMarca(TABLA_PLATILLOS, marca);
            if (pagina.size() < MenuRemoto.LIMITE_DELTA) {
                break;
            }
        }
        return huboConflictoLocal
                ? ResultadoSync.permanente(CAMBIO_LOCAL_PERDIDO)
                : ResultadoSync.ok();
    }

    private boolean aplicarCategoria(CategoriaDto dto) {
        CategoriaEntity existente = categoriaDao.porIdServidor(dto.getIdCategoria());
        if (existente == null) {
            categoriaDao.insertar(CategoriaMapper.desdeServidor(dto));
            return false;
        }
        if (ganaElServidor(existente.getEstadoSync(), existente.getActualizadoEn(),
                dto.getActualizadoEn())) {
            descartarOperacionesDe(existente.getIdLocal());
            CategoriaEntity entidad = CategoriaMapper.desdeServidor(dto);
            entidad.setIdLocal(existente.getIdLocal());
            categoriaDao.actualizar(entidad);
            return true;
        }
        // Gana el local (fila pendiente/error con marca más nueva): no se pisa.
        return false;
    }

    private boolean aplicarPlatillo(PlatilloDto dto) {
        CategoriaEntity categoria = categoriaDao.porIdServidor(dto.getIdCategoria());
        int idCategoriaLocal = categoria == null ? 0 : categoria.getIdLocal();
        PlatilloEntity existente = platilloDao.porIdServidor(dto.getIdPlatillo());
        if (existente == null) {
            platilloDao.insertar(PlatilloMapper.desdeServidor(dto, idCategoriaLocal));
            return false;
        }
        if (ganaElServidor(existente.getEstadoSync(), existente.getActualizadoEn(),
                dto.getActualizadoEn())) {
            descartarOperacionesDe(existente.getIdLocal());
            PlatilloEntity entidad = PlatilloMapper.desdeServidor(dto, idCategoriaLocal);
            entidad.setIdLocal(existente.getIdLocal());
            platilloDao.actualizar(entidad);
            return true;
        }
        // Gana el local (fila pendiente/error con marca más nueva): no se pisa.
        return false;
    }

    /**
     * Last-write-wins (§4.6): el servidor gana si la fila local está sincronizada (es la
     * misma versión, pisarla es inofensivo) o, estando pendiente/error, trae una marca más
     * nueva que la local.
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
                ? "No se pudo sincronizar el menú." : resultado.getMensaje();
        if (ClasificadorDeError.esTransitorio(resultado.getCodigoHttp())) {
            return ResultadoSync.transitorio(mensaje);
        }
        return ResultadoSync.permanente(mensaje);
    }

    // ------------------------------------------------------------------ marca de agua

    @Nullable
    private String leerMarca(String tabla) {
        SincronizacionEntity marca = sincronizacionDao.porTabla(tabla);
        return marca == null ? null : marca.getMarcaAgua();
    }

    private void guardarMarca(String tabla, @Nullable String marca) {
        SincronizacionEntity entidad = new SincronizacionEntity();
        entidad.setTabla(tabla);
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

    // ------------------------------------------------------------------ imágenes locales

    @Nullable
    private Integer idCategoriaServidor(int idCategoriaLocal) {
        CategoriaEntity categoria = categoriaDao.porIdLocal(idCategoriaLocal);
        return categoria == null ? null : categoria.getIdServidor();
    }

    @Nullable
    private ImagenPlatillo imagenDe(@Nullable String rutaRelativa) {
        if (rutaRelativa == null || rutaRelativa.isEmpty()) {
            return null;
        }
        File archivo = new File(carpetaImagenes, rutaRelativa);
        if (!archivo.isFile()) {
            return null;
        }
        try (FileInputStream entrada = new FileInputStream(archivo)) {
            byte[] bytes = new byte[(int) archivo.length()];
            int leidos = 0;
            while (leidos < bytes.length) {
                int n = entrada.read(bytes, leidos, bytes.length - leidos);
                if (n < 0) {
                    break;
                }
                leidos += n;
            }
            return new ImagenPlatillo(bytes, mimeDe(archivo.getName()));
        } catch (IOException ex) {
            return null;
        }
    }

    private static String mimeDe(String nombreArchivo) {
        String nombre = nombreArchivo.toLowerCase(Locale.ROOT);
        if (nombre.endsWith(".png")) {
            return ImagenPlatillo.MIME_PNG;
        }
        if (nombre.endsWith(".webp")) {
            return ImagenPlatillo.MIME_WEBP;
        }
        return ImagenPlatillo.MIME_JPEG;
    }

    private void borrarImagenLocal(@Nullable String rutaRelativa) {
        if (rutaRelativa == null || rutaRelativa.isEmpty()) {
            return;
        }
        new File(carpetaImagenes, rutaRelativa).delete();
    }
}
