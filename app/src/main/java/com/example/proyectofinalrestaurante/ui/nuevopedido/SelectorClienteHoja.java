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
import com.example.proyectofinalrestaurante.domain.model.Cliente;
import com.example.proyectofinalrestaurante.ui.comun.HojaModal;

/**
 * Selector de clientes (Plan Fase 3b, E8). Lee de {@code estado.getClientes()} (Room), un toque
 * llama {@code viewModel.seleccionarCliente(c.getIdLocal())} y cierra. Recibe el mismo
 * {@link NuevoPedidoViewModel} que la pantalla principal.
 */
public class SelectorClienteHoja extends HojaModal {

    public static final String TAG = "SelectorClienteHoja";

    private NuevoPedidoViewModel viewModel;
    private SelectorClienteAdapter adapter;
    private TextView vacio;

    public void recibir(NuevoPedidoViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.hoja_selector_cliente, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vacio = view.findViewById(R.id.txt_selector_cliente_vacio);
        cerrarAlTocar(view, R.id.btn_cancelar_selector);

        adapter = new SelectorClienteAdapter(new SelectorClienteAdapter.AlElegir() {
            @Override
            public void onElegir(Cliente cliente) {
                if (viewModel != null) {
                    viewModel.seleccionarCliente(cliente.getIdLocal());
                }
                dismiss();
            }
        });

        RecyclerView lista = view.findViewById(R.id.lista_selector_clientes);
        lista.setLayoutManager(new LinearLayoutManager(requireContext()));
        lista.addItemDecoration(new DividerItemDecoration(requireContext(),
                DividerItemDecoration.VERTICAL));
        lista.setAdapter(adapter);

        if (viewModel != null) {
            viewModel.getEstado().observe(getViewLifecycleOwner(), this::render);
        }
    }

    private void render(EstadoNuevoPedido estado) {
        adapter.submitList(estado.getClientes());
        vacio.setVisibility(estado.getClientes().isEmpty() ? View.VISIBLE : View.GONE);
    }
}