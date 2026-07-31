package com.example.proyectofinalrestaurante.ui.clientes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.Accion;
import com.example.proyectofinalrestaurante.domain.Modulo;
import com.example.proyectofinalrestaurante.ui.maqueta.DatosMaqueta;
import com.example.proyectofinalrestaurante.ui.permisos.VistaPorPermiso;

/** Lista de clientes. Mesero puede editar; solo admin puede eliminar. */
public class ClienteAdapter extends ListAdapter<DatosMaqueta.Cliente, ClienteAdapter.Holder> {

    public interface AlElegirAccion {
        void onAccion(DatosMaqueta.Cliente cliente, int accionId);
    }

    private final AlElegirAccion alElegirAccion;

    public ClienteAdapter(AlElegirAccion alElegirAccion) {
        super(DIFF);
        this.alElegirAccion = alElegirAccion;
    }

    private static final DiffUtil.ItemCallback<DatosMaqueta.Cliente> DIFF =
            new DiffUtil.ItemCallback<DatosMaqueta.Cliente>() {
                @Override
                public boolean areItemsTheSame(@NonNull DatosMaqueta.Cliente a,
                                               @NonNull DatosMaqueta.Cliente b) {
                    return a.nombreCompleto().equals(b.nombreCompleto());
                }

                @Override
                public boolean areContentsTheSame(@NonNull DatosMaqueta.Cliente a,
                                                  @NonNull DatosMaqueta.Cliente b) {
                    return a.telefono.equals(b.telefono);
                }
            };

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View vista = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cliente, parent, false);
        return new Holder(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.enlazar(getItem(position), alElegirAccion);
    }

    static class Holder extends RecyclerView.ViewHolder {

        private final TextView iniciales;
        private final TextView nombre;
        private final TextView identidad;
        private final TextView telefono;
        private final ImageButton opciones;

        Holder(@NonNull View itemView) {
            super(itemView);
            iniciales = itemView.findViewById(R.id.txt_iniciales_cliente);
            nombre = itemView.findViewById(R.id.txt_nombre_cliente);
            identidad = itemView.findViewById(R.id.txt_identidad_cliente);
            telefono = itemView.findViewById(R.id.txt_telefono_cliente);
            opciones = itemView.findViewById(R.id.btn_opciones_cliente);
        }

        void enlazar(DatosMaqueta.Cliente cliente, AlElegirAccion alElegirAccion) {
            nombre.setText(cliente.nombreCompleto());
            iniciales.setText(inicialesDe(cliente));
            // La identidad es opcional a propósito: la venta de mostrador no la pide (ADR-006).
            identidad.setText(cliente.identidad != null
                    ? cliente.identidad
                    : itemView.getContext().getString(R.string.clientes_sin_identidad));
            telefono.setText(cliente.telefono);

            boolean puedeEditar = VistaPorPermiso.puede(Modulo.CLIENTES, Accion.EDITAR);
            boolean puedeEliminar = VistaPorPermiso.puede(Modulo.CLIENTES, Accion.ELIMINAR);
            opciones.setVisibility(puedeEditar || puedeEliminar ? View.VISIBLE : View.GONE);
            opciones.setOnClickListener(v -> {
                PopupMenu menu = new PopupMenu(v.getContext(), v);
                menu.inflate(R.menu.menu_acciones);
                menu.getMenu().findItem(R.id.accion_editar).setVisible(puedeEditar);
                menu.getMenu().findItem(R.id.accion_eliminar).setVisible(puedeEliminar);
                menu.setOnMenuItemClickListener(item -> {
                    alElegirAccion.onAccion(cliente, item.getItemId());
                    return true;
                });
                menu.show();
            });
        }

        private String inicialesDe(DatosMaqueta.Cliente cliente) {
            char primera = cliente.nombres.isEmpty() ? '?' : cliente.nombres.charAt(0);
            char segunda = cliente.apellidos.isEmpty() ? ' ' : cliente.apellidos.charAt(0);
            return (String.valueOf(primera) + segunda).trim().toUpperCase();
        }
    }
}
