package com.example.proyectofinalrestaurante.ui.nuevopedido;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.model.Mesa;
import com.example.proyectofinalrestaurante.domain.model.TipoPedido;
import com.example.proyectofinalrestaurante.ui.pedidos.PedidosFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;

/**
 * Pantalla de toma del pedido (Plan Fase 3b, E8). Reemplaza el contenido del tablero cuando se
 * pulsa el FAB de {@code PedidosFragment}. Un solo {@link NuevoPedidoViewModel} compartido —las
 * cuatro hojas (carrito y los tres selectores) reciben el mismo por {@code recibir(vm)}— de modo
 * que el carrito sobrevive mientras se navega entre ellas.
 *
 * <p>Sigue el patrón de los Dialogs de pedidos: no navega con NavController, sino que vuelve al
 * tablero reemplazando el fragmento del contenedor como hace {@code MainActivity.mostrarModulo()}.</p>
 */
public class NuevoPedidoFragment extends Fragment {

    private NuevoPedidoViewModel viewModel;

    private ProgressBar progreso;
    private TextView mensajePermiso;
    private TextView mesaSeleccion;
    private TextView clienteSeleccion;
    private TextView resumenCarrito;
    private TextView total;
    private ChipGroup grupoTipo;
    private Chip chipEnMesa;
    private Chip chipParaLlevar;
    private MaterialButton confirmar;
    private MaterialButton agregarPlatillos;
    private MaterialButton cambiarMesa;
    private MaterialButton cambiarCliente;
    private MaterialButton verCarrito;

    private int chipsConstruidos = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_nuevo_pedido, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        progreso = view.findViewById(R.id.progreso_nuevo_pedido);
        mensajePermiso = view.findViewById(R.id.txt_mensaje_permiso);
        mesaSeleccion = view.findViewById(R.id.txt_nueva_mesa);
        clienteSeleccion = view.findViewById(R.id.txt_nuevo_cliente);
        resumenCarrito = view.findViewById(R.id.txt_resumen_carrito);
        total = view.findViewById(R.id.txt_total_nuevo_pedido);
        grupoTipo = view.findViewById(R.id.grupo_tipo_pedido);
        confirmar = view.findViewById(R.id.btn_confirmar_pedido);
        agregarPlatillos = view.findViewById(R.id.btn_agregar_platillos);
        cambiarMesa = view.findViewById(R.id.btn_cambiar_mesa);
        cambiarCliente = view.findViewById(R.id.btn_cambiar_cliente);
        verCarrito = view.findViewById(R.id.btn_ver_carrito);

        viewModel = new ViewModelProvider(this,
                new NuevoPedidoViewModelFactory(requireActivity().getApplication()))
                .get(NuevoPedidoViewModel.class);

        cambiarMesa.setOnClickListener(v -> abrirSelectorMesa());
        cambiarCliente.setOnClickListener(v -> abrirSelectorCliente());
        verCarrito.setOnClickListener(v -> abrirCarrito());
        agregarPlatillos.setOnClickListener(v -> abrirSelectorPlatillo());
        confirmar.setOnClickListener(
                v -> viewModel.confirmar(getString(R.string.nuevo_pedido_creado)));

