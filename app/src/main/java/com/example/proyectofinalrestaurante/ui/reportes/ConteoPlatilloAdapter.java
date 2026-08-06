package com.example.proyectofinalrestaurante.ui.reportes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.model.ConteoPlatillo;

/** Top-5 de platillos más pedidos (Plan Fase 3c, E10). Reemplaza el inflado manual de filas. */
public class ConteoPlatilloAdapter extends ListAdapter<ConteoPlatillo, ConteoPlatilloAdapter.Holder> {

    public ConteoPlatilloAdapter() {
        super(DIFF);
    }

    private static final DiffUtil.ItemCallback<ConteoPlatillo> DIFF =
            new DiffUtil.ItemCallback<ConteoPlatillo>() {
        @Override
        public boolean areItemsTheSame(@NonNull ConteoPlatillo a, @NonNull ConteoPlatillo b) {
            return a.getNombre().equals(b.getNombre());
        }

        @Override
        public boolean areContentsTheSame(@NonNull ConteoPlatillo a, @NonNull ConteoPlatillo b) {
            return a.getCantidad() == b.getCantidad();
        }
    };

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View vista = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conteo, parent, false);
        return new Holder(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.enlazar(getItem(position));
    }

    static class Holder extends RecyclerView.ViewHolder {

        private final TextView etiqueta;
        private final TextView valor;

        Holder(@NonNull View itemView) {
            super(itemView);
            etiqueta = itemView.findViewById(R.id.txt_conteo_etiqueta);
            valor = itemView.findViewById(R.id.txt_conteo_valor);
        }

        void enlazar(ConteoPlatillo item) {
            etiqueta.setText(item.getNombre());
            valor.setText(String.valueOf(item.getCantidad()));
        }
    }
}
