package com.example.proyectofinalrestaurante.ui.menu;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.ReglasMenu;
import com.example.proyectofinalrestaurante.domain.model.Categoria;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

/**
 * Gestión de categorías del menú (Plan Fase 2a, E6): crear, renombrar, activar/desactivar
 * y borrar.
 *
 * <p>Cada acción cierra el diálogo. Es deliberado: quien lo abrió recarga del servidor
 * después de cada operación, y mantener abierta una lista que ya quedó vieja —con
 * contadores de platillos desactualizados, que son justo lo que decide si una categoría se
 * puede borrar— llevaría a ofrecer acciones que el servidor va a rechazar.</p>
 */
public class CategoriasDialog extends DialogFragment {

    public static final String TAG = "Categorias";

    /** Quien abre el diálogo ejecuta la operación contra el repositorio. */
    public interface AlOperar {
        void onCrearCategoria(String descripcion);

        void onRenombrarCategoria(int idCategoria, String descripcion);

        void onCambiarEstadoCategoria(int idCategoria, boolean activo);

        void onBorrarCategoria(int idCategoria);
    }

    private static final String ARG_IDS = "ids";
    private static final String ARG_NOMBRES = "nombres";
    private static final String ARG_CANTIDADES = "cantidades";
    private static final String ARG_ACTIVAS = "activas";

    private AlOperar alOperar;
    private TextInputEditText campoNueva;
    private TextView textoError;
    private List<Categoria> categorias = new ArrayList<>();

    public static CategoriasDialog crear(List<Categoria> categorias) {
        CategoriasDialog dialogo = new CategoriasDialog();
        Bundle args = new Bundle();
        int[] ids = new int[categorias.size()];
        String[] nombres = new String[categorias.size()];
        int[] cantidades = new int[categorias.size()];
        boolean[] activas = new boolean[categorias.size()];
        for (int i = 0; i < categorias.size(); i++) {
            Categoria categoria = categorias.get(i);
            ids[i] = categoria.getIdLocal();
            nombres[i] = categoria.getDescripcion();
            cantidades[i] = categoria.getCantidadPlatillos();
            activas[i] = categoria.isActivo();
        }
        args.putIntArray(ARG_IDS, ids);
        args.putStringArray(ARG_NOMBRES, nombres);
        args.putIntArray(ARG_CANTIDADES, cantidades);
        args.putBooleanArray(ARG_ACTIVAS, activas);
        dialogo.setArguments(args);
        return dialogo;
    }

    public void setAlOperar(AlOperar alOperar) {
        this.alOperar = alOperar;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        categorias = reconstruirCategorias();

        View vista = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_categorias, null, false);
        campoNueva = vista.findViewById(R.id.txt_categoria_nueva);
        textoError = vista.findViewById(R.id.txt_categorias_error);

        RecyclerView lista = vista.findViewById(R.id.lista_categorias);
        lista.setLayoutManager(new LinearLayoutManager(requireContext()));
        lista.setAdapter(new Adapter(categorias));

