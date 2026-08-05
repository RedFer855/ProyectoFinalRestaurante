package com.example.proyectofinalrestaurante.ui.nuevopedido;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.model.LineaCarrito;

import java.util.ArrayList;
import java.util.List;

/**
 * Líneas del carrito (Plan Fase 3b, E8). Espejo de {@code LineaPedidoAdapter} pero con un
 * stepper +/− que llama al {@link NuevoPedidoViewModel} compartido: el mismo modelo que la
 * pantalla principal, por eso el adapter no emite "eventos" sino que invoca al ViewModel con
 * el id local de cada línea.
 */
public class LineaCarritoAdapter extends RecyclerView.Adapter<LineaCarritoAdapter.Holder> {

    public interface AlInteractuar {
        void onCambiarCantidad(int idLocalPlatillo, int cantidadNueva);

        void onQuitar(int idLocalPlatillo);
    }

    private final AlInteractuar listener;
    private final List<LineaCarrito> lineas = new ArrayList<>();

    public LineaCarritoAdapter(@NonNull AlInteractuar listener) {
        this.listener = listener;
    }

    public void submitList(@NonNull List<LineaCarrito> nueva) {
        lineas.clear();
        lineas.addAll(nueva);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View vista = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_linea_carrito, parent, false);
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

    class Holder extends RecyclerView.ViewHolder {

        private final TextView nombre;
        private final TextView cantidad;
        private final TextView subtotal;
        private int idLocalPlatillo;
        private int cantidadActual;

        Holder(@NonNull View itemView) {
            super(itemView);
            nombre = itemView.findViewById(R.id.txt_nombre_linea_carrito);
            cantidad = itemView.findViewById(R.id.txt_cantidad_linea_carrito);
            subtotal = itemView.findViewById(R.id.txt_subtotal_linea_carrito);
            itemView.findViewById(R.id.btn_menos_linea_carrito)
                    .setOnClickListener(v ->
                            listener.onCambiarCantidad(idLocalPlatillo, cantidadActual - 1));
            itemView.findViewById(R.id.btn_mas_linea_carrito)
                    .setOnClickListener(v ->
                            listener.onCambiarCantidad(idLocalPlatillo, cantidadActual + 1));
            itemView.findViewById(R.id.btn_quitar_linea_carrito)
                    .setOnClickListener(v -> listener.onQuitar(idLocalPlatillo));
        }

        void enlazar(LineaCarrito linea) {
            idLocalPlatillo = linea.getIdLocalPlatillo();
            cantidadActual = linea.getCantidad();
            nombre.setText(linea.getNombre());
            cantidad.setText(String.valueOf(linea.getCantidad()));
            subtotal.setText(itemView.getContext()
                    .getString(R.string.formato_lempiras, linea.subtotal()));
        }
    }
}