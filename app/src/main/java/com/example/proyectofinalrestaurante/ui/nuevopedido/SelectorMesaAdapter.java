package com.example.proyectofinalrestaurante.ui.nuevopedido;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.model.Mesa;

import java.util.ArrayList;
import java.util.List;

/**
 * Selector de mesas (Plan Fase 3b, E8). Una fila tocar llama {@code onElegir(m)}: el
 * {@link SelectorMesaHoja} selecciona la mesa en el ViewModel y cierra.
 */
public class SelectorMesaAdapter extends RecyclerView.Adapter<SelectorMesaAdapter.Holder> {

    public interface AlElegir {
        void onElegir(Mesa mesa);
    }

    private final AlElegir listener;
    private final List<Mesa> mesas = new ArrayList<>();

    public SelectorMesaAdapter(@NonNull AlElegir listener) {
        this.listener = listener;
    }

    public void submitList(@NonNull List<Mesa> nueva) {
        mesas.clear();
        mesas.addAll(nueva);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View vista = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_selector_mesa, parent, false);
        return new Holder(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.enlazar(mesas.get(position));
    }

    @Override
    public int getItemCount() {
        return mesas.size();
    }

    class Holder extends RecyclerView.ViewHolder {

        private final TextView numero;
        private final TextView capacidad;
        private final TextView ubicacion;

        Holder(@NonNull View itemView) {
            super(itemView);
            numero = itemView.findViewById(R.id.txt_mesa_selector);
            capacidad = itemView.findViewById(R.id.txt_capacidad_selector_mesa);
            ubicacion = itemView.findViewById(R.id.txt_ubicacion_selector_mesa);
            itemView.setOnClickListener(v -> {
                int posicion = getBindingAdapterPosition();
                if (posicion != RecyclerView.NO_POSITION) {
                    listener.onElegir(mesas.get(posicion));
                }
            });
        }

        void enlazar(Mesa mesa) {
            numero.setText(itemView.getContext()
                    .getString(R.string.pedidos_mesa, mesa.getNumeroMesa()));
            capacidad.setText(itemView.getContext()
                    .getString(R.string.mesas_capacidad, mesa.getCapacidad()));
            ubicacion.setText(mesa.getUbicacion());
        }
    }
}