package com.example.proyectofinalrestaurante.ui.buzon;

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
 * Hoja modal del buzón (Plan Fase 3, E9). Extiende {@link HojaModal} para heredar el aspecto
 * y las dos trampas ya pagadas ({@code STATE_EXPANDED} y {@code setSkipCollapsed}).
 *
 * <p>Recibe el {@link BuzonViewModel} ya creado por MainActivity (que lo usa para el badge):
 * dos consumidores de una sola fuente. Al abrirse llama a {@code abrir()} — marca todo leído
 * (el badge cae solo) y purga las leídas de más de 48 h.</p>
 */
public class BuzonHoja extends HojaModal {

    public static final String TAG = "BuzonHoja";

    private BuzonViewModel viewModel;
    private NotificacionAdapter adapter;
    private TextView vacio;

    private boolean yaAbierto = false;

    public void recibir(BuzonViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.hoja_buzon, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vacio = view.findViewById(R.id.txt_buzon_vacio);

        adapter = new NotificacionAdapter(this::marcarComoLeida);

        RecyclerView lista = view.findViewById(R.id.lista_notificaciones);
        lista.setLayoutManager(new LinearLayoutManager(requireContext()));
        lista.addItemDecoration(new DividerItemDecoration(requireContext(),
                DividerItemDecoration.VERTICAL));
        lista.setAdapter(adapter);

        if (viewModel != null) {
            viewModel.getEstado().observe(getViewLifecycleOwner(), this::render);
            // Purga + marcado de leídas solo la primera vez que se abre la hoja.
            if (!yaAbierto) {
                yaAbierto = true;
                viewModel.abrir();
            }
        }
    }

    private void render(EstadoBuzon estado) {
        adapter.submitList(estado.getNotificaciones());
        vacio.setVisibility(estado.isVacio() ? View.VISIBLE : View.GONE);
    }

    private void marcarComoLeida(com.example.proyectofinalrestaurante.domain.model.Notificacion n) {
        if (viewModel != null) {
            viewModel.marcarLeida(n);
        }
    }
}