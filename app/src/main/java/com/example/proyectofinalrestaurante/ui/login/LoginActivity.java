package com.example.proyectofinalrestaurante.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.proyectofinalrestaurante.MainActivity;
import com.example.proyectofinalrestaurante.R;

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
        setContentView(R.layout.activity_login);

        txtCorreo = findViewById(R.id.txt_correo);
        txtContrasenia = findViewById(R.id.txt_contrasenia);
        btnLogin = findViewById(R.id.btn_login);
        progressBar = findViewById(R.id.progress_login);
        txtError = findViewById(R.id.txt_error_login);

        viewModel = new ViewModelProvider(this, new LoginViewModelFactory()).get(LoginViewModel.class);

        btnLogin.setOnClickListener(v -> viewModel.login(
                txtCorreo.getText().toString(),
                txtContrasenia.getText().toString()));

        viewModel.getEstado().observe(this, this::render);
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
