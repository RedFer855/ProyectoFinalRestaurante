package com.example.proyectofinalrestaurante.ui.mesas;

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
import com.example.proyectofinalrestaurante.domain.ReglasMesa;
import com.example.proyectofinalrestaurante.domain.ValidadorMesa;
import com.example.proyectofinalrestaurante.domain.model.Mesa;
import com.example.proyectofinalrestaurante.domain.model.NuevaMesa;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Formulario de alta y edición de mesas (Plan Fase 2c, E6). Mismo patrón que
 * {@code FormularioEmpleadoDialog}: un solo diálogo para las dos operaciones. No pregunta
 * estado ni baja: el estado se cambia desde la tarjeta y la baja desde el menú ⋮.
 */
public class FormularioMesaDialog extends DialogFragment {

    public static final String TAG = "FormularioMesa";

    public interface AlGuardar {
        void onCrear(NuevaMesa nueva);

        void onEditar(int idLocal, NuevaMesa datos);
    }

    private static final String ARG_ID_LOCAL = "id_local";
    private static final String ARG_NUMERO = "numero";
    private static final String ARG_CAPACIDAD = "capacidad";
    private static final String ARG_UBICACION = "ubicacion";

    private AlGuardar alGuardar;
    private List<Mesa> mesasExistentes = Collections.emptyList();

    private TextInputEditText campoNumero;
    private TextInputEditText campoCapacidad;
    private TextInputEditText campoUbicacion;
    private TextView textoError;

    public static FormularioMesaDialog paraCrear() {
        return new FormularioMesaDialog();
    }

    public static FormularioMesaDialog paraEditar(Mesa mesa) {
        FormularioMesaDialog dialogo = new FormularioMesaDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_ID_LOCAL, mesa.getIdLocal());
        args.putInt(ARG_NUMERO, mesa.getNumeroMesa());
        args.putInt(ARG_CAPACIDAD, mesa.getCapacidad());
        args.putString(ARG_UBICACION, mesa.getUbicacion());
        dialogo.setArguments(args);
        return dialogo;
    }

    public void setAlGuardar(AlGuardar alGuardar) {
        this.alGuardar = alGuardar;
    }

    /** Lista completa (sin filtrar) para chequear números duplicados antes de mandar al servidor. */
    public void setMesasExistentes(List<Mesa> mesas) {
        this.mesasExistentes = mesas;
    }

    private boolean esEdicion() {
        return getArguments() != null && getArguments().containsKey(ARG_ID_LOCAL);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View vista = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_mesa, null, false);
        enlazarVistas(vista);

        AlertDialog dialogo = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(esEdicion() ? R.string.mesa_titulo_editar : R.string.mesa_titulo_nuevo)
                .setView(vista)
                .setPositiveButton(R.string.mesa_guardar, null)
                .setNegativeButton(R.string.mesa_cancelar, null)
                .create();

        // El listener se asigna después de mostrar para poder validar sin que el diálogo se
        // cierre solo cuando los datos están mal (mismo truco que FormularioEmpleadoDialog).
        dialogo.setOnShowListener(d ->
                dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> intentarGuardar()));
        return dialogo;
    }

    private void enlazarVistas(View vista) {
        campoNumero = vista.findViewById(R.id.txt_form_numero_mesa);
        campoCapacidad = vista.findViewById(R.id.txt_form_capacidad_mesa);
        campoUbicacion = vista.findViewById(R.id.txt_form_ubicacion_mesa);
        textoError = vista.findViewById(R.id.txt_form_error_mesa);

        if (esEdicion()) {
            Bundle args = requireArguments();
            campoNumero.setText(String.valueOf(args.getInt(ARG_NUMERO)));
            campoCapacidad.setText(String.valueOf(args.getInt(ARG_CAPACIDAD)));
            campoUbicacion.setText(args.getString(ARG_UBICACION));
        }
    }

    private void intentarGuardar() {
        int numero = entero(campoNumero);
        int capacidad = entero(campoCapacidad);
        String ubicacion = texto(campoUbicacion);

        NuevaMesa nueva = new NuevaMesa(numero, capacidad, ubicacion.isEmpty() ? null : ubicacion);
        Set<ValidadorMesa.ErrorMesa> errores = ValidadorMesa.validar(nueva);
        if (!errores.isEmpty()) {
            mostrarError(getString(primerMensajeDe(errores)));
            return;
        }

        int idLocalAExcluir = esEdicion() ? requireArguments().getInt(ARG_ID_LOCAL) : -1;
        if (ReglasMesa.esNumeroDuplicado(mesasExistentes, numero, idLocalAExcluir)) {
            mostrarError(getString(R.string.mesa_error_numero_duplicado));
            return;
        }

        if (esEdicion()) {
            alGuardar.onEditar(requireArguments().getInt(ARG_ID_LOCAL), nueva);
        } else {
            alGuardar.onCrear(nueva);
        }
        dismiss();
    }

    private int primerMensajeDe(Set<ValidadorMesa.ErrorMesa> errores) {
        if (errores.contains(ValidadorMesa.ErrorMesa.NUMERO_INVALIDO)) {
            return R.string.mesa_error_numero;
        }
        if (errores.contains(ValidadorMesa.ErrorMesa.CAPACIDAD_NO_POSITIVA)) {
            return R.string.mesa_error_capacidad;
        }
        return R.string.mesa_error_ubicacion;
    }

    private void mostrarError(String mensaje) {
        textoError.setText(mensaje);
        textoError.setVisibility(View.VISIBLE);
    }

    private String texto(TextInputEditText campo) {
        return campo.getText() == null ? "" : campo.getText().toString().trim();
    }

    private int entero(TextInputEditText campo) {
        try {
            return Integer.parseInt(texto(campo));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
