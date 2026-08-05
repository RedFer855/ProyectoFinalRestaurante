package com.example.proyectofinalrestaurante.ui.menu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.Categoria;
import com.example.proyectofinalrestaurante.domain.model.EstadoSincronizacion;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.ImagenPlatillo;
import com.example.proyectofinalrestaurante.domain.model.NuevoPlatillo;
import com.example.proyectofinalrestaurante.domain.model.Platillo;
import com.example.proyectofinalrestaurante.domain.repository.MenuRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * ViewModel del módulo Menú (Plan Fase 2a, E5; reescrito en 2b, E6 para offline-first).
 *
 * <p>El {@link ExecutorService} se inyecta por constructor — no se crea acá dentro, para
 * no replicar la deuda <b>P-005</b> del login.</p>
 *
 * <p>Desde 2b la fuente de datos es Room: el repositorio expone tres {@link LiveData} que
 * este ViewModel fusiona en un único {@link EstadoMenu}. Las listas que llegan se guardan
 * por referencia (nunca se mutan) y las escrituras, todas optimistas, corren en el
 * executor — así no hay dos hilos tocando el mismo {@code ArrayList}.</p>
 *
 * <p>El filtro por categoría y el texto de búsqueda viven acá y no en el Fragment: antes
 * eran campos del Fragment y se perdían al rotar la pantalla.</p>
 */
public class MenuViewModel extends ViewModel {

    private static final String PLATILLO_CREADO = "Platillo agregado al menú.";
    private static final String PLATILLO_ACTUALIZADO = "Platillo actualizado.";
    private static final String FOTO_QUITADA = "Foto quitada.";
    private static final String PLATILLO_ACTIVADO = "Platillo reactivado.";
    private static final String PLATILLO_DESACTIVADO = "Platillo desactivado.";
    private static final String CATEGORIA_CREADA = "Categoría creada.";
    private static final String CATEGORIA_RENOMBRADA = "Categoría renombrada.";
    private static final String CATEGORIA_ACTIVADA = "Categoría reactivada.";
    private static final String CATEGORIA_DESACTIVADA = "Categoría desactivada.";
    private static final String CATEGORIA_BORRADA = "Categoría borrada.";

    private final MenuRepository repositorio;
    private final ExecutorService executor;
    private final MediatorLiveData<EstadoMenu> estado = new MediatorLiveData<>();

    /** Últimas listas emitidas por Room, por referencia: nunca se mutan acá. */
    private List<Platillo> platillosActuales = Collections.emptyList();
    private List<Categoria> categoriasActuales = Collections.emptyList();
    private int filtroCategoria = EstadoMenu.SIN_FILTRO;
    private String textoBusqueda = "";
    private boolean sincronizando = false;
    @Nullable private String ultimoErrorSync = null;
    /**
     * Se enciende cuando termina la primera pasada de sincronización de esta pantalla.
     * Mientras esté en {@code false}, una lista vacía significa "todavía no sabemos", no
     * "no hay platillos" — ver {@link EstadoMenu#isEsperandoPrimeraCarga()}.
     */
    private boolean primeraSincronizacionHecha = false;

    public MenuViewModel(@NonNull MenuRepository repositorio, @NonNull ExecutorService executor) {
        this.repositorio = repositorio;
        this.executor = executor;
        estado.addSource(repositorio.observarPlatillos(), this::cuandoLleganPlatillos);
        estado.addSource(repositorio.observarCategorias(), this::cuandoLleganCategorias);
        estado.addSource(repositorio.getEstadoSincronizacion(), this::cuandoCambiaSincronizacion);
        estado.setValue(EstadoMenu.cargando());
        // Sync-on-launch: no depender del pull-to-refresh para la primera bajada de datos.
        // Ver el mismo comentario en MesasViewModel.
        sincronizar();
    }

    public LiveData<EstadoMenu> getEstado() {
        return estado;
    }

    /** Lista completa, sin filtrar: la usan el formulario y el chequeo de duplicados. */
    public List<Platillo> getTodosLosPlatillos() {
        return new ArrayList<>(platillosActuales);
    }

