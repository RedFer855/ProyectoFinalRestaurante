package com.example.proyectofinalrestaurante.ui.principal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.example.proyectofinalrestaurante.domain.Accion;
import com.example.proyectofinalrestaurante.domain.Modulo;
import com.example.proyectofinalrestaurante.domain.Permisos;
import com.example.proyectofinalrestaurante.domain.model.ResumenInicio;
import com.example.proyectofinalrestaurante.domain.repository.ResumenRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel de Inicio (Plan Fase 3c, E9). Antes de esta fase, {@code InicioFragment} no tenía
 * ViewModel y armaba la grilla filtrando por permiso directo en {@code onViewCreated}; ese
 * filtrado se mueve acá (§7.3 del plan) usando {@link Permisos#puede} — domain puro, sin
 * {@code View} ni {@code Context} — y no {@code VistaPorPermiso}, reservado para cuando hay
 * una {@code View} real que ocultar.
 *
 * <p>El mapeo módulo/acción por tarjeta es el mismo que ya tenía {@code InicioFragment}: no es
 * arbitrario, es a quién le sirve cada dato (ver el Javadoc original).</p>
 */
public class InicioViewModel extends ViewModel {

    private final ResumenRepository repositorio;
    @Nullable private final String rol;
    private final MediatorLiveData<EstadoInicio> estado = new MediatorLiveData<>();

    public InicioViewModel(@NonNull ResumenRepository repositorio, @Nullable String rol) {
        this.repositorio = repositorio;
        this.rol = rol;
        estado.addSource(repositorio.observarResumen(), this::cuandoLlegaResumen);
        estado.setValue(EstadoInicio.cargando());
    }

    public LiveData<EstadoInicio> getEstado() {
        return estado;
    }

    private void cuandoLlegaResumen(ResumenInicio resumen) {
        estado.setValue(EstadoInicio.conDatos(tarjetasVisibles(resumen)));
    }

    private List<TarjetaInicio> tarjetasVisibles(ResumenInicio resumen) {
        List<TarjetaInicio> visibles = new ArrayList<>();

        if (Permisos.puede(rol, Modulo.PEDIDOS, Accion.CREAR)) {
            visibles.add(TarjetaInicio.pedidosPendientes(resumen.getPedidosPendientes()));
        }
        if (Permisos.puede(rol, Modulo.PEDIDOS, Accion.CAMBIAR_ESTADO)) {
            visibles.add(TarjetaInicio.pedidosEnPreparacion(resumen.getPedidosEnPreparacion()));
        }
        if (Permisos.puede(rol, Modulo.MESAS, Accion.VER)) {
            visibles.add(TarjetaInicio.mesasOcupadas(resumen.getMesasOcupadas(), resumen.getMesasTotales()));
        }
        if (Permisos.puede(rol, Modulo.CLIENTES, Accion.VER)) {
            visibles.add(TarjetaInicio.clientesRegistrados(resumen.getClientesRegistrados()));
        }
        if (Permisos.puede(rol, Modulo.MENU, Accion.CREAR)) {
            visibles.add(TarjetaInicio.platillosActivos(resumen.getPlatillosActivos()));
        }
        if (Permisos.puede(rol, Modulo.REPORTES, Accion.VER)) {
            visibles.add(TarjetaInicio.ventasHoy(resumen.getVentasHoy(), resumen.getVentasHoyGeneradoEn()));
        }
        if (Permisos.puede(rol, Modulo.EMPLEADOS, Accion.VER)) {
            visibles.add(TarjetaInicio.empleadosActivos(resumen.getEmpleadosActivos()));
        }
        return visibles;
    }
}
