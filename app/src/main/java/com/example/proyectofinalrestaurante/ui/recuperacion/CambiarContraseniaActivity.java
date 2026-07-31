package com.example.proyectofinalrestaurante.ui.recuperacion;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.domain.RequisitoContrasenia;
import com.example.proyectofinalrestaurante.ui.login.LoginActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

/** Paso 2 de recuperación: verificar el código OTP y fijar la contraseña nueva. */
public class CambiarContraseniaActivity extends AppCompatActivity {

    public static final String EXTRA_CORREO = "extra_correo";

    private CambiarContraseniaViewModel viewModel;
    private TextInputEditText etCodigo;
    private TextInputEditText etNuevaContrasenia;
    private TextInputEditText etConfirmacion;
    private MaterialButton btnCambiar;
    private MaterialButton btnReenviar;
    private ProgressBar progressBar;
    private TextView txtError;
    private TextView txtRequisitoLongitud;
    private TextView txtRequisitoMayuscula;
    private TextView txtRequisitoMinuscula;
    private TextView txtRequisitoDigito;
    private TextView txtRequisitoSimbolo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_cambiar_contrasenia);
        aplicarInsets();

        etCodigo = findViewById(R.id.et_codigo);
        etNuevaContrasenia = findViewById(R.id.et_nueva_contrasenia);
        etConfirmacion = findViewById(R.id.et_confirmacion);
        btnCambiar = findViewById(R.id.btn_cambiar_contrasenia);
        btnReenviar = findViewById(R.id.btn_reenviar_codigo);
        progressBar = findViewById(R.id.progress_cambiar_contrasenia);
        txtError = findViewById(R.id.txt_error_cambiar_contrasenia);
        txtRequisitoLongitud = findViewById(R.id.txt_req_longitud);
        txtRequisitoMayuscula = findViewById(R.id.txt_req_mayuscula);
        txtRequisitoMinuscula = findViewById(R.id.txt_req_minuscula);
        txtRequisitoDigito = findViewById(R.id.txt_req_digito);
        txtRequisitoSimbolo = findViewById(R.id.txt_req_simbolo);

        viewModel = new ViewModelProvider(this, new CambiarContraseniaViewModelFactory())
                .get(CambiarContraseniaViewModel.class);
        viewModel.setCorreo(getIntent().getStringExtra(EXTRA_CORREO));

        findViewById(R.id.btn_volver_cambiar_contrasenia).setOnClickListener(v -> finish());

        etCodigo.addTextChangedListener(textWatcher(
                s -> viewModel.onCodigoCambiado(s)));
        etNuevaContrasenia.addTextChangedListener(textWatcher(
                s -> viewModel.onNuevaContraseniaCambiada(s)));
        etConfirmacion.addTextChangedListener(textWatcher(
                s -> viewModel.onConfirmacionCambiada(s)));

        btnReenviar.setOnClickListener(v -> viewModel.reenviarCodigo());
        btnCambiar.setOnClickListener(v -> viewModel.cambiarContrasenia());

        viewModel.getEstado().observe(this, this::render);
    }

    private void aplicarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.cambiar_contrasenia_root), (vista, insets) -> {
                    Insets barras = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
                    vista.setPadding(barras.left, barras.top, barras.right, barras.bottom);
                    return insets;
                });
    }

    private void render(EstadoCambioContrasenia estado) {
        progressBar.setVisibility(estado.isCargando() ? View.VISIBLE : View.GONE);
        btnCambiar.setEnabled(estado.isPuedeCambiar());

        String textoReenviar = estado.isPuedeReenviar()
                ? getString(R.string.recuperacion_reenviar_codigo)
                : getString(R.string.recuperacion_reenviar_cuenta_regresiva, estado.getSegundosRestantes());
        btnReenviar.setText(textoReenviar);
        btnReenviar.setEnabled(estado.isPuedeReenviar());

        if (estado.getError() != null) {
            txtError.setText(estado.getError());
            txtError.setVisibility(View.VISIBLE);
        } else {
            txtError.setVisibility(View.GONE);
        }

        pintarRequisito(txtRequisitoLongitud, estado, RequisitoContrasenia.LONGITUD_MINIMA, R.string.recuperacion_req_longitud);
        pintarRequisito(txtRequisitoMayuscula, estado, RequisitoContrasenia.MAYUSCULA, R.string.recuperacion_req_mayuscula);
        pintarRequisito(txtRequisitoMinuscula, estado, RequisitoContrasenia.MINUSCULA, R.string.recuperacion_req_minuscula);
        pintarRequisito(txtRequisitoDigito, estado, RequisitoContrasenia.DIGITO, R.string.recuperacion_req_digito);
        pintarRequisito(txtRequisitoSimbolo, estado, RequisitoContrasenia.SIMBOLO, R.string.recuperacion_req_simbolo);

        if (estado.isCambioExitoso()) {
            Snackbar.make(findViewById(R.id.cambiar_contrasenia_root),
                            R.string.recuperacion_exito, Snackbar.LENGTH_LONG)
                    .show();
            viewModel.onNavegacionConsumida();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }

    private void pintarRequisito(
            TextView vista, EstadoCambioContrasenia estado,
            RequisitoContrasenia requisito, int textoResId) {
        boolean cumple = estado.getIncumplidos().isEmpty() || !estado.getIncumplidos().contains(requisito);
        int color = getColor(cumple ? R.color.brand_primary : R.color.brand_on_surface_variant);
        vista.setText(textoResId);
        vista.setTextColor(color);

        android.graphics.drawable.Drawable icono = androidx.core.content.ContextCompat.getDrawable(
                this, cumple ? R.drawable.ic_check : R.drawable.ic_circle);
        if (icono != null) {
            icono = androidx.core.graphics.drawable.DrawableCompat.wrap(icono).mutate();
            androidx.core.graphics.drawable.DrawableCompat.setTint(icono, color);
        }
        vista.setCompoundDrawablesRelativeWithIntrinsicBounds(icono, null, null, null);
    }

    private android.text.TextWatcher textWatcher(java.util.function.Consumer<String> consumidor) {
        return new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                consumidor.accept(s == null ? "" : s.toString());
            }
        };
    }
}
