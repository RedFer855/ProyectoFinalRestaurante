package com.example.proyectofinalrestaurante.data.local.mapper;

import static org.junit.Assert.assertEquals;

import com.example.proyectofinalrestaurante.data.local.entity.NotificacionEntity;
import com.example.proyectofinalrestaurante.domain.model.Notificacion;
import com.example.proyectofinalrestaurante.domain.model.TipoNotificacion;

import org.junit.Test;

/**
 * El mapeo notificación local ↔ dominio (Plan Fase 3, E3): serialización de {@code tipo},
 * clave de idempotencia y tolerancia a tipos desconocidos.
 */
public class NotificacionMapperTest {

    @Test
    public void aEntidadNueva_armaLaClaveUnicaYPoneLeidaEnFalso() {
        NotificacionEntity entidad = NotificacionMapper.aEntidadNueva(
                TipoNotificacion.PEDIDO_NUEVO, "cocina", null, "41", 1000L);

        assertEquals("PEDIDO_NUEVO", entidad.getTipo());
        assertEquals("cocina", entidad.getRolDestino());
        assertEquals("41", entidad.getArg1());
        assertEquals(1000L, entidad.getCreadoEn());
        assertEquals(false, entidad.isLeida());
        assertEquals("PEDIDO_NUEVO:41", entidad.getClaveUnica());
    }

    @Test
    public void claveUnica_concordenaTipoYArgumento() {
        assertEquals("PEDIDO_LISTO:1042", NotificacionMapper.claveUnica(
                TipoNotificacion.PEDIDO_LISTO, "1042"));
        assertEquals("PEDIDO_NUEVO:", NotificacionMapper.claveUnica(
                TipoNotificacion.PEDIDO_NUEVO, null));
    }

    @Test
    public void aDominio_redondeaTipoDesconocidoAErrorSync() {
        NotificacionEntity entidad = NotificacionMapper.aEntidadNueva(
                TipoNotificacion.PEDIDO_NUEVO, "cocina", null, "41", 1000L);
        entidad.setTipo("TIPO_INEXISTENTE");

        Notificacion notificacion = NotificacionMapper.aDominio(entidad);

        assertEquals(TipoNotificacion.ERROR_SYNC, notificacion.getTipo());
    }

    @Test
    public void aDominio_mapeaElTipoConocido() {
        NotificacionEntity entidad = NotificacionMapper.aEntidadNueva(
                TipoNotificacion.PEDIDO_LISTO, null, "uuid-mesero", "1042", 2000L);

        Notificacion notificacion = NotificacionMapper.aDominio(entidad);

        assertEquals(TipoNotificacion.PEDIDO_LISTO, notificacion.getTipo());
        assertEquals("uuid-mesero", notificacion.getDestinatarioAuth());
        assertEquals("1042", notificacion.getArg1());
        assertEquals(2000L, notificacion.getCreadoEn());
        assertEquals(false, notificacion.isLeida());
    }

    @Test
    public void aEntidad_devuelveQuizasLaMismaFilaConLeida() {
        NotificacionEntity entidad = NotificacionMapper.aEntidadNueva(
                TipoNotificacion.PEDIDO_NUEVO, "cocina", null, "41", 1000L);
        entidad.setIdLocal(7);
        entidad.setLeida(true);

        NotificacionEntity copia = NotificacionMapper.aEntidad(
                NotificacionMapper.aDominio(entidad));
        assertEquals(7L, copia.getIdLocal());
        assertEquals(true, copia.isLeida());
        assertEquals("PEDIDO_NUEVO:41", copia.getClaveUnica());
    }
}