    public List<Categoria> getTodasLasCategorias() {
        return new ArrayList<>(categoriasActuales);
    }

    /** Pide al repositorio que drene el outbox y baje el delta (Plan Fase 2b, §5.6). */
    public void sincronizar() {
        repositorio.sincronizar();
    }

    // ------------------------------------------------------------------ fuentes de datos

    private void cuandoLleganPlatillos(List<Platillo> platillos) {
        platillosActuales = platillos;
        recalcular();
    }

    private void cuandoLleganCategorias(List<Categoria> categorias) {
        categoriasActuales = categorias;
        recalcular();
    }

    private void cuandoCambiaSincronizacion(EstadoSincronizacion estadoSync) {
        // El flanco de bajada (estaba sincronizando y dejó de estarlo) es lo que marca que
        // ya hubo una pasada completa. No alcanza con mirar isSincronizando()==false: ese
        // es también el valor inicial, antes de que el worker haya corrido siquiera.
        if (sincronizando && !estadoSync.isSincronizando()) {
            primeraSincronizacionHecha = true;
        }
        sincronizando = estadoSync.isSincronizando();
        ultimoErrorSync = estadoSync.getUltimoError();
        recalcular();
    }

    // ------------------------------------------------------------------ filtros

    public void filtrarPorCategoria(int idCategoria) {
        filtroCategoria = idCategoria;
        recalcular();
    }

    public void buscar(String texto) {
        textoBusqueda = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
        recalcular();
    }

    // ------------------------------------------------------------------ platillos

    public void crearPlatillo(NuevoPlatillo nuevo, @Nullable ImagenPlatillo imagen) {
        executor.execute(() -> {
            Result<Long> resultado = repositorio.crearPlatillo(nuevo, imagen);
            if (resultado.isSuccess()) {
                descartarFiltroQueEsconde(nuevo.getIdCategoria());
                terminarConExito(PLATILLO_CREADO);
            } else {
                estado.postValue(EstadoMenu.error(resultado.getError()));
            }
        });
    }

    public void actualizarPlatillo(int idLocal, NuevoPlatillo datos,
                                   @Nullable ImagenPlatillo imagenNueva) {
        executor.execute(() -> {
            Result<Void> resultado = repositorio.actualizarPlatillo(idLocal, datos, imagenNueva);
            if (resultado.isSuccess()) {
                descartarFiltroQueEsconde(datos.getIdCategoria());
                terminarConExito(PLATILLO_ACTUALIZADO);
            } else {
                estado.postValue(EstadoMenu.error(resultado.getError()));
            }
        });
    }

    /**
     * Suelta el filtro por categoría cuando dejaría fuera de la lista al platillo que se
     * acaba de guardar.
     *
     * <p>Sin esto, mover un platillo de categoría con un chip activo lo hacía desaparecer
     * de la pantalla: la base local guardaba bien, pero el usuario veía el platillo
     * esfumarse y lo leía como que no se había guardado. Mismo criterio que ya aplica
     * {@link #borrarCategoria(int)} cuando el filtro apunta a algo que ya no está: un
     * filtro que esconde el resultado de la acción recién hecha es un filtro obsoleto.</p>
     */
    private void descartarFiltroQueEsconde(int idCategoriaDelPlatillo) {
        if (filtroCategoria != EstadoMenu.SIN_FILTRO
                && filtroCategoria != idCategoriaDelPlatillo) {
            filtroCategoria = EstadoMenu.SIN_FILTRO;
        }
    }

    public void quitarImagen(int idLocal) {
        executor.execute(() -> terminarConExitoOError(
                repositorio.quitarImagen(idLocal), FOTO_QUITADA));
    }

    public void cambiarEstadoPlatillo(int idLocal, boolean activo) {
        executor.execute(() -> terminarConExitoOError(
                repositorio.cambiarEstadoPlatillo(idLocal, activo),
                activo ? PLATILLO_ACTIVADO : PLATILLO_DESACTIVADO));
    }

    // ------------------------------------------------------------------ categorías

