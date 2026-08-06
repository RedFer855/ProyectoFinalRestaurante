package com.example.proyectofinalrestaurante.ui.nuevopedido;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.example.proyectofinalrestaurante.domain.ReglasPedido;
import com.example.proyectofinalrestaurante.domain.Result;
import com.example.proyectofinalrestaurante.domain.ValidadorPedido;
import com.example.proyectofinalrestaurante.domain.model.Carrito;
import com.example.proyectofinalrestaurante.domain.model.Cliente;
import com.example.proyectofinalrestaurante.domain.model.EstadoMesa;
import com.example.proyectofinalrestaurante.domain.model.Mesa;
import com.example.proyectofinalrestaurante.domain.model.NuevoPedido;
import com.example.proyectofinalrestaurante.domain.model.Platillo;
import com.example.proyectofinalrestaurante.domain.model.TipoPedido;
import com.example.proyectofinalrestaurante.domain.repository.ClienteRepository;
import com.example.proyectofinalrestaurante.domain.repository.MenuRepository;
import com.example.proyectofinalrestaurante.domain.repository.MesaRepository;
import com.example.proyectofinalrestaurante.domain.repository.PedidoRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * ViewModel de la toma del pedido (Plan Fase 3b, E7). Mismo patrón que {@code PedidosViewModel}:
 * {@link ExecutorService} inyectado (P-005), fuentes de Room vía {@link LiveData} y escritura
 * optimista por el outbox.
 *
 * <p>Los selectores de mesa y cliente leen de Room (módulos 2c/2d, ya existentes): no hay red en
 * el camino de tomar un pedido. El carrito vive <b>acá</b>, en el ViewModel, para sobrevivir a una
 * rotación de pantalla; {@code confirmar()} lo valida con {@link ValidadorPedido} y, si pasa,
 * arma el {@link NuevoPedido} con la clave de idempotencia generada por {@link UUID} y llama a
 * {@code repositorio.crear()}.</p>
 */
public class NuevoPedidoViewModel extends ViewModel {

    private final PedidoRepository pedidoRepositorio;
    private final MenuRepository menuRepositorio;
    private final MesaRepository mesaRepositorio;
    private final ClienteRepository clienteRepositorio;
    private final ExecutorService executor;

    private final MediatorLiveData<EstadoNuevoPedido> estado = new MediatorLiveData<>();

    private Carrito carrito = Carrito.vacio();
    private TipoPedido tipoPedido = TipoPedido.EN_MESA;
    @Nullable private Integer idLocalMesa = null;
    @Nullable private Integer idLocalCliente = null;
    private List<Platillo> platillos = Collections.emptyList();
    private List<Mesa> mesas = Collections.emptyList();
    private List<Cliente> clientes = Collections.emptyList();
    @Nullable private String errorDeOperacion = null;
    @Nullable private String mensajeExito = null;
    @Nullable private ValidadorPedido.ErrorPedido errorDeValidacion = null;

    private final boolean puedeTomarPedido;

    public NuevoPedidoViewModel(@NonNull PedidoRepository pedidoRepositorio,
                                @NonNull MenuRepository menuRepositorio,
                                @NonNull MesaRepository mesaRepositorio,
                                @NonNull ClienteRepository clienteRepositorio,
                                @NonNull ExecutorService executor,
                                @Nullable String rol) {
        this.pedidoRepositorio = pedidoRepositorio;
        this.menuRepositorio = menuRepositorio;
        this.mesaRepositorio = mesaRepositorio;
        this.clienteRepositorio = clienteRepositorio;
        this.executor = executor;
        this.puedeTomarPedido = ReglasPedido.puedeTomarPedido(rol);
        estado.addSource(menuRepositorio.observarPlatillos(), this::cuandoLleganPlatillos);
        estado.addSource(mesaRepositorio.observarMesas(), this::cuandoLleganMesas);
        estado.addSource(clienteRepositorio.observarClientes(), this::cuandoLleganClientes);
        estado.setValue(EstadoNuevoPedido.cargando(puedeTomarPedido));
    }

    public LiveData<EstadoNuevoPedido> getEstado() {
        return estado;
    }

    // ------------------------------------------------------------------ carrito

    /** Agrega un platillo al carrito (suma 1 a su línea si ya está — B6). */
    public void agregarPlatillo(@Nullable Platillo platillo) {
        carrito = carrito.con(platillo);
        recalcular();
    }

    /** Fija la cantidad de una línea; {@code n <= 0} la elimina (B6). */
    public void cambiarCantidad(int idLocalPlatillo, int n) {
        carrito = carrito.conCantidad(idLocalPlatillo, n);
        recalcular();
    }

    /** Quita una línea del carrito. */
    public void quitarPlatillo(int idLocalPlatillo) {
        carrito = carrito.sinPlatillo(idLocalPlatillo);
        recalcular();
    }

    // ------------------------------------------------------------------ selectores

    public void seleccionarTipo(@Nullable TipoPedido tipo) {
        if (tipo != null) {
            tipoPedido = tipo;
            recalcular();
        }
    }

    public void seleccionarMesa(@Nullable Integer idLocalMesa) {
        this.idLocalMesa = idLocalMesa;
        recalcular();
    }

    public void seleccionarCliente(@Nullable Integer idLocalCliente) {
        this.idLocalCliente = idLocalCliente;
        recalcular();
    }

