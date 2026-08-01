package com.example.proyectofinalrestaurante.data.repository;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.proyectofinalrestaurante.data.local.AppDatabase;
import com.example.proyectofinalrestaurante.data.local.dao.CategoriaDao;
import com.example.proyectofinalrestaurante.data.local.dao.PlatilloDao;
import com.example.proyectofinalrestaurante.data.local.entity.CategoriaEntity;
import com.example.proyectofinalrestaurante.data.local.entity.PlatilloEntity;
import com.example.proyectofinalrestaurante.data.local.mapper.CategoriaMapper;
import com.example.proyectofinalrestaurante.data.local.mapper.PlatilloMapper;
import com.example.proyectofinalrestaurante.data.outbox.Outbox;
import com.example.proyectofinalrestaurante.data.outbox.TipoOperacion;
import com.example.proyectofinalrestaurante.data.sync.SyncScheduler;
import com.example.proyectofinalrestaurante.data.sync.ObservadorSincronizacion;
import com.example.proyectofinalrestaurante.data.sync.PayloadOperacion;
import com.example.proyectofinalrestaurante.domain.ReglasMenu;
import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.Categoria;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.ImagenPlatillo;
import com.example.proyectofinalrestaurante.domain.model.NuevoPlatillo;
import com.example.proyectofinalrestaurante.domain.model.Platillo;
import com.example.proyectofinalrestaurante.domain.repository.MenuRepository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implementación local-first de {@link MenuRepository} (Plan Fase 2b, E6).
 *
 * <p>La UI <b>nunca</b> habla con la red desde 2b: lee por {@link LiveData} desde Room (que
 * nunca falla) y las escrituras son optimistas — se escriben en la base, se encola la
 * operación en el {@link Outbox} y se dispara el {@link SyncScheduler}; el
 * {@code SyncWorker} la sube en segundo plano cuando haya red (Plan Fase 2b, §4.2).</p>
 *
 * <p>Implementa {@link ObservadorSincronizacion} para que el worker le avise el estado
 * global de la sincronización (§4.5); el {@code MenuViewModelFactory} lo registra en
 * {@code SyncApplication}. La conexión entre el worker y esta clase queda así desacoplada:
 * el worker no conoce al repositorio, y el repositorio no conoce WorkManager más allá del
 * disparo único del scheduler.</p>
 *
 * <p>La imagen viaja como archivo en {@code carpetaImagenes} (Plan Fase 2b, §5.2) y el
 * outbox guarda solo la ruta: un JPEG en base64 dentro de una fila de SQLite pesa ~2.7 MB
 * de texto. Al drenar la operación con éxito, el sincronizador borra el archivo.</p>
 */
public final class MenuRepositorioLocal implements MenuRepository, ObservadorSincronizacion {

    private static final String IMAGEN_INVALIDA =
            "La foto no se puede subir: revisá que pese menos de 2 MB y sea JPG, PNG o WEBP.";
    private static final String NO_SE_PUDO_GUARDAR_FOTO =
            "No se pudo guardar la foto en el teléfono. Intentá de nuevo.";
    private static final String NO_SE_ENCONTRO_PLATILLO =
            "No se encontró el platillo en la base local.";
    private static final String NO_SE_ENCONTRO_CATEGORIA =
            "No se encontró la categoría en la base local.";

    private final PlatilloDao platilloDao;
    private final CategoriaDao categoriaDao;
    private final Outbox outbox;
    private final File carpetaImagenes;
    private final Context contexto;

    private final MutableLiveData<EstadoSincronizacion> estadoSincronizacion =
            new MutableLiveData<>(new EstadoSincronizacion(false, null));

    public MenuRepositorioLocal(AppDatabase base, Outbox outbox, File carpetaImagenes,
                                Context contexto) {
        this.platilloDao = base.platilloDao();
        this.categoriaDao = base.categoriaDao();
        this.outbox = outbox;
        this.carpetaImagenes = carpetaImagenes;
        this.contexto = contexto;
    }

    // ------------------------------------------------------------------ lecturas (LiveData sobre Room)

    @Override
    public LiveData<List<Platillo>> observarPlatillos() {
        return Transformations.map(platilloDao.observarTodos(), MenuRepositorioLocal::aDominio);
    }

    @Override
    public LiveData<List<Categoria>> observarCategorias() {
        return Transformations.map(categoriaDao.observarTodas(),
                MenuRepositorioLocal::categoriasADominio);
    }

    @Override
    public LiveData<EstadoSincronizacion> getEstadoSincronizacion() {
        return estadoSincronizacion;
    }

    // ------------------------------------------------------------------ sincronización

    @Override
    public void sincronizar() {
        SyncScheduler.solicitar(contexto);
    }