    public void crearCategoria(String descripcion) {
        executor.execute(() -> {
            Result<Long> resultado = repositorio.crearCategoria(descripcion);
            if (resultado.isSuccess()) {
                terminarConExito(CATEGORIA_CREADA);
            } else {
                estado.postValue(EstadoMenu.error(resultado.getError()));
            }
        });
    }

    public void renombrarCategoria(int idLocal, String descripcion) {
        executor.execute(() -> terminarConExitoOError(
                repositorio.renombrarCategoria(idLocal, descripcion), CATEGORIA_RENOMBRADA));
    }

    public void cambiarEstadoCategoria(int idLocal, boolean activo) {
        executor.execute(() -> terminarConExitoOError(
                repositorio.cambiarEstadoCategoria(idLocal, activo),
                activo ? CATEGORIA_ACTIVADA : CATEGORIA_DESACTIVADA));
    }

    public void borrarCategoria(int idLocal) {
        executor.execute(() -> {
            Result<Void> resultado = repositorio.borrarCategoria(idLocal);
            if (!resultado.isSuccess()) {
                estado.postValue(EstadoMenu.error(resultado.getError()));
                return;
            }
            // Si la categoría borrada era la del filtro, el filtro ya no apunta a nada.
            if (filtroCategoria == idLocal) {
                filtroCategoria = EstadoMenu.SIN_FILTRO;
            }
            terminarConExito(CATEGORIA_BORRADA);
        });
    }

    /** Marca el aviso como mostrado para que no se repita al rotar la pantalla. */
    public void onMensajeConsumido() {
        EstadoMenu actual = estado.getValue();
        if (actual != null && actual.getMensajeExito() != null) {
            estado.setValue(actual.sinMensaje());
        }
    }

    // ------------------------------------------------------------------ interno

    private void terminarConExito(String mensajeExito) {
        estado.postValue(estadoConDatos().conMensaje(mensajeExito));
    }

    private void terminarConExitoOError(Result<Void> resultado, String mensajeExito) {
        if (resultado.isSuccess()) {
            terminarConExito(mensajeExito);
        } else {
            estado.postValue(EstadoMenu.error(resultado.getError()));
        }
    }

    private void recalcular() {
        estado.setValue(estadoConDatos());
    }

    private EstadoMenu estadoConDatos() {
        return EstadoMenu.conDatos(filtrados(), new ArrayList<>(categoriasActuales),
                filtroCategoria, textoBusqueda, sincronizando, cambiosSinSubir(), ultimoErrorSync,
                !primeraSincronizacionHecha);
    }

    private List<Platillo> filtrados() {
        List<Platillo> resultado = new ArrayList<>();
        for (Platillo platillo : platillosActuales) {
            if (coincideCategoria(platillo) && coincideBusqueda(platillo)) {
                resultado.add(platillo);
            }
        }
        return resultado;
    }

    private boolean coincideCategoria(Platillo platillo) {
        return filtroCategoria == EstadoMenu.SIN_FILTRO
                || platillo.getIdCategoria() == filtroCategoria;
    }

    private boolean coincideBusqueda(Platillo platillo) {
        if (textoBusqueda.isEmpty()) {
            return true;
        }
        String descripcion = platillo.getDescripcion() == null ? "" : platillo.getDescripcion();
        return platillo.getNombre().toLowerCase(Locale.ROOT).contains(textoBusqueda)
                || descripcion.toLowerCase(Locale.ROOT).contains(textoBusqueda);
    }

    /**
     * Cuenta las filas locales que todavía no llegaron al servidor (Plan Fase 2b, §4.5):
     * platillos y categorías cuyo {@code estadoSync} no es {@link EstadoSync#SINCRONIZADO}.
     * Se deriva acá y no en el repositorio para que no haya dos fuentes de verdad.
     */
    private int cambiosSinSubir() {
        int cambios = 0;
        for (Platillo platillo : platillosActuales) {
            if (platillo.getEstadoSync() != EstadoSync.SINCRONIZADO) {
                cambios++;
            }
        }
        for (Categoria categoria : categoriasActuales) {
            if (categoria.getEstadoSync() != EstadoSync.SINCRONIZADO) {
                cambios++;
            }
        }
        return cambios;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
