package com.example.proyectofinalrestaurante.ui.maqueta;

import java.util.Arrays;
import java.util.List;

/**
 * Datos falsos de la maqueta (Plan Fase 1c, Entregable 3).
 *
 * <p><b>Nada de esto es real.</b> El archivo se vacía a medida que los módulos pasan a leer
 * de Supabase. Vive en {@code ui/} y nunca en {@code domain/}: el dominio no debe enterarse
 * de que existió.</p>
 */
public final class DatosMaqueta {

    private DatosMaqueta() {
    }

    // ---------------------------------------------------------------- modelos

    // Platillo y Categoria vivían acá hasta la Fase 2a. Ahora el Menú lee de Supabase y
    // sus modelos reales están en domain/model — igual que pasó con Empleado en la 1d.
    // Pedido/LineaPedido se fueron en la Fase 3 (E10): el tablero lee Pedido de Room.

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
