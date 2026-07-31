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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/** Paso 1 de recuperación de contraseña: pedir el correo y enviar el código OTP. */
public class SolicitarCodigoActivity extends AppCompatActivity {

    private SolicitarCodigoViewModel viewModel;
    private TextInputEditText etCorreo;
    private MaterialButton btnEnviarCodigo;
    private ProgressBar progressBar;
    private TextView txtError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_solicitar_codigo);
        aplicarInsets();

        etCorreo = findViewById(R.id.et_correo);
        btnEnviarCodigo = findViewById(R.id.btn_enviar_codigo);
        progressBar = findViewById(R.id.progress_solicitar_codigo);
        txtError = findViewById(R.id.txt_error_solicitar_codigo);

        viewModel = new ViewModelProvider(this, new SolicitarCodigoViewModelFactory())
                .get(SolicitarCodigoViewModel.class);

        findViewById(R.id.btn_volver_solicitar_codigo).setOnClickListener(v -> finish());

        btnEnviarCodigo.setOnClickListener(v -> viewModel.solicitarCodigo(etCorreo.getText().toString()));

        viewModel.getEstado().observe(this, this::render);
    }

    private void aplicarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.solicitar_codigo_root), (vista, insets) -> {
                    Insets barras = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
                    vista.setPadding(barras.left, barras.top, barras.right, barras.bottom);
                    return insets;
                });
    }

    private void render(EstadoSolicitudCodigo estado) {
        progressBar.setVisibility(estado.isCargando() ? View.VISIBLE : View.GONE);
        btnEnviarCodigo.setEnabled(!estado.isCargando());

        if (estado.getError() != null) {
            txtError.setText(estado.getError());
            txtError.setVisibility(View.VISIBLE);
        } else {
            txtError.setVisibility(View.GONE);
        }

        if (estado.getCorreoConfirmado() != null) {
            Intent intent = new Intent(this, CambiarContraseniaActivity.class);
            intent.putExtra(CambiarContraseniaActivity.EXTRA_CORREO, estado.getCorreoConfirmado());
            startActivity(intent);
            viewModel.onNavegacionConsumida();
        }
    }
}
