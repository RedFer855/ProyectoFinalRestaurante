package com.example.proyectofinalrestaurante.ui.menu;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.domain.model.Categoria;
import com.example.proyectofinalrestaurante.domain.model.Platillo;

import java.util.Collections;
import java.util.List;

/**
 * Estado único e inmutable de la pantalla de Menú (Plan Fase 2a, E5; extendido en 2b, E6).
 *
 * <p>Modela los <b>cuatro</b> estados reales — cargando, con datos, vacío y error — con
 * {@link #isVacio()} derivado en vez de guardado como bandera: una bandera suelta puede
 * terminar contradiciendo a la lista. Misma forma que {@code EstadoEmpleados}.</p>
 *
 * <p>Lleva platillos <b>y</b> categorías porque la pantalla necesita las dos cosas a la
 * vez: los chips del filtro y el selector del formulario salen de las categorías. Y lleva
 * el filtro y la búsqueda aplicados para que el Fragment pueda repintar los chips en el
 * estado correcto después de una rotación, sin guardar nada por su cuenta.</p>
 *
 * <p>Desde 2b también modela la sincronización (Plan Fase 2b, §4.5): {@code sincronizando}
 * alimenta el indicador global, {@code cambiosSinSubir} es el conteo de filas locales que
 * todavía no llegaron al servidor y {@code ultimoErrorSync} se muestra cuando una subida
 * falló de forma permanente. Los tres llegan al Fragment juntos con los datos: una sola
 * fuente de verdad por pantalla.</p>
 */
public final class EstadoMenu {

    /** Valor de {@code filtroCategoria} cuando el filtro es "Todos". */
    public static final int SIN_FILTRO = 0;

    private final boolean cargando;
    private final List<Platillo> platillos;
    private final List<Categoria> categorias;
    private final int filtroCategoria;
    private final String textoBusqueda;
    @Nullable private final String error;
    /** Evento de un solo disparo: se consume con {@code sinMensaje()} tras mostrarlo. */
    @Nullable private final String mensajeExito;
    private final boolean sincronizando;
    private final int cambiosSinSubir;
    @Nullable private final String ultimoErrorSync;

    private EstadoMenu(boolean cargando, List<Platillo> platillos, List<Categoria> categorias,
                       int filtroCategoria, String textoBusqueda,
                       @Nullable String error, @Nullable String mensajeExito,
                       boolean sincronizando, int cambiosSinSubir,
                       @Nullable String ultimoErrorSync) {
        this.cargando = cargando;
        this.platillos = platillos == null ? Collections.emptyList()
                : Collections.unmodifiableList(platillos);
        this.categorias = categorias == null ? Collections.emptyList()
                : Collections.unmodifiableList(categorias);
        this.filtroCategoria = filtroCategoria;
        this.textoBusqueda = textoBusqueda == null ? "" : textoBusqueda;
        this.error = error;
        this.mensajeExito = mensajeExito;
        this.sincronizando = sincronizando;
        this.cambiosSinSubir = cambiosSinSubir;
        this.ultimoErrorSync = ultimoErrorSync;
    }

    public static EstadoMenu inicial() {
        return new EstadoMenu(false, Collections.emptyList(), Collections.emptyList(),
                SIN_FILTRO, "", null, null, false, 0, null);
    }

    public static EstadoMenu cargando() {
        return new EstadoMenu(true, Collections.emptyList(), Collections.emptyList(),
                SIN_FILTRO, "", null, null, false, 0, null);
    }

    public static EstadoMenu conDatos(List<Platillo> platillos, List<Categoria> categorias,
                                      int filtroCategoria, String textoBusqueda,
                                      boolean sincronizando, int cambiosSinSubir,
                                      @Nullable String ultimoErrorSync) {
        return new EstadoMenu(false, platillos, categorias, filtroCategoria, textoBusqueda,
                null, null, sincronizando, cambiosSinSubir, ultimoErrorSync);
    }

    public static EstadoMenu error(String mensaje) {
        return new EstadoMenu(false, Collections.emptyList(), Collections.emptyList(),
                SIN_FILTRO, "", mensaje, null, false, 0, null);
    }

    /** Copia con un aviso de éxito pendiente de mostrar. */
    public EstadoMenu conMensaje(String mensaje) {
        return new EstadoMenu(cargando, platillos, categorias, filtroCategoria, textoBusqueda,
                error, mensaje, sincronizando, cambiosSinSubir, ultimoErrorSync);
    }

    /** Copia sin el aviso: se llama después de mostrarlo, para que no se repita. */
    public EstadoMenu sinMensaje() {
        return new EstadoMenu(cargando, platillos, categorias, filtroCategoria, textoBusqueda,
                error, null, sincronizando, cambiosSinSubir, ultimoErrorSync);
    }

    public boolean isCargando() {
        return cargando;
    }

    /** Los platillos ya filtrados por categoría y búsqueda: es lo que se pinta. */
    public List<Platillo> getPlatillos() {
        return platillos;
    }

    public List<Categoria> getCategorias() {
        return categorias;
    }

    public int getFiltroCategoria() {
        return filtroCategoria;
    }

    public String getTextoBusqueda() {
        return textoBusqueda;
    }

    @Nullable
    public String getError() {
        return error;
    }

    @Nullable
    public String getMensajeExito() {
        return mensajeExito;
    }

    public boolean isSincronizando() {
        return sincronizando;
    }

    public int getCambiosSinSubir() {
        return cambiosSinSubir;
    }

    @Nullable
    public String getUltimoErrorSync() {
        return ultimoErrorSync;
    }

    /** No hay nada que mostrar, pero tampoco es un error ni está cargando. */
    public boolean isVacio() {
        return !cargando && error == null && platillos.isEmpty();
    }

    /** El vacío es por el filtro o la búsqueda, no porque el menú esté realmente vacío. */
    public boolean isVacioPorFiltro() {
        return isVacio() && (filtroCategoria != SIN_FILTRO || !textoBusqueda.isEmpty());
    }
}
