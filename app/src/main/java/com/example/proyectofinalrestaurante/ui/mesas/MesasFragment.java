package com.example.proyectofinalrestaurante.ui.mesas;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.Accion;
import com.example.proyectofinalrestaurante.domain.Modulo;
import com.example.proyectofinalrestaurante.domain.model.EstadoMesa;
import com.example.proyectofinalrestaurante.domain.model.Mesa;
import com.example.proyectofinalrestaurante.domain.model.NuevaMesa;
import com.example.proyectofinalrestaurante.ui.permisos.VistaPorPermiso;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Arrays;
import java.util.List;

/**
 * Módulo Mesas conectado a Supabase (Plan Fase 2c, E6). Cocina no accede: ni siquiera
 * aparece en su menú lateral, y del lado del servidor la RLS bloquea la tabla `mesa` aunque
 * alguien modifique el APK.
 *
 * <p>Un toque en la tarjeta abre el selector de estado — la acción principal, la que el
 * mesero hace cincuenta veces por turno. El ⋮ (solo quien tiene {@code EDITAR}) abre editar
 * y dar de baja/reactivar.</p>
 */
public class MesasFragment extends Fragment implements FormularioMesaDialog.AlGuardar {

    private MesasViewModel viewModel;
    private MesaAdapter adapter;
    private TextView vacio;
    private View progreso;
    private TextView estadoSync;
    private SwipeRefreshLayout refresco;
    private ChipGroup grupoEstados;
    private boolean chipsConstruidos = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mesas, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vacio = view.findViewById(R.id.txt_mesas_vacio);
        progreso = view.findViewById(R.id.progress_mesas);
        estadoSync = view.findViewById(R.id.txt_estado_sync_mesas);
        refresco = view.findViewById(R.id.refresco_mesas);
        grupoEstados = view.findViewById(R.id.grupo_estados_mesa);

        viewModel = new ViewModelProvider(this,
                new MesasViewModelFactory(requireActivity().getApplication()))
                .get(MesasViewModel.class);

        refresco.setOnRefreshListener(() -> viewModel.sincronizar());

        adapter = new MesaAdapter(this::abrirSelectorDeEstado, this::alElegirAccion);
        RecyclerView lista = view.findViewById(R.id.lista_mesas);
        int columnas = getResources().getInteger(R.integer.columnas_mesas);
        lista.setLayoutManager(new GridLayoutManager(requireContext(), columnas));
        lista.setAdapter(adapter);

        TextInputEditText campo = view.findViewById(R.id.txt_buscar_mesa);
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

        ExtendedFloatingActionButton fab = view.findViewById(R.id.fab_agregar_mesa);
        VistaPorPermiso.aplicar(fab, Modulo.MESAS, Accion.CREAR);
        fab.setOnClickListener(v -> abrirFormulario(null));

        viewModel.getEstado().observe(getViewLifecycleOwner(), this::render);
    }

    private void render(EstadoMesas estado) {
        progreso.setVisibility(estado.isCargando() ? View.VISIBLE : View.GONE);
        adapter.submitList(estado.getMesas());
        actualizarIndicadorSync(estado);
        construirChipsDeEstado(estado);

        // La lista sale de Room: un error puntual nunca la vacía.
        if (estado.isVacio()) {
            vacio.setVisibility(View.VISIBLE);
            vacio.setText(estado.isVacioPorFiltro() ? R.string.mesas_sin_coincidencias
                    : R.string.mesas_vacio);
        } else {
            vacio.setVisibility(View.GONE);
        }

        String error = estado.getError();
        if (error != null) {
            Snackbar.make(requireView(), error, Snackbar.LENGTH_LONG).show();
            viewModel.onErrorConsumido();
        }

        String exito = estado.getMensajeExito();
        if (exito != null) {
            Snackbar.make(requireView(), exito, Snackbar.LENGTH_SHORT).show();
            viewModel.onMensajeConsumido();
        }
    }

    /** Los chips salen del catálogo real (Room), no de un enum fijo en el Fragment. */
    private void construirChipsDeEstado(EstadoMesas estado) {
        if (chipsConstruidos || estado.getEstadosDisponibles().isEmpty()) {
            return;
        }
        chipsConstruidos = true;
        grupoEstados.removeAllViews();
        grupoEstados.addView(crearChip(getString(R.string.filtro_todos), null, true));
        for (EstadoMesa disponible : estado.getEstadosDisponibles()) {
            grupoEstados.addView(crearChip(getString(EstadoMesaUi.etiqueta(disponible)),
                    disponible, false));
        }
    }

    private Chip crearChip(String etiqueta, @Nullable EstadoMesa valor, boolean seleccionado) {
        Chip chip = new Chip(requireContext());
        chip.setText(etiqueta);
        chip.setCheckable(true);
        chip.setChecked(seleccionado);
        chip.setMinHeight(getResources().getDimensionPixelSize(R.dimen.altura_minima_tactil));
        chip.setOnClickListener(v -> viewModel.filtrarPorEstado(valor));
        return chip;
    }

    private void actualizarIndicadorSync(EstadoMesas estado) {
        refresco.setRefreshing(estado.isSincronizando());
        String mensaje = mensajeDeSync(estado);
        estadoSync.setVisibility(mensaje == null ? View.GONE : View.VISIBLE);
        if (mensaje != null) {
            estadoSync.setText(mensaje);
        }
    }

    @Nullable
    private String mensajeDeSync(EstadoMesas estado) {
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

    /** Un toque en la tarjeta: selector de estado, la acción que el mesero usa todo el turno. */
    private void abrirSelectorDeEstado(Mesa mesa) {
        List<EstadoMesa> disponibles = Arrays.asList(EstadoMesa.values());
        String[] etiquetas = new String[disponibles.size()];
        int actual = 0;
        for (int i = 0; i < disponibles.size(); i++) {
            etiquetas[i] = getString(EstadoMesaUi.etiqueta(disponibles.get(i)));
            if (disponibles.get(i) == mesa.getEstado()) {
                actual = i;
            }
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.mesa_titulo_cambiar_estado, mesa.getNumeroMesa()))
                .setSingleChoiceItems(etiquetas, actual, (d, cual) -> {
                    d.dismiss();
                    EstadoMesa elegido = disponibles.get(cual);
                    if (elegido != mesa.getEstado()) {
                        viewModel.cambiarEstado(mesa, elegido);
                    }
                })
                .setNegativeButton(R.string.mesa_cancelar, null)
                .show();
    }

    private void alElegirAccion(Mesa mesa, int accionId) {
        if (accionId == R.id.accion_editar_mesa) {
            abrirFormulario(mesa);
        } else if (accionId == R.id.accion_activar_desactivar_mesa) {
            viewModel.cambiarBaja(mesa, !mesa.isActivo());
        }
    }

    private void abrirFormulario(@Nullable Mesa mesa) {
        FormularioMesaDialog dialogo =
                mesa == null ? FormularioMesaDialog.paraCrear() : FormularioMesaDialog.paraEditar(mesa);
        dialogo.setAlGuardar(this);
        dialogo.setMesasExistentes(viewModel.getTodasLasMesas());
        dialogo.show(getChildFragmentManager(), FormularioMesaDialog.TAG);
    }

    @Override
    public void onCrear(NuevaMesa nueva) {
        viewModel.crear(nueva);
    }

    @Override
    public void onEditar(int idLocal, NuevaMesa datos) {
        viewModel.actualizar(idLocal, datos);
    }
}