        viewModel.getEstado().observe(getViewLifecycleOwner(), this::render);
    }

    private void render(EstadoNuevoPedido estado) {
        asegurarChipsTipo();
        progreso.setVisibility(estado.isCargando() ? View.VISIBLE : View.GONE);

        boolean puede = estado.isPuedeTomarPedido();
        mensajePermiso.setVisibility(puede ? View.GONE : View.VISIBLE);
        confirmar.setEnabled(puede && estado.isPuedeConfirmar());
        agregarPlatillos.setEnabled(puede);
        cambiarMesa.setEnabled(puede);
        cambiarCliente.setEnabled(puede);
        verCarrito.setEnabled(puede);

        mesaSeleccion.setText(estado.getIdLocalMesa() == null
                ? getString(R.string.nuevo_pedido_sin_mesa)
                : getString(R.string.nuevo_pedido_mesa_numero,
                        numeroDeMesa(estado)));
        clienteSeleccion.setText(estado.getIdLocalCliente() == null
                ? getString(R.string.nuevo_pedido_sin_cliente)
                : nombreDeCliente(estado));

        marcarTipoCarrito(estado.getTipoPedido());
        pintarCarrito(estado);

        String error = estado.getError();
        if (error != null) {
            Snackbar.make(requireView(), error, Snackbar.LENGTH_LONG).show();
            viewModel.onErrorConsumido();
        }
        if (estado.getErrorDeValidacion() != null) {
            Snackbar.make(requireView(), ErrorNuevoPedidoUi.mensaje(estado.getErrorDeValidacion()),
                    Snackbar.LENGTH_LONG).show();
            viewModel.onErrorDeValidacionConsumido();
        }
        String exito = estado.getMensajeExito();
        if (exito != null) {
            Snackbar.make(requireView(), exito, Snackbar.LENGTH_SHORT).show();
            viewModel.onMensajeConsumido();
            volverAlTablero();
        }
    }

    private void pintarCarrito(EstadoNuevoPedido estado) {
        int cantidad = estado.getCarrito().cantidadItems();
        resumenCarrito.setText(estado.isCarritoVacio()
                ? getResources().getQuantityString(R.plurals.nuevo_pedido_items, 0, 0)
                : getResources().getQuantityString(R.plurals.nuevo_pedido_items,
                        cantidad, cantidad));
        total.setText(getString(R.string.formato_lempiras, estado.getCarrito().total()));
    }

    private String numeroDeMesa(EstadoNuevoPedido estado) {
        for (Mesa mesa : estado.getMesas()) {
            if (mesa.getIdLocal() == estado.getIdLocalMesa()) {
                return String.valueOf(mesa.getNumeroMesa());
            }
        }
        return String.valueOf(estado.getIdLocalMesa());
    }

    private CharSequence nombreDeCliente(EstadoNuevoPedido estado) {
        for (com.example.proyectofinalrestaurante.domain.model.Cliente cliente
                : estado.getClientes()) {
            if (cliente.getIdLocal() == estado.getIdLocalCliente()) {
                return cliente.nombreCompleto();
            }
        }
        return String.valueOf(estado.getIdLocalCliente());
    }

    /** Construye los chips de tipo de pedido una sola vez (En mesa / Para llevar). */
    private void asegurarChipsTipo() {
        if (chipsConstruidos >= 0) {
            return;
        }
        chipsConstruidos = 0;
        chipEnMesa = crearChip(TipoPedidoUi.etiqueta(TipoPedido.EN_MESA), TipoPedido.EN_MESA);
        chipParaLlevar = crearChip(TipoPedidoUi.etiqueta(TipoPedido.PARA_LLEVAR),
                TipoPedido.PARA_LLEVAR);
        grupoTipo.addView(chipEnMesa);
        grupoTipo.addView(chipParaLlevar);
    }

    private Chip crearChip(int etiquetaRes, TipoPedido tipo) {
        Chip chip = new Chip(requireContext());
        chip.setText(etiquetaRes);
        chip.setCheckable(true);
        chip.setMinHeight(getResources().getDimensionPixelSize(R.dimen.altura_minima_tactil));
        chip.setOnClickListener(v -> viewModel.seleccionarTipo(tipo));
        return chip;
    }

    private void marcarTipoCarrito(TipoPedido tipoDePedido) {
        if (chipEnMesa == null || chipParaLlevar == null) {
            return;
        }
        chipEnMesa.setChecked(tipoDePedido == TipoPedido.EN_MESA);
        chipParaLlevar.setChecked(tipoDePedido == TipoPedido.PARA_LLEVAR);
    }

    // ------------------------------------------------------------------ hojas

    private void abrirCarrito() {
        CarritoHoja hoja = new CarritoHoja();
        hoja.recibir(viewModel);
        hoja.show(getParentFragmentManager(), CarritoHoja.TAG);
    }

    private void abrirSelectorPlatillo() {
        SelectorPlatilloHoja hoja = new SelectorPlatilloHoja();
        hoja.recibir(viewModel);
        hoja.show(getParentFragmentManager(), SelectorPlatilloHoja.TAG);
    }

    private void abrirSelectorMesa() {
        SelectorMesaHoja hoja = new SelectorMesaHoja();
        hoja.recibir(viewModel);
        hoja.show(getParentFragmentManager(), SelectorMesaHoja.TAG);
    }

    private void abrirSelectorCliente() {
        SelectorClienteHoja hoja = new SelectorClienteHoja();
        hoja.recibir(viewModel);
        hoja.show(getParentFragmentManager(), SelectorClienteHoja.TAG);
    }

    /** Regresa al tablero reemplazando el fragmento, igual que cambia de módulo MainActivity. */
    private void volverAlTablero() {
        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.contenedor_contenido, new PedidosFragment())
                .commit();
    }
}