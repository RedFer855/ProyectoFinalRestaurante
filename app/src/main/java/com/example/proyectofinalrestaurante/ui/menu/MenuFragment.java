package com.example.proyectofinalrestaurante.ui.menu;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.Accion;
import com.example.proyectofinalrestaurante.domain.Modulo;
import com.example.proyectofinalrestaurante.domain.model.Categoria;
import com.example.proyectofinalrestaurante.domain.model.ImagenPlatillo;
import com.example.proyectofinalrestaurante.domain.model.NuevoPlatillo;
import com.example.proyectofinalrestaurante.domain.model.Platillo;
import com.example.proyectofinalrestaurante.ui.permisos.VistaPorPermiso;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

/**
 * Módulo Menú conectado a Supabase (Plan Fase 2a, E6).
 *
 * <p>La misma pantalla para los tres roles: todos ven el catálogo, pero solo {@code admin}
 * ve el botón de agregar, el de categorías y el ⋮ de cada platillo. Eso es experiencia de
 * usuario, no seguridad: quien impide que un mesero cambie un precio es la policy RLS de
 * Postgres, que sigue valiendo aunque alguien modifique el APK.</p>
 */
public class MenuFragment extends Fragment
        implements FormularioPlatilloDialog.AlGuardar, CategoriasDialog.AlOperar,
        PlatilloAdapter.AlElegirAccion {

    private MenuViewModel viewModel;
    private PlatilloAdapter adapter;
    private ChipGroup grupoCategorias;
    private TextView vacio;
    private View progreso;
    private TextView estadoSync;
    private SwipeRefreshLayout refresco;

    /** Categorías con las que se pintaron los chips, para no rearmarlos en cada estado. */
    private List<Categoria> categoriasPintadas;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vacio = view.findViewById(R.id.txt_menu_vacio);
        progreso = view.findViewById(R.id.progress_menu);
        grupoCategorias = view.findViewById(R.id.grupo_categorias);
        estadoSync = view.findViewById(R.id.txt_estado_sync);
        refresco = view.findViewById(R.id.refresco_menu);

        viewModel = new ViewModelProvider(this,
                new MenuViewModelFactory(requireActivity().getApplication()))
                .get(MenuViewModel.class);

        // "Bajar para sincronizar": dispara el drenado del outbox + delta (Plan Fase 2b, §5.6).
        refresco.setOnRefreshListener(() -> viewModel.sincronizar());

        adapter = new PlatilloAdapter(this);
        RecyclerView lista = view.findViewById(R.id.lista_platillos);
        lista.setLayoutManager(new LinearLayoutManager(requireContext()));
        lista.setAdapter(adapter);

        configurarBusqueda(view.findViewById(R.id.txt_buscar_platillo));

        FloatingActionButton fab = view.findViewById(R.id.fab_agregar_platillo);
        VistaPorPermiso.aplicar(fab, Modulo.MENU, Accion.CREAR);
        fab.setOnClickListener(v -> abrirFormulario(null));

        ImageButton botonCategorias = view.findViewById(R.id.btn_categorias);
        VistaPorPermiso.aplicar(botonCategorias, Modulo.MENU, Accion.CREAR);
        botonCategorias.setOnClickListener(v -> abrirCategorias());

        viewModel.getEstado().observe(getViewLifecycleOwner(), this::render);
    }

    private void configurarBusqueda(EditText campo) {
        campo.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                viewModel.buscar(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    // ------------------------------------------------------------------ render

    private void render(EstadoMenu estado) {
        progreso.setVisibility(estado.isCargando() ? View.VISIBLE : View.GONE);
        adapter.submitList(estado.getPlatillos());
        pintarChips(estado);
        actualizarIndicadorSync(estado);

        if (estado.getError() != null) {
            vacio.setVisibility(View.VISIBLE);
            vacio.setText(estado.getError());
            mostrarReintentar(estado.getError());
        } else if (estado.isEsperandoPrimeraCarga()) {
            // Va ANTES de isVacio(): en una instalación desde cero Room emite la lista
            // vacía mucho antes de que conteste el servidor, y anunciar "no hay platillos"
            // ahí es mentirle al usuario sobre un estado que todavía no se conoce.
            vacio.setVisibility(View.VISIBLE);
            vacio.setText(R.string.menu_cargando_primera_vez);
        } else if (estado.isVacio()) {
            vacio.setVisibility(View.VISIBLE);
            vacio.setText(estado.isVacioPorFiltro()
                    ? R.string.menu_vacio : R.string.menu_sin_platillos);
        } else {
            vacio.setVisibility(View.GONE);
        }

        String exito = estado.getMensajeExito();
        if (exito != null) {
            Snackbar.make(requireView(), exito, Snackbar.LENGTH_SHORT).show();
            // Se marca como consumido para que no vuelva a aparecer al rotar (P-013).
            viewModel.onMensajeConsumido();
        }
    }

    /**
     * Indicador global de sincronización (Plan Fase 2b, §4.5): el spinner del
     * SwipeRefreshLayout refleja {@code sincronizando}, y el banner informa cuándo hay
     * cambios locales todavía sin subir o un error permanente.
     */
    private void actualizarIndicadorSync(EstadoMenu estado) {
        refresco.setRefreshing(estado.isSincronizando());
        String mensaje = mensajeDeSync(estado);
        estadoSync.setVisibility(mensaje == null ? View.GONE : View.VISIBLE);
        if (mensaje != null) {
            estadoSync.setText(mensaje);
        }
    }

    @Nullable
    private String mensajeDeSync(EstadoMenu estado) {
        if (estado.getUltimoErrorSync() != null) {
            return estado.getUltimoErrorSync();
        }
        if (estado.isSincronizando()) {
            return getString(R.string.sync_en_proceso);
        }
        if (estado.getCambiosSinSubir() > 0) {
            return getResources().getQuantityString(R.plurals.sync_cambios_sin_subir,
                    estado.getCambiosSinSubir(), estado.getCambiosSinSubir());
        }
        return null;
    }

    /**
     * Los chips se rearman solo cuando las categorías cambian de verdad.
     *
     * <p>El estado {@code cargando} llega sin listas, y repintar en cada operación haría
     * que los chips desaparecieran y volvieran a aparecer: no se toca nada si la lista
     * viene vacía por eso.</p>
     */
    private void pintarChips(EstadoMenu estado) {
        List<Categoria> categorias = estado.getCategorias();
        if (categorias.isEmpty() || mismasCategorias(categorias)) {
            marcarChipSeleccionado(estado.getFiltroCategoria());
            return;
        }

        grupoCategorias.removeAllViews();
        grupoCategorias.addView(crearChip(getString(R.string.filtro_todos), EstadoMenu.SIN_FILTRO));
        for (Categoria categoria : categorias) {
            grupoCategorias.addView(
                    crearChip(categoria.getDescripcion(), categoria.getIdLocal()));
        }
        categoriasPintadas = categorias;
        marcarChipSeleccionado(estado.getFiltroCategoria());
    }

    private boolean mismasCategorias(List<Categoria> categorias) {
        if (categoriasPintadas == null || categoriasPintadas.size() != categorias.size()) {
            return false;
        }
        for (int i = 0; i < categorias.size(); i++) {
            Categoria pintada = categoriasPintadas.get(i);
            Categoria actual = categorias.get(i);
            if (pintada.getIdLocal() != actual.getIdLocal()
                    || !pintada.getDescripcion().equals(actual.getDescripcion())) {
                return false;
            }
        }
        return true;
    }

    /**
     * El chip se infla en vez de construirse con {@code new Chip(context)}: un Chip
     * creado por constructor no toma el estilo, y sin estilo pierde los colores por
     * estado del diseño (activo terracota / inactivo blanco con borde).
     */
    private Chip crearChip(String etiqueta, int idCategoria) {
        Chip chip = (Chip) LayoutInflater.from(requireContext())
                .inflate(R.layout.item_chip_filtro, grupoCategorias, false);
        chip.setText(etiqueta);
        chip.setTag(idCategoria);
        chip.setOnClickListener(v -> viewModel.filtrarPorCategoria(idCategoria));
        return chip;
    }

    /** Deja marcado el chip del filtro vigente — así sobrevive a una rotación. */
    private void marcarChipSeleccionado(int idCategoria) {
        for (int i = 0; i < grupoCategorias.getChildCount(); i++) {
            View hijo = grupoCategorias.getChildAt(i);
            if (hijo instanceof Chip) {
                ((Chip) hijo).setChecked(Integer.valueOf(idCategoria).equals(hijo.getTag()));
            }
        }
    }

    private void mostrarReintentar(String mensaje) {
        Snackbar.make(requireView(), mensaje, Snackbar.LENGTH_LONG)
                .setAction(R.string.empleado_reintentar, v -> viewModel.sincronizar())
                .show();
    }

    // ------------------------------------------------------------------ acciones

    @Override
    public void onEditarPlatillo(Platillo platillo) {
        abrirFormulario(platillo);
    }

    @Override
    public void onAlternarEstadoPlatillo(Platillo platillo) {
        viewModel.cambiarEstadoPlatillo(platillo.getIdLocal(), !platillo.isActivo());
    }

    private void abrirFormulario(@Nullable Platillo platillo) {
        List<Categoria> categorias = viewModel.getTodasLasCategorias();
        if (categorias.isEmpty()) {
            // Sin categorías no se puede crear un platillo: id_categoria es obligatorio.
            Snackbar.make(requireView(), R.string.menu_sin_categorias_aviso,
                    Snackbar.LENGTH_LONG).show();
            return;
        }
        FormularioPlatilloDialog dialogo = platillo == null
                ? FormularioPlatilloDialog.paraCrear(categorias)
                : FormularioPlatilloDialog.paraEditar(platillo, categorias);
        dialogo.setAlGuardar(this);
        dialogo.show(getChildFragmentManager(), FormularioPlatilloDialog.TAG);
    }

    private void abrirCategorias() {
        CategoriasDialog dialogo = CategoriasDialog.crear(viewModel.getTodasLasCategorias());
        dialogo.setAlOperar(this);
        dialogo.show(getChildFragmentManager(), CategoriasDialog.TAG);
    }

    // ------------------------------------------------------------------ callbacks

    /**
     * El Fragment vive más que su vista (por ejemplo en la pila de navegación): conservar
     * referencias a vistas destruidas las mantiene en memoria sin necesidad.
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        vacio = null;
        progreso = null;
        grupoCategorias = null;
        estadoSync = null;
        refresco = null;
        adapter = null;
        categoriasPintadas = null;
    }

    @Override
    public void onCrear(NuevoPlatillo nuevo, @Nullable ImagenPlatillo imagen) {
        viewModel.crearPlatillo(nuevo, imagen);
    }

    @Override
    public void onEditar(int idLocal, NuevoPlatillo editado, @Nullable ImagenPlatillo imagenNueva) {
        viewModel.actualizarPlatillo(idLocal, editado, imagenNueva);
    }

    @Override
    public void onQuitarFoto(int idLocal) {
        viewModel.quitarImagen(idLocal);
    }

    @Override
    public void onCrearCategoria(String descripcion) {
        viewModel.crearCategoria(descripcion);
    }

    @Override
    public void onRenombrarCategoria(int idCategoria, String descripcion) {
        viewModel.renombrarCategoria(idCategoria, descripcion);
    }

    @Override
    public void onCambiarEstadoCategoria(int idCategoria, boolean activo) {
        viewModel.cambiarEstadoCategoria(idCategoria, activo);
    }

    @Override
    public void onBorrarCategoria(int idCategoria) {
        viewModel.borrarCategoria(idCategoria);
    }
}
