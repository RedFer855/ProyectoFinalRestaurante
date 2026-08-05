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
import com.example.proyectofinalrestaurante.ui.comun.HojaModal;
import com.google.android.material.button.MaterialButton;

/**
 * Carrito de la toma del pedido (Plan Fase 3b, E8). Extiende {@link HojaModal} como
 * {@code BuzonHoja}: recibe el {@link NuevoPedidoViewModel} compartido por
 * {@code recibir(vm)} y consume el mismo estado que la pantalla principal.
 */
public class CarritoHoja extends HojaModal {

    public static final String TAG = "CarritoHoja";

    private NuevoPedidoViewModel viewModel;
    private LineaCarritoAdapter adapter;
    private TextView total;
    private TextView vacio;
    private MaterialButton confirmar;

    public void recibir(NuevoPedidoViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.hoja_carrito, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        total = view.findViewById(R.id.txt_total_carrito);
        vacio = view.findViewById(R.id.txt_carrito_vacio);
        confirmar = view.findViewById(R.id.btn_confirmar_carrito);
        confirmar.setOnClickListener(v -> confirmarYcerrar());

        adapter = new LineaCarritoAdapter(new LineaCarritoAdapter.AlInteractuar() {
            @Override
            public void onCambiarCantidad(int idLocalPlatillo, int cantidadNueva) {
                if (viewModel != null) {
                    viewModel.cambiarCantidad(idLocalPlatillo, cantidadNueva);
                }
            }

            @Override
            public void onQuitar(int idLocalPlatillo) {
                if (viewModel != null) {
                    viewModel.quitarPlatillo(idLocalPlatillo);
                }
            }
        });

        RecyclerView lista = view.findViewById(R.id.lista_lineas_carrito);
        lista.setLayoutManager(new LinearLayoutManager(requireContext()));
        lista.addItemDecoration(new DividerItemDecoration(requireContext(),
                DividerItemDecoration.VERTICAL));
        lista.setAdapter(adapter);

        if (viewModel != null) {
            viewModel.getEstado().observe(getViewLifecycleOwner(), this::render);
        }
    }

    private void render(EstadoNuevoPedido estado) {
        adapter.submitList(estado.getCarrito().getLineas());
        total.setText(getString(R.string.formato_lempiras, estado.getCarrito().total()));
        vacio.setVisibility(estado.isCarritoVacio() ? View.VISIBLE : View.GONE);
        confirmar.setEnabled(estado.isPuedeTomarPedido() && estado.isPuedeConfirmar());
    }

    /** Confirma y cierra la hoja; el {@code NuevoPedidoFragment} navega al éxito. */
    private void confirmarYcerrar() {
        if (viewModel != null) {
            viewModel.confirmar(getString(R.string.nuevo_pedido_creado));
        }
        dismiss();
    }
}