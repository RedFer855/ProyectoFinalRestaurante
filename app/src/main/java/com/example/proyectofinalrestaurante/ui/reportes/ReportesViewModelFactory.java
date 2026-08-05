package com.example.proyectofinalrestaurante.ui.reportes;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.proyectofinalrestaurante.core.ProveedorDeToken;
import com.example.proyectofinalrestaurante.core.SupabaseClient;
import com.example.proyectofinalrestaurante.core.SyncApplication;
import com.example.proyectofinalrestaurante.data.repository.ReporteRemoto;
import com.example.proyectofinalrestaurante.data.repository.ReporteRepositorioLocal;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Composition root del módulo Reportes (DI manual — ver P-002). Mismo patrón que
 * {@code MesasViewModelFactory}, pero <b>sin</b> {@code Outbox} ni
 * {@code SyncApplication.registrarObservador(...)}: Reportes no entra al {@code SyncWorker}
 * (ADR-013), así que no hay nada que registrar como observador global.
 */
public class ReportesViewModelFactory implements ViewModelProvider.Factory {

    private final Application aplicacion;

    public ReportesViewModelFactory(@NonNull Application aplicacion) {
        this.aplicacion = aplicacion;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        SyncApplication app = (SyncApplication) aplicacion;
        ProveedorDeToken proveedorDeToken =
                new ProveedorDeToken(SupabaseClient.getAuthApi(), app.sesionRepository());
        ReporteRemoto remoto = new ReporteRemoto(SupabaseClient.getReporteApi(), proveedorDeToken);
        ReporteRepositorioLocal repositorio =
                new ReporteRepositorioLocal(app.baseDeDatos(), remoto, System::currentTimeMillis);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        return (T) new ReportesViewModel(repositorio, executor);
    }
}
