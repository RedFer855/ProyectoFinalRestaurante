package com.example.proyectofinalrestaurante.ui.mesas;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.Accion;
import com.example.proyectofinalrestaurante.domain.Modulo;
import com.example.proyectofinalrestaurante.ui.maqueta.DatosMaqueta;
import com.example.proyectofinalrestaurante.ui.permisos.VistaPorPermiso;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/**
 * Módulo Mesas (Plan Fase 1c, Entregable 4). Cocina no accede a este módulo — ni
 * siquiera aparece en su menú. El mesero puede ocupar y liberar mesas, pero solo el
 * admin puede crearlas o eliminarlas.
 */
public class MesasFragment extends Fragment {

    private MesaAdapter adapter;
    private TextView vacio;
    private final List<DatosMaqueta.Mesa> mesas = new ArrayList<>();
    private DatosMaqueta.EstadoMesa filtroEstado = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mesas, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vacio = view.findViewById(R.id.txt_mesas_vacio);

        if (mesas.isEmpty()) {
            mesas.addAll(DatosMaqueta.mesas());
        }

        adapter = new MesaAdapter(this::cambiarEstado);
        RecyclerView lista = view.findViewById(R.id.lista_mesas);
        // Las columnas salen de integers.xml, con variante sw600dp para tablet.
        int columnas = getResources().getInteger(R.integer.columnas_mesas);
        lista.setLayoutManager(new GridLayoutManager(requireContext(), columnas));
        lista.setAdapter(adapter);

        configurarFiltroEstados(view.findViewById(R.id.grupo_estados_mesa));

        ExtendedFloatingActionButton fab = view.findViewById(R.id.fab_agregar_mesa);
        VistaPorPermiso.aplicar(fab, Modulo.MESAS, Accion.CREAR);
        fab.setOnClickListener(v -> avisarMaqueta());

        refrescar();
    }

    private void configurarFiltroEstados(ChipGroup grupo) {
        grupo.removeAllViews();
        grupo.addView(crearChip(getString(R.string.filtro_todos), null, true));
        for (DatosMaqueta.EstadoMesa estado : DatosMaqueta.EstadoMesa.values()) {
            grupo.addView(crearChip(getString(estado.etiqueta), estado, false));
        }
    }

    private Chip crearChip(String etiqueta, @Nullable DatosMaqueta.EstadoMesa valor,
                           boolean seleccionado) {
        Chip chip = new Chip(requireContext());
        chip.setText(etiqueta);
        chip.setCheckable(true);
        chip.setChecked(seleccionado);
        chip.setMinHeight(getResources().getDimensionPixelSize(R.dimen.altura_minima_tactil));
        chip.setOnClickListener(v -> {
            filtroEstado = valor;
            refrescar();
        });
        return chip;
    }

    private void cambiarEstado(DatosMaqueta.Mesa mesa) {
        for (int i = 0; i < mesas.size(); i++) {
            if (mesas.get(i).numero == mesa.numero) {
                mesas.set(i, mesa.conEstado(mesa.estado.siguiente()));
                break;
            }
        }
        refrescar();
    }

    private void refrescar() {
        List<DatosMaqueta.Mesa> filtradas = new ArrayList<>();
        for (DatosMaqueta.Mesa mesa : mesas) {
            if (filtroEstado == null || mesa.estado == filtroEstado) {
                filtradas.add(mesa);
            }
        }
        adapter.submitList(filtradas);
        vacio.setVisibility(filtradas.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void avisarMaqueta() {
        View raiz = getView();
        if (raiz != null) {
            Snackbar.make(raiz, R.string.maqueta_sin_funcion, Snackbar.LENGTH_SHORT).show();
        }
    }
}
