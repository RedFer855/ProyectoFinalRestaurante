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
import com.example.proyectofinalrestaurante.domain.model.Cliente;
import com.example.proyectofinalrestaurante.ui.permisos.VistaPorPermiso;

/**
 * Lista de clientes (Fase 2d). Mesero puede editar; solo admin puede eliminar.
 */
public class ClienteAdapter extends ListAdapter<Cliente, ClienteAdapter.Holder> {

    public interface AlElegirAccion {
        void onAccion(Cliente cliente, int accionId);
    }

    private final AlElegirAccion alElegirAccion;

    public ClienteAdapter(AlElegirAccion alElegirAccion) {
        super(DIFF);
        this.alElegirAccion = alElegirAccion;
    }

    private static final DiffUtil.ItemCallback<Cliente> DIFF =
            new DiffUtil.ItemCallback<Cliente>() {
                @Override
                public boolean areItemsTheSame(@NonNull Cliente a, @NonNull Cliente b) {
                    return a.getIdLocal() == b.getIdLocal();
                }

                @Override
                public boolean areContentsTheSame(@NonNull Cliente a, @NonNull Cliente b) {
                    return a.equals(b);
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

        void enlazar(Cliente cliente, AlElegirAccion alElegirAccion) {
            nombre.setText(completo(cliente));
            iniciales.setText(inicialesDe(cliente));
            identidad.setText(cliente.getIdentidad() != null
                    ? cliente.getIdentidad()
                    : itemView.getContext().getString(R.string.clientes_sin_identidad));
            telefono.setText(cliente.getTelefono());

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

        private static String completo(Cliente c) {
            String n = c.getNombre() == null ? "" : c.getNombre();
            String a = c.getApellido() == null ? "" : c.getApellido();
            String completo = (n + " " + a).trim();
            return completo.isEmpty() ? "?" : completo;
        }

        private static String inicialesDe(Cliente c) {
            String n = c.getNombre() == null ? "" : c.getNombre();
            String a = c.getApellido() == null ? "" : c.getApellido();
            char primera = n.isEmpty() ? '?' : n.charAt(0);
            char segunda = a.isEmpty() ? ' ' : a.charAt(0);
            return (String.valueOf(primera) + segunda).trim().toUpperCase();
        }
    }
}
