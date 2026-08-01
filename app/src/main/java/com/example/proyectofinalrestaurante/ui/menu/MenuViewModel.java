package com.example.proyectofinalrestaurante.ui.menu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.model.Categoria;
import com.example.proyectofinalrestaurante.domain.model.ImagenPlatillo;
import com.example.proyectofinalrestaurante.domain.model.NuevoPlatillo;
import com.example.proyectofinalrestaurante.domain.model.Platillo;
import com.example.proyectofinalrestaurante.domain.repository.MenuRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * ViewModel del módulo Menú (Plan Fase 2a, E5).
 *
 * <p>El {@link ExecutorService} se inyecta por constructor — no se crea acá dentro, para
 * no replicar la deuda <b>P-005</b> del login.</p>
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
    private final MutableLiveData<EstadoMenu> estado = new MutableLiveData<>(EstadoMenu.inicial());

    /** Listas completas recibidas del servidor; lo publicado son los platillos filtrados. */
    private final List<Platillo> todosLosPlatillos = new ArrayList<>();
    private final List<Categoria> todasLasCategorias = new ArrayList<>();
    private int filtroCategoria = EstadoMenu.SIN_FILTRO;
    private String textoBusqueda = "";

    public MenuViewModel(@NonNull MenuRepository repositorio, @NonNull ExecutorService executor) {
        this.repositorio = repositorio;
        this.executor = executor;
    }

    public LiveData<EstadoMenu> getEstado() {
        return estado;
    }

    /** Lista completa, sin filtrar: la usan el formulario y el chequeo de duplicados. */
    public List<Platillo> getTodosLosPlatillos() {
        return new ArrayList<>(todosLosPlatillos);
    }

    public List<Categoria> getTodasLasCategorias() {
        return new ArrayList<>(todasLasCategorias);
    }

    public void cargar() {
        estado.setValue(EstadoMenu.cargando());
        executor.execute(() -> {
            Result<Void> lectura = releer();
            if (lectura.isSuccess()) {
                estado.postValue(estadoConDatos());
            } else {
                estado.postValue(EstadoMenu.error(lectura.getError()));
            }
        });
    }

    // ------------------------------------------------------------------ filtros

    public void filtrarPorCategoria(int idCategoria) {
        filtroCategoria = idCategoria;
        estado.setValue(estadoConDatos());
    }

    public void buscar(String texto) {
        textoBusqueda = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
        estado.setValue(estadoConDatos());
    }

    // ------------------------------------------------------------------ platillos

    public void crearPlatillo(NuevoPlatillo nuevo, @Nullable ImagenPlatillo imagen) {
        estado.setValue(EstadoMenu.cargando());
        executor.execute(() -> {
            Result<Platillo> resultado = repositorio.crearPlatillo(nuevo, imagen);
            if (resultado.isSuccess()) {
                descartarFiltroQueEsconde(nuevo.getIdCategoria());
                recargarCon(PLATILLO_CREADO);
            } else {
                estado.postValue(EstadoMenu.error(resultado.getError()));
            }
        });
    }

    public void actualizarPlatillo(Platillo platillo, @Nullable ImagenPlatillo imagenNueva) {
        estado.setValue(EstadoMenu.cargando());
        executor.execute(() -> {
            Result<Void> resultado = repositorio.actualizarPlatillo(platillo, imagenNueva);
            if (resultado.isSuccess()) {
                descartarFiltroQueEsconde(platillo.getIdCategoria());
                recargarCon(PLATILLO_ACTUALIZADO);
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
     * de la pantalla: el servidor guardaba bien y respondía 204, pero el usuario veía el
     * platillo esfumarse y lo leía como que no se había guardado. Mismo criterio que ya
     * aplica {@link #borrarCategoria(int)} cuando el filtro apunta a algo que ya no está:
     * un filtro que esconde el resultado de la acción recién hecha es un filtro obsoleto.</p>
     */
    private void descartarFiltroQueEsconde(int idCategoriaDelPlatillo) {
        if (filtroCategoria != EstadoMenu.SIN_FILTRO
                && filtroCategoria != idCategoriaDelPlatillo) {
            filtroCategoria = EstadoMenu.SIN_FILTRO;
        }
    }

    public void quitarImagen(Platillo platillo) {
        estado.setValue(EstadoMenu.cargando());
        executor.execute(() -> ejecutar(repositorio.quitarImagen(platillo), FOTO_QUITADA));
    }

    public void cambiarEstadoPlatillo(Platillo platillo, boolean activo) {
        estado.setValue(EstadoMenu.cargando());
        executor.execute(() -> ejecutar(
                repositorio.cambiarEstadoPlatillo(platillo.getIdPlatillo(), activo),
                activo ? PLATILLO_ACTIVADO : PLATILLO_DESACTIVADO));
    }

    // ------------------------------------------------------------------ categorías

    public void crearCategoria(String descripcion) {
        estado.setValue(EstadoMenu.cargando());
        executor.execute(() -> {
            Result<Categoria> resultado = repositorio.crearCategoria(descripcion);
            if (resultado.isSuccess()) {
                recargarCon(CATEGORIA_CREADA);
            } else {
                estado.postValue(EstadoMenu.error(resultado.getError()));
            }
        });
    }

    public void renombrarCategoria(int idCategoria, String descripcion) {
        estado.setValue(EstadoMenu.cargando());
        executor.execute(() -> ejecutar(
                repositorio.renombrarCategoria(idCategoria, descripcion), CATEGORIA_RENOMBRADA));
    }

    public void cambiarEstadoCategoria(int idCategoria, boolean activo) {
        estado.setValue(EstadoMenu.cargando());
        executor.execute(() -> ejecutar(
                repositorio.cambiarEstadoCategoria(idCategoria, activo),
                activo ? CATEGORIA_ACTIVADA : CATEGORIA_DESACTIVADA));
    }

    public void borrarCategoria(int idCategoria) {
        estado.setValue(EstadoMenu.cargando());
        executor.execute(() -> {
            Result<Void> resultado = repositorio.borrarCategoria(idCategoria);
            if (!resultado.isSuccess()) {
                estado.postValue(EstadoMenu.error(resultado.getError()));
                return;
            }
            // Si la categoría borrada era la del filtro, el filtro ya no apunta a nada.
            if (filtroCategoria == idCategoria) {
                filtroCategoria = EstadoMenu.SIN_FILTRO;
            }
            recargarCon(CATEGORIA_BORRADA);
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

    private void ejecutar(Result<Void> resultado, String mensajeExito) {
        if (resultado.isSuccess()) {
            recargarCon(mensajeExito);
        } else {
            estado.postValue(EstadoMenu.error(resultado.getError()));
        }
    }

    /**
     * Tras una operación exitosa se vuelve a leer del servidor en vez de retocar las
     * listas en memoria: así lo que se ve es lo que la base realmente aceptó, con los
     * triggers y los contadores de las vistas ya aplicados.
     */
    private void recargarCon(String mensajeExito) {
        Result<Void> relectura = releer();
        if (relectura.isSuccess()) {
            estado.postValue(estadoConDatos().conMensaje(mensajeExito));
        } else {
            estado.postValue(EstadoMenu.error(relectura.getError()));
        }
    }

    /** Relee platillos y categorías. El primer error corta: la pantalla necesita ambos. */
    private Result<Void> releer() {
        Result<List<Platillo>> platillos = repositorio.listarPlatillos();
        if (!platillos.isSuccess()) {
            return Result.fail(platillos.getError());
        }
        Result<List<Categoria>> categorias = repositorio.listarCategorias();
        if (!categorias.isSuccess()) {
            return Result.fail(categorias.getError());
        }
        todosLosPlatillos.clear();
        todosLosPlatillos.addAll(platillos.getValue());
        todasLasCategorias.clear();
        todasLasCategorias.addAll(categorias.getValue());
        return Result.ok(null);
    }

    private EstadoMenu estadoConDatos() {
        return EstadoMenu.conDatos(filtrados(), new ArrayList<>(todasLasCategorias),
                filtroCategoria, textoBusqueda);
    }

    private List<Platillo> filtrados() {
        List<Platillo> resultado = new ArrayList<>();
        for (Platillo platillo : todosLosPlatillos) {
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

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