    @Override
    public void alIniciar() {
        estadoSincronizacion.postValue(new EstadoSincronizacion(true, null));
    }

    @Override
    public void alTerminar(@Nullable String ultimoError) {
        estadoSincronizacion.postValue(new EstadoSincronizacion(false, ultimoError));
    }

    // ------------------------------------------------------------------ platillos

    @Override
    public Result<Long> crearPlatillo(NuevoPlatillo nuevo, @Nullable ImagenPlatillo imagen) {
        if (!validarImagen(imagen)) {
            return Result.fail(IMAGEN_INVALIDA);
        }

        PlatilloEntity fila = new PlatilloEntity();
        fila.setNombre(nuevo.getNombre());
        fila.setDescripcion(nuevo.getDescripcion());
        fila.setPrecio(nuevo.getPrecio());
        fila.setIdCategoriaLocal(nuevo.getIdCategoria());
        fila.setNombreCategoria(nombreDeCategoria(nuevo.getIdCategoria()));
        fila.setActivo(true);
        fila.setEstadoSync(EstadoSync.PENDIENTE.name());
        long idLocal = platilloDao.insertar(fila);

        String rutaImagenLocal = guardarImagenLocal(imagen);
        if (imagen != null && rutaImagenLocal == null) {
            // Compensación: no queda una fila pendiente cuya foto no se pudo persistir.
            platilloDao.borrar(platilloDao.porIdLocal(idLocal));
            return Result.fail(NO_SE_PUDO_GUARDAR_FOTO);
        }

        outbox.encolar(TipoOperacion.CREAR_PLATILLO, idLocal, null, rutaImagenLocal);
        sincronizar();
        return Result.ok(idLocal);
    }

    @Override
    public Result<Void> actualizarPlatillo(int idLocal, NuevoPlatillo datos,
                                           @Nullable ImagenPlatillo imagenNueva) {
        if (!validarImagen(imagenNueva)) {
            return Result.fail(IMAGEN_INVALIDA);
        }
        PlatilloEntity fila = platilloDao.porIdLocal(idLocal);
        if (fila == null) {
            return Result.fail(NO_SE_ENCONTRO_PLATILLO);
        }

        fila.setNombre(datos.getNombre());
        fila.setDescripcion(datos.getDescripcion());
        fila.setPrecio(datos.getPrecio());
        fila.setIdCategoriaLocal(datos.getIdCategoria());
        fila.setNombreCategoria(nombreDeCategoria(datos.getIdCategoria()));
        fila.setEstadoSync(EstadoSync.PENDIENTE.name());
        platilloDao.actualizar(fila);

        String rutaImagenLocal = guardarImagenLocal(imagenNueva);
        if (imagenNueva != null && rutaImagenLocal == null) {
            return Result.fail(NO_SE_PUDO_GUARDAR_FOTO);
        }

        if (fila.getIdServidor() == null) {
            // Todavía no se subió (hay un CREAR pendiente): la edición ya viaja en ese
            // CREAR, que al drenar lee la fila local (Plan Fase 2b, §5.5). La foto nueva
            // reemplaza a la que llevaba la operación.
            outbox.actualizarImagenDelCrear(idLocal, rutaImagenLocal);
            return Result.ok(null);
        }

        outbox.encolar(TipoOperacion.ACTUALIZAR_PLATILLO, idLocal, null, rutaImagenLocal);
        sincronizar();
        return Result.ok(null);
    }

    @Override
    public Result<Void> quitarImagen(int idLocal) {
        PlatilloEntity fila = platilloDao.porIdLocal(idLocal);
        if (fila == null) {
            return Result.fail(NO_SE_ENCONTRO_PLATILLO);
        }
        String rutaVieja = fila.getRutaImagen();
        fila.setRutaImagen(null);
        fila.setEstadoSync(EstadoSync.PENDIENTE.name());
        platilloDao.actualizar(fila);

        if (fila.getIdServidor() == null) {
            // Nunca se subió la foto: el CREAR pendiente deja de llevar imagen.
            outbox.actualizarImagenDelCrear(idLocal, null);
            return Result.ok(null);
        }
        outbox.encolar(TipoOperacion.QUITAR_IMAGEN_PLATILLO, idLocal,
                PayloadOperacion.quitarImagen(rutaVieja), null);
        sincronizar();
        return Result.ok(null);
    }

    @Override
    public Result<Void> cambiarEstadoPlatillo(int idLocal, boolean activo) {
        PlatilloEntity fila = platilloDao.porIdLocal(idLocal);
        if (fila == null) {
            return Result.fail(NO_SE_ENCONTRO_PLATILLO);
        }
        fila.setActivo(activo);
        fila.setEstadoSync(EstadoSync.PENDIENTE.name());
        platilloDao.actualizar(fila);

        if (fila.getIdServidor() == null) {
            // El CREAR pendiente ya va a crear la fila; el estado viaja en ella.
            return Result.ok(null);
        }
        outbox.encolar(TipoOperacion.CAMBIAR_ESTADO_PLATILLO, idLocal, null, null);
        sincronizar();
        return Result.ok(null);
    }

