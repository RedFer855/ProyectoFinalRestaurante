package com.example.proyectofinalrestaurante.data.local.mapper;

import androidx.annotation.Nullable;

import com.example.proyectofinalrestaurante.data.local.entity.ConteoPlatilloEntity;
import com.example.proyectofinalrestaurante.data.local.entity.DesempenoMeseroEntity;
import com.example.proyectofinalrestaurante.data.local.entity.ReporteVentasEntity;
import com.example.proyectofinalrestaurante.data.remote.dto.ConteoPlatilloDto;
import com.example.proyectofinalrestaurante.data.remote.dto.DesempenoMeseroDto;
import com.example.proyectofinalrestaurante.data.remote.dto.ReporteVentasDto;
import com.example.proyectofinalrestaurante.domain.model.ConteoPlatillo;
import com.example.proyectofinalrestaurante.domain.model.DesempenoMesero;
import com.example.proyectofinalrestaurante.domain.model.RangoReporte;
import com.example.proyectofinalrestaurante.domain.model.ReporteVentas;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mapeo entre {@link ReporteVentasDto} (servidor), las tres entidades de instantánea de Room y
 * {@link ReporteVentas} (dominio) — Plan Fase 3c, §7.2.
 *
 * <p>{@code generado_en} viaja del servidor como ISO-8601 y se guarda en Room como epoch
 * millis: es lo único que {@link com.example.proyectofinalrestaurante.domain.ReglasReporte}
 * necesita comparar contra el reloj, y evita reparsear la fecha en cada lectura.</p>
 */
public final class ReporteMapper {

    private ReporteMapper() {
    }

    // ------------------------------------------------------------------ servidor -> entidad

    public static ReporteVentasEntity cabeceraDesdeDto(String rango, ReporteVentasDto dto) {
        ReporteVentasEntity entidad = new ReporteVentasEntity();
        entidad.setRango(rango);
        entidad.setGeneradoEn(Instant.parse(dto.getGeneradoEn()).toEpochMilli());
        entidad.setTotalVentas(dto.getTotalVentas());
        entidad.setCantidadPedidos(dto.getCantidadPedidos());
        entidad.setTicketPromedio(dto.getTicketPromedio());
        return entidad;
    }

    public static List<ConteoPlatilloEntity> topPlatillosDesdeDto(String rango, ReporteVentasDto dto) {
        List<ConteoPlatilloDto> origen = dto.getTopPlatillos();
        if (origen == null) {
            return Collections.emptyList();
        }
        List<ConteoPlatilloEntity> lista = new ArrayList<>(origen.size());
        for (int i = 0; i < origen.size(); i++) {
            ConteoPlatilloDto item = origen.get(i);
            ConteoPlatilloEntity entidad = new ConteoPlatilloEntity();
            entidad.setRango(rango);
            entidad.setNombre(item.getNombre());
            entidad.setCantidad(item.getCantidad());
            entidad.setOrden(i);
            lista.add(entidad);
        }
        return lista;
    }

    public static List<DesempenoMeseroEntity> desempenoDesdeDto(String rango, ReporteVentasDto dto) {
        List<DesempenoMeseroDto> origen = dto.getDesempenoMeseros();
        if (origen == null) {
            return Collections.emptyList();
        }
        List<DesempenoMeseroEntity> lista = new ArrayList<>(origen.size());
        for (int i = 0; i < origen.size(); i++) {
            DesempenoMeseroDto item = origen.get(i);
            DesempenoMeseroEntity entidad = new DesempenoMeseroEntity();
            entidad.setRango(rango);
            entidad.setNombre(item.getNombre());
            entidad.setCantidadPedidos(item.getCantidadPedidos());
            entidad.setTotalVendido(item.getTotalVendido());
            entidad.setOrden(i);
            lista.add(entidad);
        }
        return lista;
    }

    // ------------------------------------------------------------------ entidad -> dominio

    /**
     * Combina las tres tablas en un {@link ReporteVentas}. {@code null} si {@code cabecera} es
     * {@code null} — todavía no hay instantánea para ese rango (Plan Fase 3c, §6: estado vacío
     * honesto, nunca datos inventados).
     */
    @Nullable
    public static ReporteVentas aDominio(@Nullable ReporteVentasEntity cabecera,
                                         List<ConteoPlatilloEntity> topPlatillos,
                                         List<DesempenoMeseroEntity> desempenoMeseros) {
        if (cabecera == null) {
            return null;
        }
        return new ReporteVentas(
                RangoReporte.valueOf(cabecera.getRango()),
                cabecera.getGeneradoEn(),
                cabecera.getTotalVentas(),
                cabecera.getCantidadPedidos(),
                cabecera.getTicketPromedio(),
                aDominioPlatillos(topPlatillos),
                aDominioMeseros(desempenoMeseros));
    }

    private static List<ConteoPlatillo> aDominioPlatillos(List<ConteoPlatilloEntity> entidades) {
        if (entidades == null) {
            return Collections.emptyList();
        }
        List<ConteoPlatillo> dominio = new ArrayList<>(entidades.size());
        for (ConteoPlatilloEntity entidad : entidades) {
            dominio.add(new ConteoPlatillo(entidad.getNombre(), entidad.getCantidad()));
        }
        return dominio;
    }

    private static List<DesempenoMesero> aDominioMeseros(List<DesempenoMeseroEntity> entidades) {
        if (entidades == null) {
            return Collections.emptyList();
        }
        List<DesempenoMesero> dominio = new ArrayList<>(entidades.size());
        for (DesempenoMeseroEntity entidad : entidades) {
            dominio.add(new DesempenoMesero(
                    entidad.getNombre(), entidad.getCantidadPedidos(), entidad.getTotalVendido()));
        }
        return dominio;
    }
}
