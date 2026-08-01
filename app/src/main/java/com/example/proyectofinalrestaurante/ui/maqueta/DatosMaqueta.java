package com.example.proyectofinalrestaurante.ui.maqueta;

import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;

import com.example.proyectofinalrestaurante.R;

import java.util.Arrays;
import java.util.List;

/**
 * Datos falsos de la maqueta (Plan Fase 1c, Entregable 3).
 *
 * <p><b>Nada de esto es real.</b> Todo el archivo se borra en la Fase 2, cuando los
 * módulos empiecen a leer de Supabase. Vive en {@code ui/} y nunca en {@code domain/}:
 * el dominio no debe enterarse de que existió.</p>
 *
 * <p>Los estados de pedido y mesa están acá y no en {@code domain} a propósito: el
 * catálogo real todavía no se decidió en la base ({@code estado_general} solo tiene
 * Activo/Inactivo — ver Esquema de Base de Datos). Definirlos ahora sería congelar una
 * decisión que no está tomada.</p>
 */
public final class DatosMaqueta {

    private DatosMaqueta() {
    }

    // ---------------------------------------------------------------- estados

    public enum EstadoPedido {
        PENDIENTE(R.string.estado_pedido_pendiente, R.color.estado_pendiente),
        EN_PREPARACION(R.string.estado_pedido_preparacion, R.color.estado_preparacion),
        LISTO(R.string.estado_pedido_listo, R.color.estado_listo),
        ENTREGADO(R.string.estado_pedido_entregado, R.color.estado_entregado),
        CANCELADO(R.string.estado_pedido_cancelado, R.color.estado_cancelado);

        @StringRes public final int etiqueta;
        @ColorRes public final int color;

        EstadoPedido(@StringRes int etiqueta, @ColorRes int color) {
            this.etiqueta = etiqueta;
            this.color = color;
        }

        /** Siguiente estado en el flujo de cocina. El último no avanza. */
        public EstadoPedido siguiente() {
            switch (this) {
                case PENDIENTE:
                    return EN_PREPARACION;
                case EN_PREPARACION:
                    return LISTO;
                case LISTO:
                    return ENTREGADO;
                default:
                    return this;
            }
        }
    }

    public enum EstadoMesa {
        LIBRE(R.string.estado_mesa_libre, R.color.estado_listo),
        OCUPADA(R.string.estado_mesa_ocupada, R.color.estado_cancelado),
        RESERVADA(R.string.estado_mesa_reservada, R.color.estado_pendiente);

        @StringRes public final int etiqueta;
        @ColorRes public final int color;

        EstadoMesa(@StringRes int etiqueta, @ColorRes int color) {
            this.etiqueta = etiqueta;
            this.color = color;
        }

        public EstadoMesa siguiente() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    // ---------------------------------------------------------------- modelos

    // Platillo y Categoria vivían acá hasta la Fase 2a. Ahora el Menú lee de Supabase y
    // sus modelos reales están en domain/model — igual que pasó con Empleado en la 1d.

    public static final class Pedido {
        public final int numero;
        /** "Mesa 4" o "Para llevar" — lo que identifica al pedido de un vistazo. */
        public final String referencia;
        public final String cliente;
        public final String hora;
        public final double total;
        public final EstadoPedido estado;

        public Pedido(int numero, String referencia, String cliente, String hora,
                      double total, EstadoPedido estado) {
            this.numero = numero;
            this.referencia = referencia;
            this.cliente = cliente;
            this.hora = hora;
            this.total = total;
            this.estado = estado;
        }

        /** Copia con otro estado — inmutable, para que DiffUtil detecte el cambio. */
        public Pedido conEstado(EstadoPedido nuevo) {
            return new Pedido(numero, referencia, cliente, hora, total, nuevo);
        }
    }

    public static final class LineaPedido {
        public final String nombre;
        public final int cantidad;
        public final double precio;
        public final boolean esComplemento;

        public LineaPedido(String nombre, int cantidad, double precio, boolean esComplemento) {
            this.nombre = nombre;
            this.cantidad = cantidad;
            this.precio = precio;
            this.esComplemento = esComplemento;
        }
    }

    public static final class Mesa {
        public final int numero;
        public final int capacidad;
        public final EstadoMesa estado;

        public Mesa(int numero, int capacidad, EstadoMesa estado) {
            this.numero = numero;
            this.capacidad = capacidad;
            this.estado = estado;
        }

        /** Copia con otro estado — inmutable, para que DiffUtil detecte el cambio. */
        public Mesa conEstado(EstadoMesa nuevo) {
            return new Mesa(numero, capacidad, nuevo);
        }
    }

    public static final class Cliente {
        public final String nombres;
        public final String apellidos;
        public final String identidad;
        public final String telefono;

