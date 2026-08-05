package com.example.proyectofinalrestaurante.ui.nuevopedido;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.model.Cliente;

import java.util.ArrayList;
import java.util.List;

/**
 * Selector de clientes (Plan Fase 3b, E8). Una fila tocar llama {@code onElegir(c)}: el
 * {@link SelectorClienteHoja} selecciona el cliente en el ViewModel y cierra.
 */
public class SelectorClienteAdapter extends RecyclerView.Adapter<SelectorClienteAdapter.Holder> {

    public interface AlElegir {
        void onElegir(Cliente cliente);
    }

    private final AlElegir listener;
    private final List<Cliente> clientes = new ArrayList<>();

    public SelectorClienteAdapter(@NonNull AlElegir listener) {
        this.listener = listener;
    }

    public void submitList(@NonNull List<Cliente> nueva) {
        clientes.clear();
        clientes.addAll(nueva);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View vista = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_selector_cliente, parent, false);
        return new Holder(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.enlazar(clientes.get(position));
    }

    @Override
    public int getItemCount() {
        return clientes.size();
    }

    class Holder extends RecyclerView.ViewHolder {

        private final TextView iniciales;
        private final TextView nombre;
        private final TextView identidad;
        private final TextView telefono;

        Holder(@NonNull View itemView) {
            super(itemView);
            iniciales = itemView.findViewById(R.id.txt_iniciales_selector_cliente);
            nombre = itemView.findViewById(R.id.txt_nombre_selector_cliente);
            identidad = itemView.findViewById(R.id.txt_identidad_selector_cliente);
            telefono = itemView.findViewById(R.id.txt_telefono_selector_cliente);
            itemView.setOnClickListener(v -> {
                int posicion = getBindingAdapterPosition();
                if (posicion != RecyclerView.NO_POSITION) {
                    listener.onElegir(clientes.get(posicion));
                }
            });
        }

        void enlazar(Cliente cliente) {
            iniciales.setText(inicialesDe(cliente));
            nombre.setText(cliente.nombreCompleto());
            identidad.setText(cliente.getIdentidad());
            telefono.setText(cliente.getTelefono());
        }

        private String inicialesDe(Cliente cliente) {
            char primera = cliente.getNombre().isEmpty() ? '?' : cliente.getNombre().charAt(0);
            char segunda = cliente.getApellido().isEmpty() ? ' ' : cliente.getApellido().charAt(0);
            return (String.valueOf(primera) + segunda).trim().toUpperCase();
        }
    }
}