    // ------------------------------------------------------------------ confirmar

    /**
     * Confirma el pedido: valida el carrito y, si es válido, encola la creación en el executor.
     * La clave de idempotencia se genera acá ({@code UUID}) para que el reintento del outbox mande
     * la misma y el servidor no duplique (Plan Fase 3b, §2). La plantilla del mensaje de éxito la
     * resuelve la UI ({@code strings.xml}) y se pasa por parámetro (P-001: el ViewModel no tiene
     * {@code Context}).
     */
    public void confirmar(String plantillaExito) {
        executor.execute(() -> {
            NuevoPedido nuevo = new NuevoPedido(carrito, idLocalMesa, idLocalCliente, tipoPedido,
                    UUID.randomUUID().toString());
            Set<ValidadorPedido.ErrorPedido> errores = ValidadorPedido.validar(nuevo);
            if (!errores.isEmpty()) {
                publicarErrorDeValidacion(primerError(errores));
                return;
            }
            Result<Long> resultado = pedidoRepositorio.crear(nuevo);
            if (resultado.isSuccess()) {
                publicarExito(String.format(Locale.ROOT, plantillaExito, resultado.getValue()));
            } else {
                publicarError(resultado.getError());
            }
        });
    }

    // ------------------------------------------------------------------ fuentes de datos

    private void cuandoLleganPlatillos(List<Platillo> platillos) {
        this.platillos = filtrarPedibles(platillos);
        recalcular();
    }

    private void cuandoLleganMesas(List<Mesa> mesas) {
        this.mesas = filtrarDisponibles(mesas);
        recalcular();
    }

    private void cuandoLleganClientes(List<Cliente> clientes) {
        this.clientes = clientes == null ? Collections.emptyList() : clientes;
        recalcular();
    }

    // ------------------------------------------------------------------ mensajes

    public void onMensajeConsumido() {
        mensajeExito = null;
        EstadoNuevoPedido actual = estado.getValue();
        if (actual != null && actual.getMensajeExito() != null) {
            estado.setValue(actual.sinMensaje());
        }
    }

    public void onErrorConsumido() {
        errorDeOperacion = null;
        recalcular();
    }

    public void onErrorDeValidacionConsumido() {
        errorDeValidacion = null;
        recalcular();
    }

    private void publicarExito(String mensaje) {
        mensajeExito = mensaje;
        errorDeOperacion = null;
        errorDeValidacion = null;
        estado.postValue(construirEstado());
    }

    private void publicarError(String mensaje) {
        errorDeOperacion = mensaje;
        mensajeExito = null;
        estado.postValue(construirEstado());
    }

    private void publicarErrorDeValidacion(ValidadorPedido.ErrorPedido error) {
        errorDeValidacion = error;
        mensajeExito = null;
        estado.postValue(construirEstado());
    }

    // ------------------------------------------------------------------ estado

    private void recalcular() {
        estado.setValue(construirEstado());
    }

    private EstadoNuevoPedido construirEstado() {
        EstadoNuevoPedido nuevo = EstadoNuevoPedido.conDatos(carrito, tipoPedido, idLocalMesa,
                idLocalCliente, platillos, mesas, clientes, puedeTomarPedido);
        if (errorDeValidacion != null) {
            nuevo = nuevo.conErrorDeValidacion(errorDeValidacion);
        }
        if (errorDeOperacion != null) {
            nuevo = nuevo.conError(errorDeOperacion);
        }
        if (mensajeExito != null) {
            nuevo = nuevo.conMensaje(mensajeExito);
        }
        return nuevo;
    }

    private static List<Platillo> filtrarPedibles(List<Platillo> platillos) {
        if (platillos == null) {
            return Collections.emptyList();
        }
        List<Platillo> resultado = new ArrayList<>();
        for (Platillo platillo : platillos) {
            if (ReglasPedido.puedePedirse(platillo)) {
                resultado.add(platillo);
            }
        }
        return resultado;
    }

    /**
     * El selector de mesas de un pedido nuevo solo ofrece mesas libres y dadas de alta: una
     * mesa ocupada o reservada ya tiene otro pedido en curso, y una de baja no debería
     * recibir pedidos nuevos.
     */
    private static List<Mesa> filtrarDisponibles(@Nullable List<Mesa> mesas) {
        if (mesas == null) {
            return Collections.emptyList();
        }
        List<Mesa> resultado = new ArrayList<>();
        for (Mesa mesa : mesas) {
            if (mesa.isActivo() && mesa.getEstado() == EstadoMesa.LIBRE) {
                resultado.add(mesa);
            }
        }
        return resultado;
    }

    @Nullable
    private static ValidadorPedido.ErrorPedido primerError(
            Set<ValidadorPedido.ErrorPedido> errores) {
        if (errores.contains(ValidadorPedido.ErrorPedido.CARRITO_VACIO)) {
            return ValidadorPedido.ErrorPedido.CARRITO_VACIO;
        }
        if (errores.contains(ValidadorPedido.ErrorPedido.DEMASIADAS_LINEAS)) {
            return ValidadorPedido.ErrorPedido.DEMASIADAS_LINEAS;
        }
        return ValidadorPedido.ErrorPedido.CANTIDAD_INVALIDA;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
