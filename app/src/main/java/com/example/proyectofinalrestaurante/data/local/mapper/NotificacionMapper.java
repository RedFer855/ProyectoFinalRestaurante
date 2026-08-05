package com.example.proyectofinalrestaurante.data.local.mapper;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.data.local.entity.NotificacionEntity;
import com.example.proyectofinalrestaurante.domain.model.Notificacion;
import com.example.proyectofinalrestaurante.domain.model.TipoNotificacion;

/**
 * Mapeo {@link NotificacionEntity} ↔ {@link Notificacion} (Plan Fase 3, E3).
 *
 * <p>{@code TipoNotificacion.toString()} es la serialización de {@code tipo} en la base;
 * {@link #aDominio} reintenta {@link TipoNotificacion#valueOf(String)} y cae en
 * {@code ERROR_SYNC} si el tipo no se conoce — nunca deja que una fila del buzón rompa la
 * lista por un tipo de una versión más nueva del APK.</p>
 */
public final class NotificacionMapper {

    private NotificacionMapper() {
    }

    public static Notificacion aDominio(NotificacionEntity entidad) {
        TipoNotificacion tipo = tipoDesdeString(entidad.getTipo());
        return new Notificacion(
                entidad.getIdLocal(),
                tipo,
                entidad.getRolDestino(),
                entidad.getDestinatarioAuth(),
                entidad.getArg1(),
                entidad.getCreadoEn(),
                entidad.isLeida());
    }

    public static NotificacionEntity aEntidad(Notificacion notificacion) {
        return aEntidad(notificacion.getIdLocal(), notificacion.getTipo(),
                notificacion.getRolDestino(), notificacion.getDestinatarioAuth(),
                notificacion.getArg1(), notificacion.getCreadoEn(), notificacion.isLeida());
    }

    /** Fila para insertar (sin un idLocal asignado todavía). */
    public static NotificacionEntity aEntidadNueva(TipoNotificacion tipo,
                                                   @Nullable String rolDestino,
                                                   @Nullable String destinatarioAuth,
                                                   @Nullable String arg1, long creadoEn) {
        return aEntidad(0L, tipo, rolDestino, destinatarioAuth, arg1, creadoEn, false);
    }

    private static NotificacionEntity aEntidad(long idLocal, TipoNotificacion tipo,
                                               @Nullable String rolDestino,
                                               @Nullable String destinatarioAuth,
                                               @Nullable String arg1, long creadoEn,
                                               boolean leida) {
        NotificacionEntity entidad = new NotificacionEntity();
        entidad.setIdLocal(idLocal);
        entidad.setTipo(tipo.name());
        entidad.setRolDestino(rolDestino);
        entidad.setDestinatarioAuth(destinatarioAuth);
        entidad.setArg1(arg1);
        entidad.setCreadoEn(creadoEn);
        entidad.setLeida(leida);
        entidad.setClaveUnica(claveUnica(tipo, arg1));
        return entidad;
    }

    /** La clave de idempotencia del buzón: {@code <TIPO>:<arg1>} (Plan Fase 3, §4.6). */
    public static String claveUnica(TipoNotificacion tipo, @Nullable String arg1) {
        return tipo.name() + ":" + (arg1 == null ? "" : arg1);
    }

    private static TipoNotificacion tipoDesdeString(String tipo) {
        try {
            return TipoNotificacion.valueOf(tipo);
        } catch (IllegalArgumentException ex) {
            return TipoNotificacion.ERROR_SYNC;
        }
    }
}