    // ------------------------------------------------------------------ categorías

    @Override
    public Result<Long> crearCategoria(String descripcion) {
        CategoriaEntity fila = new CategoriaEntity();
        fila.setDescripcion(descripcion);
        fila.setActivo(true);
        fila.setCantidadPlatillos(0);
        fila.setCantidadPlatillosActivos(0);
        fila.setEstadoSync(EstadoSync.PENDIENTE.name());
        long idLocal = categoriaDao.insertar(fila);

        outbox.encolar(TipoOperacion.CREAR_CATEGORIA, idLocal, null, null);
        sincronizar();
        return Result.ok(idLocal);
    }

    @Override
    public Result<Void> renombrarCategoria(int idLocal, String descripcion) {
        CategoriaEntity fila = categoriaDao.porIdLocal(idLocal);
        if (fila == null) {
            return Result.fail(NO_SE_ENCONTRO_CATEGORIA);
        }
        fila.setDescripcion(descripcion);
        fila.setEstadoSync(EstadoSync.PENDIENTE.name());
        categoriaDao.actualizar(fila);

        if (fila.getIdServidor() == null) {
            return Result.ok(null);
        }
        outbox.encolar(TipoOperacion.RENOMBRAR_CATEGORIA, idLocal, null, null);
        sincronizar();
        return Result.ok(null);
    }

    @Override
    public Result<Void> cambiarEstadoCategoria(int idLocal, boolean activo) {
        CategoriaEntity fila = categoriaDao.porIdLocal(idLocal);
        if (fila == null) {
            return Result.fail(NO_SE_ENCONTRO_CATEGORIA);
        }
        fila.setActivo(activo);
        fila.setEstadoSync(EstadoSync.PENDIENTE.name());
        categoriaDao.actualizar(fila);

        if (fila.getIdServidor() == null) {
            return Result.ok(null);
        }
        outbox.encolar(TipoOperacion.CAMBIAR_ESTADO_CATEGORIA, idLocal, null, null);
        sincronizar();
        return Result.ok(null);
    }

    @Override
    public Result<Void> borrarCategoria(int idLocal) {
        CategoriaEntity fila = categoriaDao.porIdLocal(idLocal);
        if (fila == null) {
            return Result.ok(null);
        }
        Integer idServidor = fila.getIdServidor();
        categoriaDao.borrar(fila);

        if (idServidor == null) {
            // Nunca se subió: no hay nada que borrar en el servidor.
            return Result.ok(null);
        }
        // La fila local ya no existe: el id_servidor viaja en el payload.
        outbox.encolar(TipoOperacion.BORRAR_CATEGORIA, idLocal,
                PayloadOperacion.borrarCategoria(idServidor), null);
        sincronizar();
        return Result.ok(null);
    }

    // ------------------------------------------------------------------ helpers

    private boolean validarImagen(@Nullable ImagenPlatillo imagen) {
        return imagen == null || ReglasMenu.puedeSubirse(imagen);
    }

    /**
     * Guarda la imagen comprimida como archivo en {@code carpetaImagenes} y devuelve el
     * nombre relativo, o {@code null} si no se pudo (o no había imagen que guardar).
     */
    @Nullable
    private String guardarImagenLocal(@Nullable ImagenPlatillo imagen) {
        if (imagen == null) {
            return null;
        }
        String nombre = UUID.randomUUID() + "." + imagen.extensionDeArchivo();
        File destino = new File(carpetaImagenes, nombre);
        try (FileOutputStream salida = new FileOutputStream(destino)) {
            salida.write(imagen.getBytes());
        } catch (IOException ex) {
            return null;
        }
        return nombre;
    }

    @Nullable
    private String nombreDeCategoria(int idCategoriaLocal) {
        CategoriaEntity categoria = categoriaDao.porIdLocal(idCategoriaLocal);
        return categoria == null ? null : categoria.getDescripcion();
    }

    private static List<Platillo> aDominio(List<PlatilloEntity> entidades) {
        List<Platillo> dominio = new ArrayList<>(entidades.size());
        for (PlatilloEntity entidad : entidades) {
            dominio.add(PlatilloMapper.aDominio(entidad));
        }
        return dominio;
    }

    private static List<Categoria> categoriasADominio(List<CategoriaEntity> entidades) {
        List<Categoria> dominio = new ArrayList<>(entidades.size());
        for (CategoriaEntity entidad : entidades) {
            dominio.add(CategoriaMapper.aDominio(entidad));
        }
        return dominio;
    }
}