        vista.findViewById(R.id.btn_agregar_categoria)
                .setOnClickListener(v -> intentarCrear());

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.menu_titulo_categorias)
                .setView(vista)
                .setNegativeButton(R.string.menu_categoria_cerrar, null)
                .create();
    }

    /** Rearma los objetos de dominio desde los arreglos paralelos del Bundle. */
    private List<Categoria> reconstruirCategorias() {
        Bundle args = requireArguments();
        int[] ids = args.getIntArray(ARG_IDS);
        String[] nombres = args.getStringArray(ARG_NOMBRES);
        int[] cantidades = args.getIntArray(ARG_CANTIDADES);
        boolean[] activas = args.getBooleanArray(ARG_ACTIVAS);

        List<Categoria> reconstruidas = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            // El contador de activos no se usa acá y no se transporta; lo que decide el
            // borrado es el total, que sí viaja. idServidor no viaja: el diálogo opera por
            // idLocal (Plan Fase 2b, §5.5).
            reconstruidas.add(new Categoria(ids[i], null, nombres[i], activas[i],
                    cantidades[i], 0, EstadoSync.SINCRONIZADO));
        }
        return reconstruidas;
    }

    private void intentarCrear() {
        String descripcion = campoNueva.getText() == null
                ? "" : campoNueva.getText().toString().trim();

        if (descripcion.isEmpty()) {
            mostrarError(getString(R.string.menu_error_categoria_vacia));
            return;
        }
        // Espeja el índice único uq_categoria_descripcion, que ignora mayúsculas y
        // espacios de sobra: mejor avisar acá que gastar un viaje de red para un 409.
        if (ReglasMenu.existeOtraCategoriaLlamada(categorias, descripcion, 0)) {
            mostrarError(getString(R.string.menu_error_categoria_repetida));
            return;
        }

        alOperar.onCrearCategoria(descripcion);
        dismiss();
    }

    private void alElegirAccion(Categoria categoria, int accionId) {
        if (accionId == R.id.accion_renombrar_categoria) {
            pedirNuevoNombre(categoria);
        } else if (accionId == R.id.accion_activar_desactivar_categoria) {
            alOperar.onCambiarEstadoCategoria(categoria.getIdLocal(), !categoria.isActivo());
            dismiss();
        } else if (accionId == R.id.accion_borrar_categoria) {
            confirmarBorrado(categoria);
        }
    }

    private void pedirNuevoNombre(Categoria categoria) {
        View vista = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_categorias, null, false);
        vista.findViewById(R.id.lista_categorias).setVisibility(View.GONE);
        vista.findViewById(R.id.btn_agregar_categoria).setVisibility(View.GONE);

        TextInputEditText campo = vista.findViewById(R.id.txt_categoria_nueva);
        campo.setText(categoria.getDescripcion());

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.menu_categoria_renombrar)
                .setView(vista)
                .setPositiveButton(R.string.empleado_guardar, (d, cual) -> {
                    String nuevo = campo.getText() == null
                            ? "" : campo.getText().toString().trim();
                    if (!nuevo.isEmpty() && !nuevo.equals(categoria.getDescripcion())) {
                        alOperar.onRenombrarCategoria(categoria.getIdLocal(), nuevo);
                        dismiss();
                    }
                })
                .setNegativeButton(R.string.empleado_cancelar, null)
                .show();
    }

    private void confirmarBorrado(Categoria categoria) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.menu_categoria_borrar)
                .setMessage(getString(R.string.menu_categoria_confirmar_borrado,
                        categoria.getDescripcion()))
                .setPositiveButton(R.string.menu_categoria_borrar, (d, cual) -> {
                    alOperar.onBorrarCategoria(categoria.getIdLocal());
                    dismiss();
                })
                .setNegativeButton(R.string.empleado_cancelar, null)
                .show();
    }

    private void mostrarError(String mensaje) {
        textoError.setText(mensaje);
        textoError.setVisibility(View.VISIBLE);
    }

    /** Lista de categorías del diálogo. Es interno: no se reusa en ninguna otra pantalla. */
    private final class Adapter extends RecyclerView.Adapter<Adapter.Holder> {

        private final List<Categoria> items;

        Adapter(List<Categoria> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_categoria, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.enlazar(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class Holder extends RecyclerView.ViewHolder {

            private final TextView nombre;
            private final TextView conteo;
            private final TextView estado;
            private final ImageButton opciones;

            Holder(@NonNull View itemView) {
                super(itemView);
                nombre = itemView.findViewById(R.id.txt_nombre_categoria);
                conteo = itemView.findViewById(R.id.txt_conteo_categoria);
                estado = itemView.findViewById(R.id.txt_estado_categoria);
                opciones = itemView.findViewById(R.id.btn_opciones_categoria);
            }

            void enlazar(Categoria categoria) {
                nombre.setText(categoria.getDescripcion());
                conteo.setText(itemView.getResources().getQuantityString(
                        R.plurals.menu_conteo_platillos,
                        categoria.getCantidadPlatillos(), categoria.getCantidadPlatillos()));
                estado.setVisibility(categoria.isActivo() ? View.GONE : View.VISIBLE);

                opciones.setOnClickListener(v -> {
                    PopupMenu menu = new PopupMenu(v.getContext(), v);
                    menu.inflate(R.menu.menu_categoria);

                    MenuItem cambiarEstado =
                            menu.getMenu().findItem(R.id.accion_activar_desactivar_categoria);
                    cambiarEstado.setTitle(categoria.isActivo()
                            ? R.string.accion_desactivar : R.string.accion_activar);

                    // Sin platillos colgando, el servidor acepta el borrado; con platillos
                    // lo rechaza, así que la opción ni se ofrece.
                    menu.getMenu().findItem(R.id.accion_borrar_categoria)
                            .setVisible(ReglasMenu.puedeBorrarse(categoria));

                    menu.setOnMenuItemClickListener(item -> {
                        alElegirAccion(categoria, item.getItemId());
                        return true;
                    });
                    menu.show();
                });
            }
        }
    }
}
