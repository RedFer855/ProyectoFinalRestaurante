package com.example.proyectofinalrestaurante.ui.empleados;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.Permisos;
import com.example.proyectofinalrestaurante.domain.RequisitoContrasenia;
import com.example.proyectofinalrestaurante.domain.ResultadoValidacion;
import com.example.proyectofinalrestaurante.domain.ValidadorContrasenia;
import com.example.proyectofinalrestaurante.domain.model.Empleado;
import com.example.proyectofinalrestaurante.domain.model.NuevoEmpleado;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

/**
 * Formulario de alta y edición de empleados (Plan Fase 1d, E5).
 *
 * <p>Un mismo diálogo para las dos operaciones: lo único que cambia es que al crear
 * aparece la contraseña temporal y se puede elegir el rol. Al editar, la contraseña no
 * se toca (para eso está el flujo de recuperación) y el rol se cambia desde su propia
 * opción del menú ⋮, que tiene reglas más estrictas.</p>
 */
public class FormularioEmpleadoDialog extends DialogFragment {

    public static final String TAG = "FormularioEmpleado";

    /** Quien abre el diálogo recibe el resultado. */
    public interface AlGuardar {
        void onCrear(NuevoEmpleado nuevo);

        void onEditar(Empleado editado);
    }

    private static final String ARG_EMPLEADO_ID = "empleado_id";
    private static final String ARG_NOMBRES = "nombres";
    private static final String ARG_APELLIDOS = "apellidos";
    private static final String ARG_IDENTIDAD = "identidad";
    private static final String ARG_TELEFONO = "telefono";
    private static final String ARG_CORREO = "correo";
    private static final String ARG_ROL = "rol";
    private static final String ARG_AUTH = "id_auth";

    private AlGuardar alGuardar;

    private TextInputEditText campoNombres;
    private TextInputEditText campoApellidos;
    private TextInputEditText campoIdentidad;
    private TextInputEditText campoTelefono;
    private TextInputEditText campoCorreo;
    private MaterialAutoCompleteTextView campoRol;
    private TextInputEditText campoContrasenia;
    private TextView textoRequisitos;
    private TextView textoError;

    public static FormularioEmpleadoDialog paraCrear() {
        return new FormularioEmpleadoDialog();
    }

