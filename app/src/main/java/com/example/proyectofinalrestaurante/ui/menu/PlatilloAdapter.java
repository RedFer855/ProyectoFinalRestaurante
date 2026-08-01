package com.example.proyectofinalrestaurante.ui.menu;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.Accion;
import com.example.proyectofinalrestaurante.domain.Modulo;
import com.example.proyectofinalrestaurante.domain.model.EstadoSync;
import com.example.proyectofinalrestaurante.domain.model.Platillo;
import com.example.proyectofinalrestaurante.ui.permisos.VistaPorPermiso;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.Objects;

/**
 * Lista de platillos del menú (Plan Fase 2a, E6; tarjeta rediseñada 2026-08-01).
 *
 * <p>Las acciones son botones a la vista dentro de la tarjeta, no un menú ⋮: con solo dos
 * opciones, esconderlas detrás de un menú cuesta un toque de más sin ganar nada. El grupo
 * entero desaparece si el rol no puede hacer ninguna de las dos.</p>
 *
 * <p>Ojo con el nombre: {@link Accion#ELIMINAR} en este módulo significa <b>desactivar</b>,
 * porque el servidor no permite borrar un platillo — el botón dice "Desactivar" o
 * "Reactivar" y nunca "Eliminar".</p>
 */
public class PlatilloAdapter extends ListAdapter<Platillo, PlatilloAdapter.Holder> {

    /** Opacidad de un platillo desactivado: sigue siendo legible pero se distingue. */
    private static final float ALPHA_INACTIVO = 0.45f;
    private static final float ALPHA_ACTIVO = 1f;

    /**
     * El Fragment decide qué hacer con la acción elegida.
     *
     * <p>Un método por acción en vez de un {@code int} de id: el id venía del
     * {@code PopupMenu} que ya no existe, y con dos acciones fijas el compilador puede
     * verificar que las dos estén atendidas.</p>
     */
    public interface AlElegirAccion {
        void onEditarPlatillo(Platillo platillo);

        void onAlternarEstadoPlatillo(Platillo platillo);
    }

    private final AlElegirAccion alElegirAccion;

    public PlatilloAdapter(AlElegirAccion alElegirAccion) {
        super(DIFF);
        this.alElegirAccion = alElegirAccion;
    }

