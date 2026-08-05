package com.example.proyectofinalrestaurante.ui.nuevopedido;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.domain.ValidadorPedido;
import com.example.proyectofinalrestaurante.domain.model.Carrito;
import com.example.proyectofinalrestaurante.domain.model.Cliente;
import com.example.proyectofinalrestaurante.domain.model.Mesa;
import com.example.proyectofinalrestaurante.domain.model.NuevoPedido;
import com.example.proyectofinalrestaurante.domain.model.Platillo;
import com.example.proyectofinalrestaurante.domain.model.TipoPedido;

import java.util.Collections;
import java.util.List;

/**
 * Estado único e inmutable de la pantalla de toma del pedido (Plan Fase 3b, E7). Mismo patrón que
 * {@code EstadoPedidos}.
 *
 * <p>Las tres listas ({@code platillos}, {@code mesas}, {@code clientes}) son las que alimentan los
 * selectores; la de platillos llega <b>filtrada</b> por {@code ReglasPedido#puedePedirse}. Los dos
 * flags son derivados, nunca banderas sueltas: {@code puedeConfirmar} exige el carrito no vacío y
 * {@code ValidadorPedido#esValido}; {@code puedeTomarPedido} sale de {@code ReglasPedido}.
 * {@code errorDeValidacion} es la clave que la UI mapea a un mensaje de {@code strings.xml} — el
 * ViewModel no tiene {@code Context} para resolverla.</p>
 */
public final class EstadoNuevoPedido {

    private final boolean cargando;
    private final Carrito carrito;
    private final TipoPedido tipoPedido;
    @Nullable private final Integer idLocalMesa;
    @Nullable private final Integer idLocalCliente;
    private final List<Platillo> platillos;
    private final List<Mesa> mesas;
    private final List<Cliente> clientes;
    private final boolean puedeConfirmar;
    private final boolean puedeTomarPedido;
    @Nullable private final ValidadorPedido.ErrorPedido errorDeValidacion;
    @Nullable private final String error;
    @Nullable private final String mensajeExito;

    private EstadoNuevoPedido(boolean cargando, Carrito carrito, TipoPedido tipoPedido,
                              @Nullable Integer idLocalMesa, @Nullable Integer idLocalCliente,
                              List<Platillo> platillos, List<Mesa> mesas, List<Cliente> clientes,
                              boolean puedeConfirmar, boolean puedeTomarPedido,
                              @Nullable ValidadorPedido.ErrorPedido errorDeValidacion,
                              @Nullable String error, @Nullable String mensajeExito) {
        this.cargando = cargando;
        this.carrito = carrito;
        this.tipoPedido = tipoPedido;
        this.idLocalMesa = idLocalMesa;
        this.idLocalCliente = idLocalCliente;
        this.platillos = platillos == null
                ? Collections.emptyList() : Collections.unmodifiableList(platillos);
        this.mesas = mesas == null ? Collections.emptyList() : Collections.unmodifiableList(mesas);
        this.clientes = clientes == null
                ? Collections.emptyList() : Collections.unmodifiableList(clientes);
        this.puedeConfirmar = puedeConfirmar;
        this.puedeTomarPedido = puedeTomarPedido;
        this.errorDeValidacion = errorDeValidacion;
        this.error = error;
        this.mensajeExito = mensajeExito;
    }

    /** Estado inicial: el carrito vacío y las listas del selector sin llegar todavía. */
    public static EstadoNuevoPedido cargando(boolean puedeTomarPedido) {
        return new EstadoNuevoPedido(true, Carrito.vacio(), TipoPedido.EN_MESA, null, null,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                false, puedeTomarPedido, null, null, null);
    }

