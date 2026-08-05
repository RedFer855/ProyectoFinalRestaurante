package com.example.proyectofinalrestaurante.ui.nuevopedido;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.model.Platillo;

import java.util.ArrayList;
import java.util.List;

/**
 * Selector de platillo (Plan Fase 3b, E8/B6). Una fila tocar llama {@code onElegir(p)}: el
 * {@link SelectorPlatilloHoja} agrega al carrito y cierra. La lista ya llega filtrada por
 * {@code ReglasPedido.puedePedirse} desde el ViewModel.
 */
public class SelectorPlatilloAdapter extends RecyclerView.Adapter<SelectorPlatilloAdapter.Holder> {

    public interface AlElegir {
        void onElegir(Platillo platillo);
    }

    private final AlElegir listener;
    private final List<Platillo> platillos = new ArrayList<>();

    public SelectorPlatilloAdapter(@NonNull AlElegir listener) {
        this.listener = listener;
    }

    public void submitList(@NonNull List<Platillo> nueva) {
        platillos.clear();
        platillos.addAll(nueva);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View vista = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_selector_platillo, parent, false);
        return new Holder(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.enlazar(platillos.get(position));
    }

    @Override
    public int getItemCount() {
        return platillos.size();
    }

    class Holder extends RecyclerView.ViewHolder {

        private final TextView nombre;
        private final TextView categoria;
        private final TextView precio;

        Holder(@NonNull View itemView) {
            super(itemView);
            nombre = itemView.findViewById(R.id.txt_nombre_selector_platillo);
            categoria = itemView.findViewById(R.id.txt_categoria_selector_platillo);
            precio = itemView.findViewById(R.id.txt_precio_selector_platillo);
            itemView.setOnClickListener(v -> {
                int posicion = getBindingAdapterPosition();
                if (posicion != RecyclerView.NO_POSITION) {
                    listener.onElegir(platillos.get(posicion));
                }
            });
        }

        void enlazar(Platillo platillo) {
            nombre.setText(platillo.getNombre());
            categoria.setText(platillo.getNombreCategoria());
            precio.setText(itemView.getContext()
                    .getString(R.string.formato_lempiras, platillo.getPrecio()));
        }
    }
}