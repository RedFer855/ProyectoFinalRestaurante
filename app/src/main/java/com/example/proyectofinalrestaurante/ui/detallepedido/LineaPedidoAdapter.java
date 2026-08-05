package com.example.proyectofinalrestaurante.ui.detallepedido;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.model.LineaPedido;

import java.util.ArrayList;
import java.util.List;

/**
 * Líneas del detalle de un pedido (Plan Fase 3b, E9). Una fila simple con nombre, cantidad,
 * precio y subtotal, espejo de la estructura de {@code PedidoAdapter} pero sobre
 * {@link LineaPedido}. Sin {@code DiffUtil}: la lista del detalle es corta y se remplaza completa
 * cuando vuelve de Room.
 */
public class LineaPedidoAdapter extends RecyclerView.Adapter<LineaPedidoAdapter.Holder> {

    private final List<LineaPedido> lineas = new ArrayList<>();

    public void submitList(List<LineaPedido> nueva) {
        lineas.clear();
        if (nueva != null) {
            lineas.addAll(nueva);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View vista = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_linea_pedido, parent, false);
        return new Holder(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.enlazar(lineas.get(position));
    }

    @Override
    public int getItemCount() {
        return lineas.size();
    }

    static class Holder extends RecyclerView.ViewHolder {

        private final TextView nombre;
        private final TextView cantidad;
        private final TextView subtotal;

        Holder(@NonNull View itemView) {
            super(itemView);
            nombre = itemView.findViewById(R.id.txt_nombre_linea);
            cantidad = itemView.findViewById(R.id.txt_cantidad_linea);
            subtotal = itemView.findViewById(R.id.txt_subtotal_linea);
        }

        void enlazar(LineaPedido linea) {
            nombre.setText(linea.getNombre());
            cantidad.setText(itemView.getContext()
                    .getString(R.string.detalle_cantidad, linea.getCantidad()));
            subtotal.setText(itemView.getContext()
                    .getString(R.string.formato_lempiras, linea.subtotal()));
        }
    }
}