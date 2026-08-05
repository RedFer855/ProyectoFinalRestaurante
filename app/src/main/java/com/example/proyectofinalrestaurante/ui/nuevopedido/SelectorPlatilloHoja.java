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
import com.example.proyectofinalrestaurante.domain.model.Platillo;
import com.example.proyectofinalrestaurante.ui.comun.HojaModal;

/**
 * Selector de platillos (Plan Fase 3b, E8). Lee de {@code estado.getPlatillos()} (Room), un
 * toque llama {@code viewModel.agregarPlatillo(p)} (B6) y cierra la hoja. Recibe el mismo
 * {@link NuevoPedidoViewModel} que la pantalla principal.
 */
public class SelectorPlatilloHoja extends HojaModal {

    public static final String TAG = "SelectorPlatilloHoja";

    private NuevoPedidoViewModel viewModel;
    private SelectorPlatilloAdapter adapter;
    private TextView vacio;

    public void recibir(NuevoPedidoViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.hoja_selector_platillo, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vacio = view.findViewById(R.id.txt_selector_platillo_vacio);
        cerrarAlTocar(view, R.id.btn_cancelar_selector);

        adapter = new SelectorPlatilloAdapter(new SelectorPlatilloAdapter.AlElegir() {
            @Override
            public void onElegir(Platillo platillo) {
                if (viewModel != null) {
                    viewModel.agregarPlatillo(platillo);
                }
                dismiss();
            }
        });

        RecyclerView lista = view.findViewById(R.id.lista_selector_platillos);
        lista.setLayoutManager(new LinearLayoutManager(requireContext()));
        lista.addItemDecoration(new DividerItemDecoration(requireContext(),
                DividerItemDecoration.VERTICAL));
        lista.setAdapter(adapter);

        if (viewModel != null) {
            viewModel.getEstado().observe(getViewLifecycleOwner(), this::render);
        }
    }

    private void render(EstadoNuevoPedido estado) {
        adapter.submitList(estado.getPlatillos());
        vacio.setVisibility(estado.getPlatillos().isEmpty() ? View.VISIBLE : View.GONE);
    }
}