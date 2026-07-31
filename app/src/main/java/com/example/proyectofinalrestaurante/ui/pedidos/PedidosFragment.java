package com.example.proyectofinalrestaurante.ui.pedidos;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
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
 * Módulo Pedidos (Plan Fase 1c, Entregable 4). Es el módulo donde más se nota la
 * matriz de permisos: cocina puede avanzar el estado pero no crear pedidos; mesero
 * puede crear y editar pero no cancelar; admin puede todo.
 *
 * <p>El cambio de estado sí funciona (en memoria) — es lo que hace demostrable el
 * flujo de cocina sin haber conectado la base todavía.</p>
 */
public class PedidosFragment extends Fragment {

    private PedidoAdapter adapter;
    private TextView vacio;
    private final List<DatosMaqueta.Pedido> pedidos = new ArrayList<>();
    private DatosMaqueta.EstadoPedido filtroEstado = null;

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

        if (pedidos.isEmpty()) {
            pedidos.addAll(DatosMaqueta.pedidos());
        }

        adapter = new PedidoAdapter(new PedidoAdapter.AlInteractuar() {
            @Override
            public void onAvanzarEstado(DatosMaqueta.Pedido pedido) {
                avanzarEstado(pedido);
            }

            @Override
            public void onAccion(DatosMaqueta.Pedido pedido, int accionId) {
                avisarMaqueta();
            }
        });

        RecyclerView lista = view.findViewById(R.id.lista_pedidos);
        lista.setLayoutManager(new LinearLayoutManager(requireContext()));
        lista.setAdapter(adapter);

        configurarFiltroEstados(view.findViewById(R.id.grupo_estados_pedido));

        ExtendedFloatingActionButton fab = view.findViewById(R.id.fab_nuevo_pedido);
        VistaPorPermiso.aplicar(fab, Modulo.PEDIDOS, Accion.CREAR);
        fab.setOnClickListener(v -> avisarMaqueta());

        refrescar();
    }

    private void configurarFiltroEstados(ChipGroup grupo) {
        grupo.removeAllViews();
        grupo.addView(crearChip(getString(R.string.filtro_todos), null, true));
        for (DatosMaqueta.EstadoPedido estado : DatosMaqueta.EstadoPedido.values()) {
            grupo.addView(crearChip(getString(estado.etiqueta), estado, false));
        }
    }

    private Chip crearChip(String etiqueta, @Nullable DatosMaqueta.EstadoPedido valor,
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

    /** Sustituye el pedido por una copia con el estado siguiente — DiffUtil anima el cambio. */
    private void avanzarEstado(DatosMaqueta.Pedido pedido) {
        for (int i = 0; i < pedidos.size(); i++) {
            if (pedidos.get(i).numero == pedido.numero) {
                pedidos.set(i, pedido.conEstado(pedido.estado.siguiente()));
                break;
            }
        }
        refrescar();
    }

    private void refrescar() {
        List<DatosMaqueta.Pedido> filtrados = new ArrayList<>();
        for (DatosMaqueta.Pedido pedido : pedidos) {
            if (filtroEstado == null || pedido.estado == filtroEstado) {
                filtrados.add(pedido);
            }
        }
        adapter.submitList(filtrados);
        vacio.setVisibility(filtrados.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void avisarMaqueta() {
        View raiz = getView();
        if (raiz != null) {
            Snackbar.make(raiz, R.string.maqueta_sin_funcion, Snackbar.LENGTH_SHORT).show();
        }
    }
}
