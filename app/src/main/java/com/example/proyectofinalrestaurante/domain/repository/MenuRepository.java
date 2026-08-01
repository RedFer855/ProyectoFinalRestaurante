package com.example.proyectofinalrestaurante.domain.repository;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.Categoria;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.ImagenPlatillo;
import com.example.proyectofinalrestaurante.domain.model.NuevoPlatillo;
import com.example.proyectofinalrestaurante.domain.model.Platillo;

import java.util.List;

/**
 * Contrato del módulo Menú (Domain Layer). {@code data} lo implementa; es la única cara
 * que {@code ui} ve del menú.
 *
 * <p>Desde la Fase 2b (offline-first) las <b>lecturas</b> son {@link LiveData} sobre Room:
 * nunca fallan y siempre reflejan lo que hay en disco al instante. Las <b>escrituras</b> son
 * optimistas — escriben en Room, encolan en el outbox y devuelven {@code Result} aunque no
 * haya red; la subida al servidor la hace en segundo plano el {@code SyncWorker}. Ver
 * [[Plan Fase 2b - Offline-First con Room y Outbox]].</p>
 *
 * <p>Los ids que la UI le pasa a las escrituras son siempre <b>locales</b>
 * ({@code idLocal}); el mapeo a {@code id_servidor} vive en {@code data}.</p>
 */
public interface MenuRepository {

    LiveData<List<Platillo>> observarPlatillos();

    LiveData<List<Categoria>> observarCategorias();

    /** Sincronizando en este momento y, si algo se cayó de forma permanente, el error. */
    LiveData<EstadoSincronizacion> getEstadoSincronizacion();

    /**
     * Pide una sincronización (drenar el outbox y bajar el delta). No bloquea: dispara el
     * trabajo único de {@code SyncWorker}, que ya corre con constraint de red.
     */
    void sincronizar();

    /**
     * Crea el platillo en Room y encola su subida. Si viene imagen, se guarda como archivo
     * local y se sube cuando drene el outbox.
     *
     * @return el {@code idLocal} de la fila creada
     */
    Result<Long> crearPlatillo(NuevoPlatillo nuevo, @Nullable ImagenPlatillo imagen);

    /**
     * Actualiza los datos de un platillo existente. Si el platillo todavía no se subió
     * ({@code idServidor == null}), la edición se pliega al {@code CREAR} pendiente en vez
     * de encolar una actualización que no tendría a qué apuntar (Plan Fase 2b, §5.5).
     */
    Result<Void> actualizarPlatillo(int idLocal, NuevoPlatillo datos,
                                    @Nullable ImagenPlatillo imagenNueva);

    /** Deja el platillo sin foto: limpia la ruta local y encola la operación. */
    Result<Void> quitarImagen(int idLocal);

    /** Activa o desactiva. Un platillo nunca se borra: rompería el historial de pedidos. */
    Result<Void> cambiarEstadoPlatillo(int idLocal, boolean activo);

    Result<Long> crearCategoria(String descripcion);

    Result<Void> renombrarCategoria(int idLocal, String descripcion);

    Result<Void> cambiarEstadoCategoria(int idLocal, boolean activo);

    /** Solo funciona si la categoría no tiene platillos; si los tiene, el servidor la rechaza. */
    Result<Void> borrarCategoria(int idLocal);
}
