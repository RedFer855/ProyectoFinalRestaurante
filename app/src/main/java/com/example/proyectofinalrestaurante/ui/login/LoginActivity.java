package com.example.proyectofinalrestaurante.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.proyectofinalrestaurante.MainActivity;
import com.example.proyectofinalrestaurante.R;
import com.example.proyectofinalrestaurante.ui.recuperacion.SolicitarCodigoActivity;

public class LoginActivity extends AppCompatActivity {

    private LoginViewModel viewModel;
    private EditText txtCorreo;
    private EditText txtContrasenia;
    private Button btnLogin;
    private ProgressBar progressBar;
    private TextView txtError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        aplicarInsets();

        txtCorreo = findViewById(R.id.txt_correo);
        txtContrasenia = findViewById(R.id.txt_contrasenia);
        btnLogin = findViewById(R.id.btn_login);
        progressBar = findViewById(R.id.progress_login);
        txtError = findViewById(R.id.txt_error_login);

        viewModel = new ViewModelProvider(this, new LoginViewModelFactory()).get(LoginViewModel.class);

        btnLogin.setOnClickListener(v -> viewModel.login(
                txtCorreo.getText().toString(),
                txtContrasenia.getText().toString()));

        findViewById(R.id.btn_olvidaste_contrasenia).setOnClickListener(
                v -> startActivity(new Intent(this, SolicitarCodigoActivity.class)));

        viewModel.getEstado().observe(this, this::render);
    }

    /**
     * Con targetSdk 36+ el edge-to-edge es obligatorio: sin esto el título queda bajo la
     * barra de estado y el botón bajo la barra de navegación o el teclado. Se incluye
     * {@code ime()} porque la pantalla tiene campos de texto (deuda P-004).
     */
    private void aplicarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.login_root), (vista, insets) -> {
            Insets barras = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });
    }

    private void render(EstadoLogin estadoLogin) {
        progressBar.setVisibility(estadoLogin.isCargando() ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!estadoLogin.isCargando());

        if (estadoLogin.getError() != null) {
            txtError.setText(estadoLogin.getError());
            txtError.setVisibility(View.VISIBLE);
        } else {
            txtError.setVisibility(View.GONE);
        }

        if (estadoLogin.getSesion() != null) {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        }
    }
}
