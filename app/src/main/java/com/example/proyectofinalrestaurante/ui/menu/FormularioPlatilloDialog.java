package com.example.proyectofinalrestaurante.ui.menu;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.ReglasMenu;
import com.example.proyectofinalrestaurante.domain.ValidadorPlatillo;
import com.example.proyectofinalrestaurante.domain.ValidadorPlatillo.ErrorPlatillo;
import com.example.proyectofinalrestaurante.domain.model.Categoria;
import com.example.proyectofinalrestaurante.domain.model.ImagenPlatillo;
import com.example.proyectofinalrestaurante.domain.model.NuevoPlatillo;
import com.example.proyectofinalrestaurante.domain.model.Platillo;
import com.example.proyectofinalrestaurante.ui.comun.HojaModal;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Formulario de alta y edición de platillos (Plan Fase 2a, E6).
 *
 * <p>Un mismo diálogo para las dos operaciones, igual que {@code FormularioEmpleadoDialog}:
 * lo único que cambia al editar es que los campos vienen llenos y que aparece la opción de
 * quitar la foto guardada.</p>
 *
 * <p>Es una <b>hoja modal inferior</b> y ya no un diálogo centrado, siguiendo el diseño
 * aprobado ("Restaurant App v2.dc.html", bloque {@code modalIsDish}). El cambio es de
 * presentación: la interfaz {@link AlGuardar} no se tocó, así que quien lo abre no se
 * entera. De {@link HojaModal} hereda el abrirse expandida — si no, este formulario
 * abría a medias y los botones de guardar quedaban abajo de la pantalla.</p>
 */
public class FormularioPlatilloDialog extends HojaModal {

    public static final String TAG = "FormularioPlatillo";

    /** Quien abre el diálogo recibe el resultado. */
    public interface AlGuardar {
        void onCrear(NuevoPlatillo nuevo, @Nullable ImagenPlatillo imagen);

        void onEditar(int idLocal, NuevoPlatillo editado, @Nullable ImagenPlatillo imagenNueva);

        void onQuitarFoto(int idLocal);
    }

    private static final String ARG_CATEGORIA_IDS = "categoria_ids";
    private static final String ARG_CATEGORIA_NOMBRES = "categoria_nombres";
    private static final String ARG_PLATILLO_ID = "platillo_id";
    private static final String ARG_NOMBRE = "nombre";
    private static final String ARG_DESCRIPCION = "descripcion";
    private static final String ARG_PRECIO = "precio";
    private static final String ARG_ID_CATEGORIA = "id_categoria";
    private static final String ARG_RUTA_IMAGEN = "ruta_imagen";
    private static final String ARG_ACTIVO = "activo";

