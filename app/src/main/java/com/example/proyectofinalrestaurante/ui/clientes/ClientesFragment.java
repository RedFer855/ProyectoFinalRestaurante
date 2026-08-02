package com.example.proyectofinalrestaurante.ui.clientes;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.Accion;
import com.example.proyectofinalrestaurante.domain.Modulo;
import com.example.proyectofinalrestaurante.domain.model.Cliente;
import com.example.proyectofinalrestaurante.domain.model.NuevoCliente;
import com.example.proyectofinalrestaurante.ui.permisos.VistaPorPermiso;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Módulo Clientes conectado a Supabase (Plan Fase 2d, E6). Cocina no accede: la matriz de
 * permisos no le da el módulo, y del lado del servidor la RLS le niega la lectura aunque
 * alguien modifique el APK.
 */
public class ClientesFragment extends Fragment implements FormularioClienteDialog.AlGuardar {

    private ClientesViewModel viewModel;
    private ClienteAdapter adapter;
    private TextView vacio;
    private View progreso;
    private TextView estadoSync;
    private SwipeRefreshLayout refresco;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_clientes, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vacio = view.findViewById(R.id.txt_clientes_vacio);
        progreso = view.findViewById(R.id.progress_clientes);
        estadoSync = view.findViewById(R.id.txt_estado_sync_clientes);
        refresco = view.findViewById(R.id.refresco_clientes);

        viewModel = new ViewModelProvider(this,
                new ClientesViewModelFactory(requireActivity().getApplication()))
                .get(ClientesViewModel.class);

        refresco.setOnRefreshListener(() -> viewModel.sincronizar());

        adapter = new ClienteAdapter(new ClienteAdapter.AlElegirAccion() {
            @Override
            public void onEditar(Cliente cliente) {
                abrirFormulario(cliente);
            }

            @Override
            public void onReactivar(Cliente cliente) {
                viewModel.cambiarBaja(cliente, true);
            }

            @Override
            public void onDarDeBaja(Cliente cliente) {
                viewModel.cambiarBaja(cliente, false);
            }

            @Override
            public void onBorrar(Cliente cliente) {
                confirmarBorrado(cliente);
            }
        });

        RecyclerView lista = view.findViewById(R.id.lista_clientes);
        lista.setLayoutManager(new LinearLayoutManager(requireContext()));
        lista.setAdapter(adapter);

        configurarFiltroEstado(view.findViewById(R.id.grupo_estado_cliente));

        TextInputEditText campo = view.findViewById(R.id.txt_buscar_cliente);
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

        ExtendedFloatingActionButton fab = view.findViewById(R.id.fab_agregar_cliente);
        VistaPorPermiso.aplicar(fab, Modulo.CLIENTES, Accion.CREAR);
        fab.setOnClickListener(v -> abrirFormulario(null));

        viewModel.getEstado().observe(getViewLifecycleOwner(), this::render);
    }

    /** Chips fijos: activo/inactivo es un booleano, no un catálogo que pedirle al servidor. */
    private void configurarFiltroEstado(ChipGroup grupo) {
        grupo.addView(crearChip(getString(R.string.filtro_todos), null, true));
        grupo.addView(crearChip(getString(R.string.clientes_activo), true, false));
        grupo.addView(crearChip(getString(R.string.clientes_inactivo), false, false));
    }

    private Chip crearChip(String etiqueta, @Nullable Boolean valor, boolean seleccionado) {
        Chip chip = new Chip(requireContext());
        chip.setText(etiqueta);
        chip.setCheckable(true);
        chip.setChecked(seleccionado);
        chip.setMinHeight(getResources().getDimensionPixelSize(R.dimen.altura_minima_tactil));
        chip.setOnClickListener(v -> viewModel.filtrarPorActivo(valor));
        return chip;
    }

    private void render(EstadoClientes estado) {
        progreso.setVisibility(estado.isCargando() ? View.VISIBLE : View.GONE);
        adapter.submitList(estado.getClientes());
        actualizarIndicadorSync(estado);

        if (estado.isVacio()) {
            vacio.setVisibility(View.VISIBLE);
            vacio.setText(estado.isVacioPorFiltro()
                    ? R.string.clientes_sin_coincidencias : R.string.clientes_vacio);
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

    private void actualizarIndicadorSync(EstadoClientes estado) {
        refresco.setRefreshing(estado.isSincronizando());
        String mensaje = mensajeDeSync(estado);
        estadoSync.setVisibility(mensaje == null ? View.GONE : View.VISIBLE);
        if (mensaje != null) {
            estadoSync.setText(mensaje);
        }
    }

    @Nullable
    private String mensajeDeSync(EstadoClientes estado) {
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

    private void confirmarBorrado(Cliente cliente) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.accion_eliminar)
                .setMessage(getString(R.string.cliente_confirmar_borrado, cliente.nombreCompleto()))
                .setPositiveButton(R.string.accion_eliminar, (d, cual) -> viewModel.borrar(cliente))
                .setNegativeButton(R.string.cliente_cancelar, null)
                .show();
    }

    private void abrirFormulario(@Nullable Cliente cliente) {
        FormularioClienteDialog dialogo = cliente == null
                ? FormularioClienteDialog.paraCrear() : FormularioClienteDialog.paraEditar(cliente);
        dialogo.setAlGuardar(this);
        dialogo.show(getChildFragmentManager(), FormularioClienteDialog.TAG);
    }

    @Override
    public void onCrear(NuevoCliente nuevo) {
        viewModel.crear(nuevo);
    }

    @Override
    public void onEditar(int idLocal, NuevoCliente datos) {
        viewModel.actualizar(idLocal, datos);
    }
}
