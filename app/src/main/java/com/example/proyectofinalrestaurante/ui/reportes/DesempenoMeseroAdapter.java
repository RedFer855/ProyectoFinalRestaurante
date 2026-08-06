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
import com.example.proyectofinalrestaurante.domain.model.DesempenoMesero;

/** Desempeño por mesero (Plan Fase 3c, E10). Reemplaza el inflado manual de filas. */
public class DesempenoMeseroAdapter
        extends ListAdapter<DesempenoMesero, DesempenoMeseroAdapter.Holder> {

    public DesempenoMeseroAdapter() {
        super(DIFF);
    }

    private static final DiffUtil.ItemCallback<DesempenoMesero> DIFF =
            new DiffUtil.ItemCallback<DesempenoMesero>() {
        @Override
        public boolean areItemsTheSame(@NonNull DesempenoMesero a, @NonNull DesempenoMesero b) {
            return a.getNombre().equals(b.getNombre());
        }

        @Override
        public boolean areContentsTheSame(@NonNull DesempenoMesero a, @NonNull DesempenoMesero b) {
            return a.getCantidadPedidos() == b.getCantidadPedidos()
                    && a.getTotalVendido() == b.getTotalVendido();
        }
    };

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View vista = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_desempeno, parent, false);
        return new Holder(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.enlazar(getItem(position));
    }

    static class Holder extends RecyclerView.ViewHolder {

        private final TextView nombre;
        private final TextView pedidos;
        private final TextView total;

        Holder(@NonNull View itemView) {
            super(itemView);
            nombre = itemView.findViewById(R.id.txt_desempeno_nombre);
            pedidos = itemView.findViewById(R.id.txt_desempeno_pedidos);
            total = itemView.findViewById(R.id.txt_desempeno_total);
        }

        void enlazar(DesempenoMesero item) {
            nombre.setText(item.getNombre());
            pedidos.setText(itemView.getContext()
                    .getString(R.string.reportes_pedidos_atendidos, item.getCantidadPedidos()));
            total.setText(itemView.getContext()
                    .getString(R.string.formato_lempiras, item.getTotalVendido()));
        }
    }
}