    /**
     * El selector de fotos del sistema: no pide <b>ningún</b> permiso de almacenamiento,
     * que es justamente por qué se usa este y no un {@code Intent} a la galería.
     *
     * <p>Se registra como campo a propósito: {@code registerForActivityResult} tiene que
     * llamarse antes de que el Fragment llegue a {@code STARTED}. Hacerlo dentro del
     * {@code onClick} del botón lanza {@code IllegalStateException} y tira la app.</p>
     */
    private final ActivityResultLauncher<PickVisualMediaRequest> selectorDeFoto =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(),
                    this::alElegirFoto);

    /**
     * Comprimir decodifica la foto completa: en un teléfono de gama baja eso son cientos
     * de milisegundos y no puede pasar en el hilo principal. Se apaga en {@code onDestroy}.
     */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler hiloPrincipal = new Handler(Looper.getMainLooper());

    private AlGuardar alGuardar;

    private EditText campoNombre;
    private EditText campoDescripcion;
    private EditText campoPrecio;
    private MaterialAutoCompleteTextView campoCategoria;
    private ShapeableImageView previsualizacion;
    /** Aviso "Tocá para subir una foto"; solo se ve mientras no hay ninguna. */
    private TextView avisoFoto;
    private MaterialButton botonQuitarFoto;
    private TextView textoError;

    private int[] categoriaIds;
    private String[] categoriaNombres;
    private int idCategoriaElegida = ValidadorPlatillo.SIN_CATEGORIA_ELEGIDA;
    /** Foto recién elegida, todavía sin guardar. */
    @Nullable private ImagenPlatillo imagenElegida;

    public static FormularioPlatilloDialog paraCrear(List<Categoria> categorias) {
        FormularioPlatilloDialog dialogo = new FormularioPlatilloDialog();
        dialogo.setArguments(argumentosDeCategorias(categorias));
        return dialogo;
    }

    public static FormularioPlatilloDialog paraEditar(Platillo platillo, List<Categoria> categorias) {
        FormularioPlatilloDialog dialogo = new FormularioPlatilloDialog();
        Bundle args = argumentosDeCategorias(categorias);
        args.putInt(ARG_PLATILLO_ID, platillo.getIdLocal());
        args.putString(ARG_NOMBRE, platillo.getNombre());
        args.putString(ARG_DESCRIPCION, platillo.getDescripcion());
        args.putDouble(ARG_PRECIO, platillo.getPrecio());
        args.putInt(ARG_ID_CATEGORIA, platillo.getIdCategoria());
        args.putString(ARG_RUTA_IMAGEN, platillo.getRutaImagen());
        args.putBoolean(ARG_ACTIVO, platillo.isActivo());
        dialogo.setArguments(args);
        return dialogo;
    }

    /**
     * Las categorías viajan como dos arreglos paralelos y no como objetos: así el diálogo
     * sobrevive a que el sistema lo recree sin depender de quién lo abrió.
     */
    private static Bundle argumentosDeCategorias(List<Categoria> categorias) {
        Bundle args = new Bundle();
        int[] ids = new int[categorias.size()];
        String[] nombres = new String[categorias.size()];
        for (int i = 0; i < categorias.size(); i++) {
            ids[i] = categorias.get(i).getIdLocal();
            nombres[i] = categorias.get(i).getDescripcion();
        }
        args.putIntArray(ARG_CATEGORIA_IDS, ids);
        args.putStringArray(ARG_CATEGORIA_NOMBRES, nombres);
        return args;
    }

    public void setAlGuardar(AlGuardar alGuardar) {
        this.alGuardar = alGuardar;
    }

    private boolean esEdicion() {
        return getArguments() != null && getArguments().containsKey(ARG_PLATILLO_ID);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_platillo, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View vista, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(vista, savedInstanceState);
        enlazarVistas(vista);
    }

    private void enlazarVistas(View vista) {
        campoNombre = vista.findViewById(R.id.txt_platillo_nombre);
        campoDescripcion = vista.findViewById(R.id.txt_platillo_descripcion);
        campoPrecio = vista.findViewById(R.id.txt_platillo_precio);
        campoCategoria = vista.findViewById(R.id.txt_platillo_categoria);
        previsualizacion = vista.findViewById(R.id.img_platillo_previsualizacion);
        avisoFoto = vista.findViewById(R.id.txt_aviso_foto);
        botonQuitarFoto = vista.findViewById(R.id.btn_quitar_foto);
        textoError = vista.findViewById(R.id.txt_platillo_error);

        Bundle args = requireArguments();
        categoriaIds = args.getIntArray(ARG_CATEGORIA_IDS);
        categoriaNombres = args.getStringArray(ARG_CATEGORIA_NOMBRES);
        campoCategoria.setSimpleItems(categoriaNombres);
        campoCategoria.setOnItemClickListener((padre, v, posicion, id) ->
                idCategoriaElegida = categoriaIds[posicion]);
        // Fuera de un TextInputLayout.ExposedDropdownMenu nadie abre la lista por vos: el
        // campo no es editable (inputType="none"), así que sin esto un toque no hace nada.
        campoCategoria.setOnClickListener(v -> campoCategoria.showDropDown());

        vista.findViewById(R.id.btn_elegir_foto).setOnClickListener(v -> abrirSelectorDeFotos());
        // El hueco entero abre el selector, no solo el botón: es un blanco mucho más
        // grande y es lo que la caja punteada está prometiendo con "Tocá para subir".
        vista.findViewById(R.id.caja_foto_platillo)
                .setOnClickListener(v -> abrirSelectorDeFotos());
        botonQuitarFoto.setOnClickListener(v -> quitarFoto());

        // El título y las acciones ahora viven en el layout, no en los botones que antes
        // ponía MaterialAlertDialogBuilder. "Guardar" valida primero: solo cierra la hoja
        // si los datos pasan, igual que hacía el viejo setOnShowListener.
        ((TextView) vista.findViewById(R.id.txt_titulo_platillo)).setText(
                esEdicion() ? R.string.menu_titulo_editar_platillo
                        : R.string.menu_titulo_nuevo_platillo);
        cerrarAlTocar(vista, R.id.btn_cerrar_platillo);
        cerrarAlTocar(vista, R.id.btn_cancelar_platillo);
        vista.findViewById(R.id.btn_guardar_platillo).setOnClickListener(v -> intentarGuardar());

        if (esEdicion()) {
            campoNombre.setText(args.getString(ARG_NOMBRE));
            campoDescripcion.setText(args.getString(ARG_DESCRIPCION));
            // Punto decimal fijo (Locale.US): es lo que espera el teclado numberDecimal
            // y lo que precioIngresado() vuelve a parsear.
            campoPrecio.setText(String.format(Locale.US, "%.2f", args.getDouble(ARG_PRECIO)));
            idCategoriaElegida = args.getInt(ARG_ID_CATEGORIA);
            campoCategoria.setText(nombreDeCategoria(idCategoriaElegida), false);
            mostrarFotoGuardada(args.getString(ARG_RUTA_IMAGEN));
        }
    }

    private String nombreDeCategoria(int idCategoria) {
        for (int i = 0; i < categoriaIds.length; i++) {
            if (categoriaIds[i] == idCategoria) {
                return categoriaNombres[i];
            }
        }
        return "";
    }

    // ------------------------------------------------------------------ foto

    private void abrirSelectorDeFotos() {
        selectorDeFoto.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void alElegirFoto(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        // El Uri es un content://, no una ruta de archivo: se lee por el ContentResolver.
        // Construir un File con él no funciona. El resolver se toma acá, en el hilo
        // principal, porque requireContext() no se puede llamar desde el executor.
        ContentResolver resolver = requireContext().getContentResolver();
        executor.execute(() -> {
            ImagenPlatillo comprimida = CompresorDeImagen.comprimir(resolver, uri);
            hiloPrincipal.post(() -> alTerminarDeComprimir(comprimida, uri));
        });
    }

    private void alTerminarDeComprimir(@Nullable ImagenPlatillo comprimida, Uri uri) {
        if (!isAdded()) {
            return;
        }
        if (comprimida == null) {
            mostrarError(getString(R.string.menu_error_leer_foto));
            return;
        }
        // Se verifica el tamaño acá y no cuando responde el servidor: un 400 después de
        // haber subido el archivo entero por 3G es la peor forma de enterarse.
        if (!ReglasMenu.puedeSubirse(comprimida)) {
            mostrarError(getString(R.string.menu_error_foto_pesada));
            return;
        }

        imagenElegida = comprimida;
        ocultarError();
        mostrarEstadoConFoto();
        Glide.with(this).load(uri).centerCrop().into(previsualizacion);
    }

    private void mostrarFotoGuardada(@Nullable String rutaImagen) {
        String url = UrlDeImagen.urlDePlatillo(rutaImagen);
        if (url == null) {
            return;
        }
        mostrarEstadoConFoto();
        Glide.with(this)
                .load(url)
                .error(R.drawable.ic_platillo_sin_foto)
                .centerCrop()
                .into(previsualizacion);
    }

    /**
     * Hueco con foto: se recorta para llenar, se saca la caja punteada y aparece la
     * opción de quitarla.
     */
    private void mostrarEstadoConFoto() {
        previsualizacion.setScaleType(ImageView.ScaleType.CENTER_CROP);
        previsualizacion.setPadding(0, 0, 0, 0);
        previsualizacion.setBackground(null);
        avisoFoto.setVisibility(View.GONE);
        botonQuitarFoto.setVisibility(View.VISIBLE);
    }

    /**
     * Hueco vacío: caja punteada con el ícono centrado y el aviso de tocar.
     *
     * <p>El {@code CENTER_INSIDE} es lo que arregla la "franja horizontal": el ícono del
     * placeholder es un vector cuadrado de 24dp y con {@code CENTER_CROP} se escalaba
     * hasta cubrir una caja mucho más ancha que alta, de la que solo se veía una banda
     * del medio. Recortar sirve para una foto real, no para un ícono.</p>
     */
    private void mostrarEstadoSinFoto() {
        Glide.with(this).clear(previsualizacion);
        previsualizacion.setImageResource(R.drawable.ic_platillo_sin_foto);
        previsualizacion.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int margen = getResources().getDimensionPixelSize(R.dimen.espaciado_campo);
        previsualizacion.setPadding(margen, margen, margen, margen);
        previsualizacion.setBackgroundResource(R.drawable.bg_foto_vacia);
        avisoFoto.setVisibility(View.VISIBLE);
    }

    /**
     * Si hay una foto recién elegida se descarta solo la selección; si la foto ya está
     * guardada en el servidor, se pide quitarla y el diálogo se cierra.
     */
    private void quitarFoto() {
        if (imagenElegida != null) {
            imagenElegida = null;
            mostrarEstadoSinFoto();
            if (!esEdicion() || requireArguments().getString(ARG_RUTA_IMAGEN) == null) {
                botonQuitarFoto.setVisibility(View.GONE);
            }
            return;
        }
        if (esEdicion() && requireArguments().getString(ARG_RUTA_IMAGEN) != null) {
            alGuardar.onQuitarFoto(requireArguments().getInt(ARG_PLATILLO_ID));
            dismiss();
        }
    }

    // ------------------------------------------------------------------ guardar

    private void intentarGuardar() {
        String nombre = texto(campoNombre);
        String descripcion = texto(campoDescripcion);
        double precio = precioIngresado();

        NuevoPlatillo candidato = new NuevoPlatillo(nombre, descripcion, precio, idCategoriaElegida);
        Set<ErrorPlatillo> errores = ValidadorPlatillo.validar(candidato);
        if (!errores.isEmpty()) {
            mostrarError(getString(mensajeDe(errores)));
            return;
        }

        if (esEdicion()) {
            alGuardar.onEditar(requireArguments().getInt(ARG_PLATILLO_ID), candidato, imagenElegida);
        } else {
            alGuardar.onCrear(candidato, imagenElegida);
        }
        dismiss();
    }

    private double precioIngresado() {
        // El teclado decimal puede dar coma según la configuración regional del teléfono.
        String crudo = texto(campoPrecio).replace(',', '.');
        try {
            return Double.parseDouble(crudo);
        } catch (NumberFormatException ex) {
            // Vacío o ilegible: el validador lo reporta como precio no positivo.
            return 0;
        }
    }

    private int mensajeDe(Set<ErrorPlatillo> errores) {
        if (errores.contains(ErrorPlatillo.NOMBRE_VACIO)) {
            return R.string.menu_error_nombre;
        }
        if (errores.contains(ErrorPlatillo.PRECIO_NO_POSITIVO)) {
            return R.string.menu_error_precio;
        }
        return R.string.menu_error_categoria;
    }

    private void mostrarError(String mensaje) {
        textoError.setText(mensaje);
        textoError.setVisibility(View.VISIBLE);
    }

    private void ocultarError() {
        textoError.setVisibility(View.GONE);
    }

    private String texto(EditText campo) {
        return campo.getText() == null ? "" : campo.getText().toString().trim();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        hiloPrincipal.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }
}
