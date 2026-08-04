package com.example.proyectofinalrestaurante.ui.mesas;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.Accion;
import com.example.proyectofinalrestaurante.domain.Modulo;
import com.example.proyectofinalrestaurante.domain.model.EstadoMesa;
import com.example.proyectofinalrestaurante.domain.model.Mesa;
import com.example.proyectofinalrestaurante.ui.permisos.VistaPorPermiso;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

/**
 * Módulo Mesas (Fase 2c). Cocina no accede a este módulo. El mesero puede
 * cambiar el estado de las mesas; solo el admin puede crear, editar o dar de baja.
 *
 * <p>La grilla se lee mejor en cuadrícula ({@link GridLayoutManager}) y el color
 * por estado sale de la paleta. El chip muestra color <b>+</b> etiqueta de texto
 * para accesibilidad (ver Guía de Diseño Visual).</p>
 */
public class MesasFragment extends Fragment {

    private MesasViewModel viewModel;
    private MesaAdapter adapter;
    private TextView vacio;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mesas, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this, new MesasViewModelFactory(
                requireActivity().getApplication())).get(MesasViewModel.class);

        vacio = view.findViewById(R.id.txt_mesas_vacio);

        adapter = new MesaAdapter(this::cambiarEstado);
        RecyclerView lista = view.findViewById(R.id.lista_mesas);
        int columnas = getResources().getInteger(R.integer.columnas_mesas);
        lista.setLayoutManager(new GridLayoutManager(requireContext(), columnas));
        lista.setAdapter(adapter);

        configurarFiltroEstados(view.findViewById(R.id.grupo_estados_mesa));

        ExtendedFloatingActionButton fab = view.findViewById(R.id.fab_agregar_mesa);
        VistaPorPermiso.aplicar(fab, Modulo.MESAS, Accion.CREAR);
        fab.setOnClickListener(v -> avisarMaqueta());

        viewModel.getEstado().observe(getViewLifecycleOwner(), this::repintar);
    }

    private void configurarFiltroEstados(ChipGroup grupo) {
        grupo.removeAllViews();
        grupo.addView(crearChip(getString(R.string.filtro_todos), null, true));
        for (EstadoMesa estado : EstadoMesa.values()) {
            grupo.addView(crearChip(getString(etiquetaDeEstado(estado)), estado, false));
        }
    }

    private Chip crearChip(String etiqueta, @Nullable EstadoMesa valor, boolean seleccionado) {
        Chip chip = new Chip(requireContext());
        chip.setText(etiqueta);
        chip.setCheckable(true);
        chip.setChecked(seleccionado);
        chip.setMinHeight(getResources().getDimensionPixelSize(R.dimen.altura_minima_tactil));
        chip.setOnClickListener(v -> {
            viewModel.filtrarPorEstado(valor);
            actualizarSeleccionChips(requireView().findViewById(R.id.grupo_estados_mesa), valor);
        });
        return chip;
    }

    private void actualizarSeleccionChips(ChipGroup grupo, @Nullable EstadoMesa seleccionado) {
        for (int i = 0; i < grupo.getChildCount(); i++) {
            Chip chip = (Chip) grupo.getChildAt(i);
            boolean debeEstarSeleccionado = (i == 0 && seleccionado == null)
                    || (i > 0 && EstadoMesa.values()[i - 1] == seleccionado);
            chip.setChecked(debeEstarSeleccionado);
        }
    }

    private void cambiarEstado(Mesa mesa) {
        viewModel.cambiarEstadoMesa(mesa.getIdLocal(), mesa.getEstadoMesa().siguiente());
    }

    private void repintar(EstadoMesas estado) {
        if (estado == null) {
            return;
        }
        adapter.submitList(estado.getMesas());
        vacio.setVisibility(estado.isVacio() ? View.VISIBLE : View.GONE);

        if (estado.getMensajeExito() != null) {
            View raiz = getView();
            if (raiz != null) {
                Snackbar.make(raiz, estado.getMensajeExito(), Snackbar.LENGTH_SHORT).show();
            }
            viewModel.onMensajeConsumido();
        }

        if (estado.getError() != null) {
            View raiz = getView();
            if (raiz != null) {
                Snackbar.make(raiz, estado.getError(), Snackbar.LENGTH_LONG).show();
            }
        }
    }

    private void avisarMaqueta() {
        View raiz = getView();
        if (raiz != null) {
            Snackbar.make(raiz, R.string.maqueta_sin_funcion, Snackbar.LENGTH_SHORT).show();
        }
    }

    private static int etiquetaDeEstado(EstadoMesa estado) {
        switch (estado) {
            case OCUPADA:
                return R.string.estado_mesa_ocupada;
            case RESERVADA:
                return R.string.estado_mesa_reservada;
            case LIBRE:
            default:
                return R.string.estado_mesa_libre;
        }
    }
}
