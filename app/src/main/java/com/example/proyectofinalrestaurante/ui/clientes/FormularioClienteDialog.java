package com.example.proyectofinalrestaurante.ui.clientes;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.ValidadorCliente;
import com.example.proyectofinalrestaurante.domain.model.Cliente;
import com.example.proyectofinalrestaurante.domain.model.NuevoCliente;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Set;

/**
 * Formulario de alta y edición de clientes (Plan Fase 2d, E6). Mismo patrón que
 * {@code FormularioMesaDialog}: un solo diálogo para las dos operaciones.
 */
public class FormularioClienteDialog extends DialogFragment {

    public static final String TAG = "FormularioCliente";

    public interface AlGuardar {
        void onCrear(NuevoCliente nuevo);

        void onEditar(int idLocal, NuevoCliente datos);
    }

    private static final String ARG_ID_LOCAL = "id_local";
    private static final String ARG_NOMBRE = "nombre";
    private static final String ARG_APELLIDO = "apellido";
    private static final String ARG_IDENTIDAD = "identidad";
    private static final String ARG_TELEFONO = "telefono";

    private AlGuardar alGuardar;

    private TextInputEditText campoNombre;
    private TextInputEditText campoApellido;
    private TextInputEditText campoIdentidad;
    private TextInputEditText campoTelefono;
    private TextView textoError;

    public static FormularioClienteDialog paraCrear() {
        return new FormularioClienteDialog();
    }

    public static FormularioClienteDialog paraEditar(Cliente cliente) {
        FormularioClienteDialog dialogo = new FormularioClienteDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_ID_LOCAL, cliente.getIdLocal());
        args.putString(ARG_NOMBRE, cliente.getNombre());
        args.putString(ARG_APELLIDO, cliente.getApellido());
        args.putString(ARG_IDENTIDAD, cliente.getIdentidad());
        args.putString(ARG_TELEFONO, cliente.getTelefono());
        dialogo.setArguments(args);
        return dialogo;
    }

    public void setAlGuardar(AlGuardar alGuardar) {
        this.alGuardar = alGuardar;
    }

    private boolean esEdicion() {
        return getArguments() != null && getArguments().containsKey(ARG_ID_LOCAL);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View vista = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_cliente, null, false);
        enlazarVistas(vista);

        AlertDialog dialogo = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(esEdicion() ? R.string.cliente_titulo_editar : R.string.cliente_titulo_nuevo)
                .setView(vista)
                .setPositiveButton(R.string.cliente_guardar, null)
                .setNegativeButton(R.string.cliente_cancelar, null)
                .create();

        dialogo.setOnShowListener(d ->
                dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> intentarGuardar()));
        return dialogo;
    }

    private void enlazarVistas(View vista) {
        campoNombre = vista.findViewById(R.id.txt_form_nombre_cliente);
        campoApellido = vista.findViewById(R.id.txt_form_apellido_cliente);
        campoIdentidad = vista.findViewById(R.id.txt_form_identidad_cliente);
        campoTelefono = vista.findViewById(R.id.txt_form_telefono_cliente);
        textoError = vista.findViewById(R.id.txt_form_error_cliente);

        if (esEdicion()) {
            Bundle args = requireArguments();
            campoNombre.setText(args.getString(ARG_NOMBRE));
            campoApellido.setText(args.getString(ARG_APELLIDO));
            campoIdentidad.setText(args.getString(ARG_IDENTIDAD));
            campoTelefono.setText(args.getString(ARG_TELEFONO));
        }
    }

    private void intentarGuardar() {
        String nombre = texto(campoNombre);
        String apellido = texto(campoApellido);
        String identidad = texto(campoIdentidad);
        String telefono = texto(campoTelefono);

        NuevoCliente nuevo = new NuevoCliente(nombre, apellido,
                identidad.isEmpty() ? null : identidad, telefono.isEmpty() ? null : telefono);
        Set<ValidadorCliente.ErrorCliente> errores = ValidadorCliente.validar(nuevo);
        if (!errores.isEmpty()) {
            mostrarError(getString(primerMensajeDe(errores)));
            return;
        }

        if (esEdicion()) {
            alGuardar.onEditar(requireArguments().getInt(ARG_ID_LOCAL), nuevo);
        } else {
            alGuardar.onCrear(nuevo);
        }
        dismiss();
    }

    private int primerMensajeDe(Set<ValidadorCliente.ErrorCliente> errores) {
        if (errores.contains(ValidadorCliente.ErrorCliente.NOMBRE_OBLIGATORIO)) {
            return R.string.cliente_error_nombre;
        }
        if (errores.contains(ValidadorCliente.ErrorCliente.APELLIDO_OBLIGATORIO)) {
            return R.string.cliente_error_apellido;
        }
        return R.string.cliente_error_identidad;
    }

    private void mostrarError(String mensaje) {
        textoError.setText(mensaje);
        textoError.setVisibility(View.VISIBLE);
    }

    private String texto(TextInputEditText campo) {
        return campo.getText() == null ? "" : campo.getText().toString().trim();
    }
}