        public Cliente(String nombres, String apellidos, String identidad, String telefono) {
            this.nombres = nombres;
            this.apellidos = apellidos;
            this.identidad = identidad;
            this.telefono = telefono;
        }

        public String nombreCompleto() {
            return nombres + " " + apellidos;
        }
    }

    public static final class ConteoSimple {
        public final String etiqueta;
        public final int cantidad;

        public ConteoSimple(String etiqueta, int cantidad) {
            this.etiqueta = etiqueta;
            this.cantidad = cantidad;
        }
    }

    public static final class DesempenoMesero {
        public final String nombre;
        public final int pedidos;
        public final double total;

        public DesempenoMesero(String nombre, int pedidos, double total) {
            this.nombre = nombre;
            this.pedidos = pedidos;
            this.total = total;
        }
    }

    // ---------------------------------------------------------------- datos

    public static List<Pedido> pedidos() {
        return Arrays.asList(
                new Pedido(1042, "Mesa 4", "Ana Cruz", "13:05", 380.00, EstadoPedido.PENDIENTE),
                new Pedido(1041, "Mesa 7", "Luis Medina", "12:58", 245.00, EstadoPedido.EN_PREPARACION),
                new Pedido(1040, "Para llevar", "Sofía Ramos", "12:47", 130.00, EstadoPedido.EN_PREPARACION),
                new Pedido(1039, "Mesa 2", "Carlos Núñez", "12:30", 520.00, EstadoPedido.LISTO),
                new Pedido(1038, "Mesa 9", "Mesa sin cliente", "12:12", 175.00, EstadoPedido.ENTREGADO),
                new Pedido(1037, "Mesa 1", "Gabriela Paz", "11:55", 90.00, EstadoPedido.CANCELADO));
    }

    /** Detalle del pedido 1042, para la vista de detalle. */
    public static List<LineaPedido> detallePedido() {
        return Arrays.asList(
                new LineaPedido("Pollo con tajadas", 2, 145.00, false),
                new LineaPedido("Sopa de caracol", 1, 190.00, false),
                new LineaPedido("Refresco de tamarindo", 1, 45.00, true),
                new LineaPedido("Tortillas extra", 2, 10.00, true));
    }

    public static List<Mesa> mesas() {
        return Arrays.asList(
                new Mesa(1, 2, EstadoMesa.LIBRE),
                new Mesa(2, 4, EstadoMesa.OCUPADA),
                new Mesa(3, 4, EstadoMesa.LIBRE),
                new Mesa(4, 6, EstadoMesa.OCUPADA),
                new Mesa(5, 2, EstadoMesa.RESERVADA),
                new Mesa(6, 8, EstadoMesa.LIBRE),
                new Mesa(7, 4, EstadoMesa.OCUPADA),
                new Mesa(8, 2, EstadoMesa.LIBRE),
                new Mesa(9, 6, EstadoMesa.OCUPADA),
                new Mesa(10, 4, EstadoMesa.LIBRE));
    }

    public static List<Cliente> clientes() {
        return Arrays.asList(
                new Cliente("Ana", "Cruz", "0801199512345", "9988-1122"),
                new Cliente("Luis", "Medina", "0501200203344", "3344-5566"),
                new Cliente("Sofía", "Ramos", "0801198877665", "9911-2233"),
                new Cliente("Carlos", "Núñez", "0703199044556", "8877-6655"),
                new Cliente("Gabriela", "Paz", null, "9900-1234"));
    }

    public static List<ConteoSimple> platillosMasPedidos() {
        return Arrays.asList(
                new ConteoSimple("Pollo con tajadas", 42),
                new ConteoSimple("Baleada sencilla", 38),
                new ConteoSimple("Sopa de caracol", 25),
                new ConteoSimple("Horchata", 21),
                new ConteoSimple("Tres leches", 14));
    }

    public static List<DesempenoMesero> desempenoMeseros() {
        return Arrays.asList(
                new DesempenoMesero("Marta Zelaya", 18, 2450.00),
                new DesempenoMesero("Óscar Fúnez", 14, 1800.00));
    }

    // ------------------------------------------------- contadores del inicio

    public static final int PEDIDOS_PENDIENTES = 4;
    public static final int PEDIDOS_EN_PREPARACION = 3;
    public static final int MESAS_OCUPADAS = 6;
    public static final int MESAS_TOTALES = 10;
    public static final int CLIENTES_REGISTRADOS = 128;
    public static final int PLATILLOS_ACTIVOS = 32;
    public static final int EMPLEADOS_ACTIVOS = 8;
    public static final double VENTAS_DEL_DIA = 4250.00;
    public static final double TICKET_PROMEDIO = 212.50;
    public static final int PEDIDOS_DEL_DIA = 20;
}