    public static EstadoNuevoPedido conDatos(Carrito carrito, TipoPedido tipoPedido,
                                             @Nullable Integer idLocalMesa,
                                             @Nullable Integer idLocalCliente,
                                             List<Platillo> platillos, List<Mesa> mesas,
                                             List<Cliente> clientes, boolean puedeTomarPedido) {
        return new EstadoNuevoPedido(false, carrito, tipoPedido, idLocalMesa, idLocalCliente,
                platillos, mesas, clientes,
                derivarPuedeConfirmar(carrito, tipoPedido, idLocalMesa, idLocalCliente),
                puedeTomarPedido, null, null, null);
    }

    public EstadoNuevoPedido conMensaje(String mensaje) {
        return new EstadoNuevoPedido(cargando, carrito, tipoPedido, idLocalMesa, idLocalCliente,
                platillos, mesas, clientes, puedeConfirmar, puedeTomarPedido, errorDeValidacion,
                error, mensaje);
    }

    public EstadoNuevoPedido sinMensaje() {
        return new EstadoNuevoPedido(cargando, carrito, tipoPedido, idLocalMesa, idLocalCliente,
                platillos, mesas, clientes, puedeConfirmar, puedeTomarPedido, errorDeValidacion,
                error, null);
    }

    public EstadoNuevoPedido conError(String mensaje) {
        return new EstadoNuevoPedido(cargando, carrito, tipoPedido, idLocalMesa, idLocalCliente,
                platillos, mesas, clientes, puedeConfirmar, puedeTomarPedido, errorDeValidacion,
                mensaje, null);
    }

    public EstadoNuevoPedido sinError() {
        return new EstadoNuevoPedido(cargando, carrito, tipoPedido, idLocalMesa, idLocalCliente,
                platillos, mesas, clientes, puedeConfirmar, puedeTomarPedido, errorDeValidacion,
                null, mensajeExito);
    }

    public EstadoNuevoPedido conErrorDeValidacion(
            @Nullable ValidadorPedido.ErrorPedido errorDeValidacion) {
        return new EstadoNuevoPedido(cargando, carrito, tipoPedido, idLocalMesa, idLocalCliente,
                platillos, mesas, clientes, puedeConfirmar, puedeTomarPedido, errorDeValidacion,
                error, mensajeExito);
    }

    public boolean isCargando() {
        return cargando;
    }

    public Carrito getCarrito() {
        return carrito;
    }

    public TipoPedido getTipoPedido() {
        return tipoPedido;
    }

    @Nullable
    public Integer getIdLocalMesa() {
        return idLocalMesa;
    }

    @Nullable
    public Integer getIdLocalCliente() {
        return idLocalCliente;
    }

    public List<Platillo> getPlatillos() {
        return platillos;
    }

    public List<Mesa> getMesas() {
        return mesas;
    }

    public List<Cliente> getClientes() {
        return clientes;
    }

    public boolean isPuedeConfirmar() {
        return puedeConfirmar;
    }

    public boolean isPuedeTomarPedido() {
        return puedeTomarPedido;
    }

    @Nullable
    public ValidadorPedido.ErrorPedido getErrorDeValidacion() {
        return errorDeValidacion;
    }

    @Nullable
    public String getError() {
        return error;
    }

    @Nullable
    public String getMensajeExito() {
        return mensajeExito;
    }

    /** Carrito vacío — la UI lo usa para mostrar la vista "todavía no hay nada". */
    public boolean isCarritoVacio() {
        return carrito.estaVacio();
    }

    /**
     * {@code puedeConfirmar} es derivado: carrito no vacío y pedido válido. Se construye un
     * {@link NuevoPedido} con la clave de idempotencia como marcador de posición: {@code esValido}
     * no la inspecciona y así se reusa el mismo validador que {@code confirmar()} en el ViewModel.
     */
    private static boolean derivarPuedeConfirmar(Carrito carrito, TipoPedido tipoPedido,
                                                 @Nullable Integer idLocalMesa,
                                                 @Nullable Integer idLocalCliente) {
        if (carrito == null || carrito.estaVacio()) {
            return false;
        }
        return ValidadorPedido.esValido(new NuevoPedido(
                carrito, idLocalMesa, idLocalCliente, tipoPedido, ""));
    }
}
