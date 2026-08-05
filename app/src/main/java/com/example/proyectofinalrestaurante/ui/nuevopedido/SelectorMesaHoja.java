package com.example.proyectofinalrestaurante.ui.nuevopedido;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.model.Mesa;
import com.example.proyectofinalrestaurante.ui.comun.HojaModal;

/**
 * Selector de mesas (Plan Fase 3b, E8). Lee de {@code estado.getMesas()} (Room), un toque llama
 * {@code viewModel.seleccionarMesa(m.getIdLocal())} y cierra. Recibe el mismo
 * {@link NuevoPedidoViewModel} que la pantalla principal.
 */
public class SelectorMesaHoja extends HojaModal {

    public static final String TAG = "SelectorMesaHoja";

    private NuevoPedidoViewModel viewModel;
    private SelectorMesaAdapter adapter;
    private TextView vacio;

    public void recibir(NuevoPedidoViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.hoja_selector_mesa, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vacio = view.findViewById(R.id.txt_selector_mesa_vacio);
        cerrarAlTocar(view, R.id.btn_cancelar_selector);

        adapter = new SelectorMesaAdapter(new SelectorMesaAdapter.AlElegir() {
            @Override
            public void onElegir(Mesa mesa) {
                if (viewModel != null) {
                    viewModel.seleccionarMesa(mesa.getIdLocal());
                }
                dismiss();
            }
        });

        RecyclerView lista = view.findViewById(R.id.lista_selector_mesas);
        lista.setLayoutManager(new LinearLayoutManager(requireContext()));
        lista.addItemDecoration(new DividerItemDecoration(requireContext(),
                DividerItemDecoration.VERTICAL));
        lista.setAdapter(adapter);

        if (viewModel != null) {
            viewModel.getEstado().observe(getViewLifecycleOwner(), this::render);
        }
    }

    private void render(EstadoNuevoPedido estado) {
        adapter.submitList(estado.getMesas());
        vacio.setVisibility(estado.getMesas().isEmpty() ? View.VISIBLE : View.GONE);
    }
}