    private static final DiffUtil.ItemCallback<Platillo> DIFF =
            new DiffUtil.ItemCallback<Platillo>() {
                @Override
                public boolean areItemsTheSame(@NonNull Platillo a, @NonNull Platillo b) {
                    return a.getIdLocal() == b.getIdLocal();
                }

                @Override
                public boolean areContentsTheSame(@NonNull Platillo a, @NonNull Platillo b) {
                    return a.getPrecio() == b.getPrecio()
                            && a.isActivo() == b.isActivo()
                            && a.getIdCategoria() == b.getIdCategoria()
                            && a.getEstadoSync() == b.getEstadoSync()
                            && Objects.equals(a.getNombre(), b.getNombre())
                            && Objects.equals(a.getDescripcion(), b.getDescripcion())
                            && Objects.equals(a.getNombreCategoria(), b.getNombreCategoria())
                            && Objects.equals(a.getRutaImagen(), b.getRutaImagen());
                }
            };

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View vista = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_platillo, parent, false);
        return new Holder(vista);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.enlazar(getItem(position), alElegirAccion);
    }

    static class Holder extends RecyclerView.ViewHolder {

        private final ShapeableImageView foto;
        private final TextView nombre;
        private final TextView categoria;
        private final TextView estado;
        private final TextView sincronizacion;
        private final TextView descripcion;
        private final TextView precio;
        private final LinearLayout grupoAcciones;
        private final MaterialButton botonEditar;
        private final MaterialButton botonEstado;

        Holder(@NonNull View itemView) {
            super(itemView);
            foto = itemView.findViewById(R.id.img_platillo);
            nombre = itemView.findViewById(R.id.txt_nombre_platillo);
            categoria = itemView.findViewById(R.id.txt_categoria_platillo);
            estado = itemView.findViewById(R.id.txt_estado_platillo);
            sincronizacion = itemView.findViewById(R.id.txt_sync_platillo);
            descripcion = itemView.findViewById(R.id.txt_descripcion_platillo);
            precio = itemView.findViewById(R.id.txt_precio_platillo);
            grupoAcciones = itemView.findViewById(R.id.grupo_acciones_platillo);
            botonEditar = itemView.findViewById(R.id.btn_editar_platillo);
            botonEstado = itemView.findViewById(R.id.btn_estado_platillo);
        }

        void enlazar(Platillo platillo, AlElegirAccion alElegirAccion) {
            nombre.setText(platillo.getNombre());
            categoria.setText(platillo.getNombreCategoria() == null
                    ? itemView.getContext().getString(R.string.menu_sin_categoria)
                    : platillo.getNombreCategoria());
            precio.setText(itemView.getContext()
                    .getString(R.string.formato_lempiras, platillo.getPrecio()));

            // "Sin subir" mientras el platillo es local y no llegó al servidor (Plan Fase 2b).
            sincronizacion.setVisibility(
                    platillo.getEstadoSync() != EstadoSync.SINCRONIZADO
                            ? View.VISIBLE : View.GONE);

            String textoDescripcion = platillo.getDescripcion();
            descripcion.setText(textoDescripcion);
            descripcion.setVisibility(
                    textoDescripcion == null || textoDescripcion.trim().isEmpty()
                            ? View.GONE : View.VISIBLE);

            // Un platillo desactivado no se esconde: el admin tiene que poder reactivarlo.
            estado.setVisibility(platillo.isActivo() ? View.GONE : View.VISIBLE);
            itemView.setAlpha(platillo.isActivo() ? ALPHA_ACTIVO : ALPHA_INACTIVO);

            cargarFoto(platillo);
            configurarOpciones(platillo, alElegirAccion);
        }

        private void cargarFoto(Platillo platillo) {
            String url = UrlDeImagen.urlDePlatillo(platillo.getRutaImagen());
            // La descripción para lectores de pantalla nombra el platillo: "Foto de X" dice
            // bastante más que "Foto del platillo" repetido en toda la lista.
            foto.setContentDescription(itemView.getContext()
                    .getString(R.string.menu_cd_foto_de, platillo.getNombre()));

            if (url == null) {
                // Sin foto: se limpia cualquier carga anterior, porque el ViewHolder se
                // recicla y podría estar mostrando la imagen de otro platillo.
                Glide.with(foto).clear(foto);
                foto.setImageResource(R.drawable.ic_platillo_sin_foto);
                foto.setPadding(margenDelPlaceholder(), margenDelPlaceholder(),
                        margenDelPlaceholder(), margenDelPlaceholder());
                return;
            }

            foto.setPadding(0, 0, 0, 0);
            Glide.with(foto)
                    .load(url)
                    .placeholder(R.drawable.ic_platillo_sin_foto)
                    .error(R.drawable.ic_platillo_sin_foto)
                    .centerCrop()
                    .into(foto);
        }

        private int margenDelPlaceholder() {
            return itemView.getResources().getDimensionPixelSize(R.dimen.espaciado_campo);
        }

        private void configurarOpciones(Platillo platillo, AlElegirAccion alElegirAccion) {
            boolean puedeEditar = VistaPorPermiso.puede(Modulo.MENU, Accion.EDITAR);
            boolean puedeDesactivar = VistaPorPermiso.puede(Modulo.MENU, Accion.ELIMINAR);

            // Mesero y cocina solo consultan la carta: sin ninguna acción disponible, la
            // fila de botones desaparece entera y la tarjeta se encoge sola.
            grupoAcciones.setVisibility(
                    puedeEditar || puedeDesactivar ? View.VISIBLE : View.GONE);

            botonEditar.setVisibility(puedeEditar ? View.VISIBLE : View.GONE);
            botonEditar.setOnClickListener(
                    puedeEditar ? v -> alElegirAccion.onEditarPlatillo(platillo) : null);

            botonEstado.setVisibility(puedeDesactivar ? View.VISIBLE : View.GONE);
            // La misma acción en los dos sentidos: el texto y el ícono dicen cuál toca.
            botonEstado.setText(platillo.isActivo()
                    ? R.string.accion_desactivar : R.string.accion_activar);
            botonEstado.setIconResource(platillo.isActivo()
                    ? R.drawable.ic_desactivar : R.drawable.ic_reactivar);
            botonEstado.setOnClickListener(
                    puedeDesactivar ? v -> alElegirAccion.onAlternarEstadoPlatillo(platillo) : null);
        }
    }
}