    public static FormularioEmpleadoDialog paraEditar(Empleado empleado) {
        FormularioEmpleadoDialog dialogo = new FormularioEmpleadoDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_EMPLEADO_ID, empleado.getIdEmpleado());
        args.putString(ARG_NOMBRES, empleado.getNombres());
        args.putString(ARG_APELLIDOS, empleado.getApellidos());
        args.putString(ARG_IDENTIDAD, empleado.getIdentidad());
        args.putString(ARG_TELEFONO, empleado.getTelefono());
        args.putString(ARG_CORREO, empleado.getCorreo());
        args.putString(ARG_ROL, empleado.getRol());
        args.putString(ARG_AUTH, empleado.getIdAuthUser());
        dialogo.setArguments(args);
        return dialogo;
    }

    public void setAlGuardar(AlGuardar alGuardar) {
        this.alGuardar = alGuardar;
    }

    private boolean esEdicion() {
        return getArguments() != null && getArguments().containsKey(ARG_EMPLEADO_ID);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View vista = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_empleado, null, false);
        enlazarVistas(vista);

        AlertDialog dialogo = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(esEdicion() ? R.string.empleado_titulo_editar
                        : R.string.empleado_titulo_nuevo)
                .setView(vista)
                .setPositiveButton(R.string.empleado_guardar, null)
                .setNegativeButton(R.string.empleado_cancelar, null)
                .create();

        // El listener se asigna después de mostrar para poder validar sin que el
        // diálogo se cierre solo cuando los datos están mal.
        dialogo.setOnShowListener(d ->
                dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> intentarGuardar()));
        return dialogo;
    }

    private void enlazarVistas(View vista) {
        campoNombres = vista.findViewById(R.id.txt_form_nombres);
        campoApellidos = vista.findViewById(R.id.txt_form_apellidos);
        campoIdentidad = vista.findViewById(R.id.txt_form_identidad);
        campoTelefono = vista.findViewById(R.id.txt_form_telefono);
        campoCorreo = vista.findViewById(R.id.txt_form_correo);
        campoRol = vista.findViewById(R.id.txt_form_rol);
        campoContrasenia = vista.findViewById(R.id.txt_form_contrasenia);
        textoRequisitos = vista.findViewById(R.id.txt_requisitos_contrasenia);
        textoError = vista.findViewById(R.id.txt_form_error);

        String[] roles = {Permisos.ROL_ADMIN, Permisos.ROL_MESERO, Permisos.ROL_COCINA};
        campoRol.setSimpleItems(roles);

        if (esEdicion()) {
            Bundle args = requireArguments();
            campoNombres.setText(args.getString(ARG_NOMBRES));
            campoApellidos.setText(args.getString(ARG_APELLIDOS));
            campoIdentidad.setText(args.getString(ARG_IDENTIDAD));
            campoTelefono.setText(args.getString(ARG_TELEFONO));
            campoCorreo.setText(args.getString(ARG_CORREO));
            campoRol.setText(args.getString(ARG_ROL), false);
            // Al editar no se cambian ni el rol ni la contraseña desde acá.
            vista.findViewById(R.id.til_form_rol).setEnabled(false);
            vista.findViewById(R.id.bloque_contrasenia).setVisibility(View.GONE);
        } else {
            campoRol.setText(Permisos.ROL_MESERO, false);
            configurarValidacionEnVivo();
        }
    }

    /** Muestra en vivo qué le falta a la contraseña, reusando ValidadorContrasenia. */
    private void configurarValidacionEnVivo() {
        campoContrasenia.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                mostrarRequisitos(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        mostrarRequisitos("");
    }

    private void mostrarRequisitos(String contrasenia) {
        ResultadoValidacion validacion = ValidadorContrasenia.validar(contrasenia);
        if (validacion.esValida()) {
            textoRequisitos.setText(R.string.empleado_requisitos_ok);
            return;
        }
        List<String> faltantes = new ArrayList<>();
        for (RequisitoContrasenia requisito : validacion.getIncumplidos()) {
            faltantes.add(getString(etiquetaDe(requisito)));
        }
        textoRequisitos.setText(
                getString(R.string.empleado_requisitos_falta, String.join(", ", faltantes)));
    }

    private int etiquetaDe(RequisitoContrasenia requisito) {
        switch (requisito) {
            case LONGITUD_MINIMA:
                return R.string.empleado_req_longitud;
            case MAYUSCULA:
                return R.string.empleado_req_mayuscula;
            case MINUSCULA:
                return R.string.empleado_req_minuscula;
            case DIGITO:
                return R.string.empleado_req_digito;
            default:
                return R.string.empleado_req_simbolo;
        }
    }

    private void intentarGuardar() {
        String nombres = texto(campoNombres);
        String apellidos = texto(campoApellidos);
        String identidad = texto(campoIdentidad);
        String telefono = texto(campoTelefono);
        String correo = texto(campoCorreo).toLowerCase();
        String rol = campoRol.getText().toString().trim().toLowerCase();

        if (nombres.isEmpty() || apellidos.isEmpty() || identidad.isEmpty() || correo.isEmpty()) {
            mostrarError(getString(R.string.empleado_error_campos));
            return;
        }
        if (!correo.contains("@")) {
            mostrarError(getString(R.string.empleado_error_correo));
            return;
        }

        if (esEdicion()) {
            Bundle args = requireArguments();
            alGuardar.onEditar(new Empleado(
                    args.getInt(ARG_EMPLEADO_ID), nombres, apellidos, identidad,
                    telefono.isEmpty() ? null : telefono, correo,
                    0, "", args.getString(ARG_AUTH), args.getString(ARG_ROL), true));
            dismiss();
            return;
        }

        String contrasenia = texto(campoContrasenia);
        if (!ValidadorContrasenia.validar(contrasenia).esValida()) {
            mostrarError(getString(R.string.empleado_requisitos_falta,
                    getString(R.string.empleado_req_longitud)));
            return;
        }

        alGuardar.onCrear(new NuevoEmpleado(nombres, apellidos, identidad,
                telefono.isEmpty() ? null : telefono, correo, rol, contrasenia));
        dismiss();
    }

    private void mostrarError(String mensaje) {
        textoError.setText(mensaje);
        textoError.setVisibility(View.VISIBLE);
    }

    private String texto(TextInputEditText campo) {
        return campo.getText() == null ? "" : campo.getText().toString().trim();
    }
}
