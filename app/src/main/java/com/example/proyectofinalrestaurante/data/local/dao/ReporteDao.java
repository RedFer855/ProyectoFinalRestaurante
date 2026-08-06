package com.example.proyectofinalrestaurante.data.local.dao;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.example.proyectofinalrestaurante.data.local.entity.ConteoPlatilloEntity;
import com.example.proyectofinalrestaurante.data.local.entity.DesempenoMeseroEntity;
import com.example.proyectofinalrestaurante.data.local.entity.ReporteVentasEntity;

import java.util.List;

/**
 * DAO del módulo Reportes (Plan Fase 3c, E3/E6). Tres tablas por instantánea (cabecera +
 * top-platillos + desempeño-meseros), una fila de cabecera por {@code rango}.
 *
 * <p>{@link #reemplazarRango} es la única vía de escritura: borra las listas del rango y
 * pone las nuevas en una transacción, para no dejar filas huérfanas si el nuevo reporte trae
 * menos meseros que el anterior (caso de aceptación B4). Las tres instantáneas (HOY/SEMANA/MES)
 * son independientes entre sí — reemplazar una nunca toca las otras dos (§6 del plan).</p>
 */
@Dao
public interface ReporteDao {

    @Query("SELECT * FROM reportes_ventas WHERE rango = :rango LIMIT 1")
    LiveData<ReporteVentasEntity> observarCabecera(String rango);

    @Nullable
    @Query("SELECT * FROM reportes_ventas WHERE rango = :rango LIMIT 1")
    ReporteVentasEntity cabeceraSincrona(String rango);

    @Query("SELECT * FROM conteo_platillo WHERE rango = :rango ORDER BY orden ASC")
    LiveData<List<ConteoPlatilloEntity>> observarTopPlatillos(String rango);

    @Query("SELECT * FROM desempeno_mesero WHERE rango = :rango ORDER BY orden ASC")
    LiveData<List<DesempenoMeseroEntity>> observarDesempeno(String rango);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertarCabecera(ReporteVentasEntity cabecera);

    @Query("DELETE FROM conteo_platillo WHERE rango = :rango")
    void vaciarTopPlatillos(String rango);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertarTopPlatillos(List<ConteoPlatilloEntity> lista);

    @Query("DELETE FROM desempeno_mesero WHERE rango = :rango")
    void vaciarDesempeno(String rango);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertarDesempeno(List<DesempenoMeseroEntity> lista);

    /** Reemplaza la instantánea completa de un rango en una transacción (Plan Fase 3c, §7.2). */
    @Transaction
    default void reemplazarRango(String rango, ReporteVentasEntity cabecera,
                                 List<ConteoPlatilloEntity> topPlatillos,
                                 List<DesempenoMeseroEntity> desempenoMeseros) {
        insertarCabecera(cabecera);
        vaciarTopPlatillos(rango);
        insertarTopPlatillos(topPlatillos);
        vaciarDesempeno(rango);
        insertarDesempeno(desempenoMeseros);
    }
}
