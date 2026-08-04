package com.example.proyectofinalrestaurante.domain.model;

/**
 * Estado operativo de una mesa en el salón (Fase 2c).
 *
 * <p>Catálogo fijo en el servidor: Libre, Ocupada, Reservada. El id corresponde a
 * {@code estado_mesa.id_estado_mesa} en la base.</p>
 *
 * <p>Este es un concepto <b>ortogonal</b> a {@code estado_general} (Activo/Inactivo):
 * una mesa puede estar "Ocupada" y "Activa" al mismo tiempo, o "Libre" y "Inactiva"
 * (dada de baja). Ver Plan Fase 2c, §2.1.</p>
 *
 * <p>Los colores y etiquetas de UI se mapean en {@code ui/mesas/} — el dominio no
 * referencia {@code R}.</p>
 */
public enum EstadoMesa {

    LIBRE(1),
    OCUPADA(2),
    RESERVADA(3);

    private final int idServidor;

    EstadoMesa(int idServidor) {
        this.idServidor = idServidor;
    }

    public int getIdServidor() {
        return idServidor;
    }

    /**
     * Busca el estado por su id del servidor. Devuelve {@code null} si el id no coincide
     * con ninguno — algo que no debería pasar si la base está bien.
     */
    public static EstadoMesa porId(int id) {
        for (EstadoMesa estado : values()) {
            if (estado.idServidor == id) {
                return estado;
            }
        }
        return null;
    }

    /** Siguiente estado en el flujo de ciclo: Libre → Ocupada → Reservada → Libre. */
    public EstadoMesa siguiente() {
        switch (this) {
            case LIBRE:
                return OCUPADA;
            case OCUPADA:
                return RESERVADA;
            case RESERVADA:
                return LIBRE;
            default:
                return this;
        }
    }
}
