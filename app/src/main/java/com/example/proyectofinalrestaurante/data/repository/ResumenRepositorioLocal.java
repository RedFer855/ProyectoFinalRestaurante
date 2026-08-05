package com.example.proyectofinalrestaurante.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.example.proyectofinalrestaurante.data.local.AppDatabase;
import com.example.proyectofinalrestaurante.data.local.dao.ClienteDao;
import com.example.proyectofinalrestaurante.data.local.dao.EmpleadoDao;
import com.example.proyectofinalrestaurante.data.local.dao.MesaDao;
import com.example.proyectofinalrestaurante.data.local.dao.PedidoDao;
import com.example.proyectofinalrestaurante.data.local.dao.PlatilloDao;
import com.example.proyectofinalrestaurante.data.local.dao.ReporteDao;
import com.example.proyectofinalrestaurante.data.local.entity.ReporteVentasEntity;
import com.example.proyectofinalrestaurante.domain.model.EstadoMesa;
import com.example.proyectofinalrestaurante.domain.model.EstadoPedido;
import com.example.proyectofinalrestaurante.domain.model.RangoReporte;
import com.example.proyectofinalrestaurante.domain.model.ResumenInicio;
import com.example.proyectofinalrestaurante.domain.repository.ResumenRepository;

/**
 * Implementación de {@link ResumenRepository} (Plan Fase 3c, §5 y §7.2).
 *
 * <p><b>Cero llamadas de red.</b> Los seis contadores son consultas locales de Room que nunca
 * fallan; la séptima tarjeta ("Ventas de hoy") lee la fila {@code HOY} que ya mantiene
 * {@link ReporteRepositorioLocal} — nunca dispara su propio refresco. Es la razón por la que
 * el dashboard es 100% local para mesero y cocina (§5 del plan): ni siquiera intenta una
 * llamada que el servidor rechazaría con 403.</p>
 */
public final class ResumenRepositorioLocal implements ResumenRepository {

    private final PedidoDao pedidoDao;
    private final MesaDao mesaDao;
    private final ClienteDao clienteDao;
    private final PlatilloDao platilloDao;
    private final EmpleadoDao empleadoDao;
    private final ReporteDao reporteDao;

    public ResumenRepositorioLocal(AppDatabase base) {
        this.pedidoDao = base.pedidoDao();
        this.mesaDao = base.mesaDao();
        this.clienteDao = base.clienteDao();
        this.platilloDao = base.platilloDao();
        this.empleadoDao = base.empleadoDao();
        this.reporteDao = base.reporteDao();
    }

    @Override
    public LiveData<ResumenInicio> observarResumen() {
        MediatorLiveData<ResumenInicio> combinado = new MediatorLiveData<>();
        Datos datos = new Datos();

        combinado.addSource(pedidoDao.observarConteoPorEstado(EstadoPedido.PENDIENTE.getId()), v -> {
            datos.pedidosPendientes = valorO0(v);
            combinado.setValue(datos.construir());
        });
        combinado.addSource(pedidoDao.observarConteoPorEstado(EstadoPedido.EN_PREPARACION.getId()), v -> {
            datos.pedidosEnPreparacion = valorO0(v);
            combinado.setValue(datos.construir());
        });
        combinado.addSource(mesaDao.observarConteoPorEstadoOperativo(EstadoMesa.OCUPADA.getId()), v -> {
            datos.mesasOcupadas = valorO0(v);
            combinado.setValue(datos.construir());
        });
        combinado.addSource(mesaDao.observarConteoActivas(), v -> {
            datos.mesasTotales = valorO0(v);
            combinado.setValue(datos.construir());
        });
        combinado.addSource(clienteDao.observarConteoActivos(), v -> {
            datos.clientesRegistrados = valorO0(v);
            combinado.setValue(datos.construir());
        });
        combinado.addSource(platilloDao.observarConteoActivos(), v -> {
            datos.platillosActivos = valorO0(v);
            combinado.setValue(datos.construir());
        });
        combinado.addSource(empleadoDao.observarConteoActivos(), v -> {
            datos.empleadosActivos = valorO0(v);
            combinado.setValue(datos.construir());
        });
        combinado.addSource(reporteDao.observarCabecera(RangoReporte.HOY.name()), cabecera -> {
            datos.ventasHoyCabecera = cabecera;
            combinado.setValue(datos.construir());
        });
        return combinado;
    }

    private static int valorO0(Integer valor) {
        return valor == null ? 0 : valor;
    }

    private static final class Datos {
        int pedidosPendientes;
        int pedidosEnPreparacion;
        int mesasOcupadas;
        int mesasTotales;
        int clientesRegistrados;
        int platillosActivos;
        int empleadosActivos;
        ReporteVentasEntity ventasHoyCabecera;

        ResumenInicio construir() {
            return new ResumenInicio(pedidosPendientes, pedidosEnPreparacion, mesasOcupadas,
                    mesasTotales, clientesRegistrados, platillosActivos, empleadosActivos,
                    ventasHoyCabecera == null ? null : ventasHoyCabecera.getTotalVentas(),
                    ventasHoyCabecera == null ? null : ventasHoyCabecera.getGeneradoEn());
        }
    }
}
