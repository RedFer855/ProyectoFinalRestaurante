package com.example.proyectofinalrestaurante.ui.detallepedido;

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

/**
 * Hoja modal del detalle de un pedido (Plan Fase 3b, E9). Extiende {@link HojaModal} para
 * heredar el aspecto y las dos trampas ya pagadas ({@code STATE_EXPANDED} y
 * {@code setSkipCollapsed}).
 *
 * <p>Recibe el {@link DetallePedidoViewModel} ya creado y el {@code idLocal} del pedido por
 * {@code arguments} (sobrevive a la recreación por {@code setArguments}). La carga es bajo
 * demanda: recién en {@code onViewCreated} se llama a {@link DetallePedidoViewModel#cargarDetalle(long)},
 * la primera vez que se abre la hoja. Mismo esqueleto que {@code BuzonHoja}.</p>
 */
public class DetallePedidoHoja extends HojaModal {

    public static final String TAG = "DetallePedidoHoja";

    private static final String EXTRA_ID_PEDIDO = "extra_id_pedido";

    private DetallePedidoViewModel viewModel;
    private LineaPedidoAdapter adapter;
    private TextView numero;
    private TextView total;
    private TextView vacio;

    private boolean yaAbierto = false;

    public static DetallePedidoHoja nuevaInstancia(long idPedidoLocal) {
        DetallePedidoHoja hoja = new DetallePedidoHoja();
        Bundle argumentos = new Bundle();
        argumentos.putLong(EXTRA_ID_PEDIDO, idPedidoLocal);
        hoja.setArguments(argumentos);
        return hoja;
    }

    public void recibir(DetallePedidoViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.hoja_detalle_pedido, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        numero = view.findViewById(R.id.txt_numero_detalle);
        total = view.findViewById(R.id.txt_total_detalle);
        vacio = view.findViewById(R.id.txt_detalle_vacio);

        long idPedido = getArguments() != null ? getArguments().getLong(EXTRA_ID_PEDIDO, 0L) : 0L;
        numero.setText(getString(R.string.detalle_numero, idPedido));

        adapter = new LineaPedidoAdapter();

        RecyclerView lista = view.findViewById(R.id.lista_lineas_pedido);
        lista.setLayoutManager(new LinearLayoutManager(requireContext()));
        lista.addItemDecoration(new DividerItemDecoration(requireContext(),
                DividerItemDecoration.VERTICAL));
        lista.setAdapter(adapter);

        if (viewModel != null) {
            viewModel.getEstado().observe(getViewLifecycleOwner(), this::render);
            // La carga bajo demanda solo la primera vez que se abre la hoja.
            if (!yaAbierto) {
                yaAbierto = true;
                viewModel.cargarDetalle(idPedido);
            }
        }
    }

    private void render(EstadoDetallePedido estado) {
        adapter.submitList(estado.getLineas());
        total.setText(getString(R.string.formato_lempiras, estado.getTotal()));
        vacio.setVisibility(estado.isVacio() ? View.VISIBLE : View.GONE);
    }
}