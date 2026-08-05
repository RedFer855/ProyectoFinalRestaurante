package com.example.proyectofinalrestaurante.ui.pedidos;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.Accion;
import com.example.proyectofinalrestaurante.domain.Modulo;
import com.example.proyectofinalrestaurante.domain.model.EstadoPedido;
import com.example.proyectofinalrestaurante.domain.model.Pedido;
import com.example.proyectofinalrestaurante.ui.permisos.VistaPorPermiso;
import com.example.proyectofinalrestaurante.ui.detallepedido.DetallePedidoHoja;
import com.example.proyectofinalrestaurante.ui.detallepedido.DetallePedidoViewModel;
import com.example.proyectofinalrestaurante.ui.detallepedido.DetallePedidoViewModelFactory;
import com.example.proyectofinalrestaurante.ui.nuevopedido.NuevoPedidoFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

/**
 * Tablero de Pedidos conectado a Room + tiempo real (Plan Fase 3, E8). Sin {@code DatosMaqueta}:
 * la fuente es {@link PedidosViewModel} y su {@link PedidoRepository}, con la lista paginada
 * por <b>ventana creciente</b> (§4.5) — el scroll pide otra ventana cuando se acerca al final
 * y {@code hayMas} es cierto.
 */
public class PedidosFragment extends Fragment {

    private PedidosViewModel viewModel;
    private PedidoAdapter adapter;
    private TextView vacio;
    private ChipGroup grupoEstados;
    private int chipsConstruidos = -1;
    private boolean cargandoMas = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_pedidos, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vacio = view.findViewById(R.id.txt_pedidos_vacio);
        grupoEstados = view.findViewById(R.id.grupo_estados_pedido);

        viewModel = new ViewModelProvider(this,
                new PedidosViewModelFactory(requireActivity().getApplication()))
                .get(PedidosViewModel.class);

        adapter = new PedidoAdapter(new PedidoAdapter.AlInteractuar() {
            @Override
            public void onAvanzarEstado(Pedido pedido) {
                viewModel.avanzarEstado(pedido);
            }

            @Override
            public void onAccion(Pedido pedido, int accionId) {
                if (accionId == R.id.accion_eliminar) {
                    viewModel.cancelar(pedido);
                } else {
                    // Editar un pedido no es parte de la Fase 3 (§1.2).
                    avisarMaqueta();
                }
            }

            @Override
            public void onVerDetalle(Pedido pedido) {
                abrirDetalle(pedido);
            }
        });

        RecyclerView lista = view.findViewById(R.id.lista_pedidos);
        lista.setLayoutManager(new LinearLayoutManager(requireContext()));
        lista.setAdapter(adapter);

        // Ventana creciente (Plan Fase 3, §4.5): el último visible pasó itemCount − 5,
        // no hay una carga en curso y hayMas (derivado de contarTotal > ventana).
        lista.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                LinearLayoutManager layoutManager =
                        (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) {
                    return;
                }
                EstadoPedidos estado = viewModel.getEstado().getValue();
                boolean hayMas = estado != null && estado.isHayMas();
                int ultimoVisible = layoutManager.findLastVisibleItemPosition();
                if (!cargandoMas && hayMas && ultimoVisible >= layoutManager.getItemCount() - 5) {
                    cargandoMas = true;
                    viewModel.cargarMas();
                }
            }
        });

        ExtendedFloatingActionButton fab = view.findViewById(R.id.fab_nuevo_pedido);
        VistaPorPermiso.aplicar(fab, Modulo.PEDIDOS, Accion.CREAR);
        fab.setOnClickListener(v -> abrirTomaPedido());

        viewModel.getEstado().observe(getViewLifecycleOwner(), this::render);
    }

    private void render(EstadoPedidos estado) {
        asegurarChips();
        // La ventana ya creció: se habilita la siguiente carga de página.
        cargandoMas = false;
        adapter.submitList(estado.getPedidos());
        vacio.setVisibility(estado.isVacio() ? View.VISIBLE : View.GONE);

        String error = estado.getError();
        if (error != null) {
            Snackbar.make(requireView(), error, Snackbar.LENGTH_LONG).show();
            viewModel.onErrorConsumido();
        }

        String exito = estado.getMensajeExito();
        if (exito != null) {
            Snackbar.make(requireView(), exito, Snackbar.LENGTH_SHORT).show();
            viewModel.onMensajeConsumido();
        }
    }

    /** Chips del filtro desde el catálogo que baja del servidor; "Todos" primero. */
    private void asegurarChips() {
        List<EstadoPedido> estados = viewModel.getEstados();
        if (estados.size() == chipsConstruidos) {
            return;
        }
        chipsConstruidos = estados.size();
        grupoEstados.removeAllViews();
        grupoEstados.addView(crearChip(getString(R.string.filtro_todos), null, true));
        for (EstadoPedido estado : estados) {
            grupoEstados.addView(crearChip(getString(EstadoPedidoUi.etiqueta(estado)), estado, false));
        }
    }

    private Chip crearChip(String etiqueta, @Nullable EstadoPedido valor, boolean seleccionado) {
        Chip chip = new Chip(requireContext());
        chip.setText(etiqueta);
        chip.setCheckable(true);
        chip.setChecked(seleccionado);
        chip.setMinHeight(getResources().getDimensionPixelSize(R.dimen.altura_minima_tactil));
        chip.setOnClickListener(v -> viewModel.filtrarPorEstado(valor));
        return chip;
    }

    private void avisarMaqueta() {
        View raiz = getView();
        if (raiz != null) {
            Snackbar.make(raiz, R.string.maqueta_sin_funcion, Snackbar.LENGTH_SHORT).show();
        }
    }

    /**
     * Abre la toma del pedido (Plan Fase 3b, E8): reemplaza el contenido del tablero por el
     * {@code NuevoPedidoFragment}, igual que cambia de módulo {@code MainActivity}.
     */
    private void abrirTomaPedido() {
        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.contenedor_contenido, new NuevoPedidoFragment())
                .commit();
    }

    /** Abre el detalle del pedido (Plan Fase 3b, E9) con el ViewModel de su propio scope. */
    private void abrirDetalle(Pedido pedido) {
        DetallePedidoViewModel detalle = new ViewModelProvider(this,
                new DetallePedidoViewModelFactory(requireActivity().getApplication()))
                .get(DetallePedidoViewModel.class);
        DetallePedidoHoja hoja = DetallePedidoHoja.nuevaInstancia(pedido.getIdLocal());
        hoja.recibir(detalle);
        hoja.show(getParentFragmentManager(), DetallePedidoHoja.TAG);
    }
}
