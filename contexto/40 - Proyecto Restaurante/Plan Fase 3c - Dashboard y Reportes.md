---
title: Plan Fase 3c — Dashboard principal y Reportes
tags:
  - restaurante
  - plan
  - fase3
  - reportes
  - dashboard
date: 2026-08-05
lifecycle: draft
---

# Plan Fase 3c — Dashboard principal y Reportes

> [!danger] Leé primero [[Protocolo de Ejecución de un Plan]]
> Contrato completo: división Parte A / Parte B, orden de lectura, reglas de oro y qué
> significa "terminado". **No es opcional.**

> [!warning] Depende de la Fase 3 **y** de la 3b — el orden es estricto
> De la **Fase 3** salen los contadores de `PedidoEntity` (2 de las 7 tarjetas del dashboard).
> De la **[[Plan Fase 3b - Toma del Pedido]]** salen las filas de `detalle_pedido`: sin ellas
> **todo el reporte da cero**, y un módulo que no se puede ver funcionando no se puede dar por
> terminado. Room entra en **v6** y sale en **v7**.

> [!success] Esta fase mata la maqueta
> Al terminar, `grep -r "DatosMaqueta" app/src` **no devuelve nada**. Es el criterio de cierre:
> binario y verificable, no una impresión.

---

## 1. El encargo

Dos pantallas que hoy muestran números inventados:

- **Dashboard** (`ui/principal/InicioFragment`) — 7 tarjetas con constantes de `DatosMaqueta`.
  Ya filtra por permiso con `VistaPorPermiso`, y **no tiene ViewModel**: lee la maqueta directo.
- **Reportes** (`ui/reportes/ReportesFragment`) — ventas, pedidos, ticket promedio, top-5 de
  platillos y desempeño por mesero. Tiene un `ChipGroup` de rango (Hoy / Semana / Mes)
  **sin listener**: hoy no filtra nada. Solo admin.

### 1.1 Alcance

**Entra:** las dos pantallas con datos reales, el `ChipGroup` funcionando, el **patrón de
módulo solo-lectura** (§2), y `ui/maqueta/` eliminado del repo.

**No entra**

| Qué | Por qué |
|---|---|
| **Gráficos** (barras, líneas) | Toda librería de charting razonable es una dependencia nueva y varias son Kotlin-first — pesa contra [[Librerias Java-Friendly vs Kotlin-Only]] y contra [[Presupuestos de Rendimiento en Gama Baja]]. Los 5 reportes pedidos son números y listas |
| Exportar a PDF/CSV, compartir | No se pidió; arrastra permisos de almacenamiento y `FileProvider` |
| Rango personalizado con `DatePicker` | Los 3 chips son el requisito. El RPC queda con la forma que lo permite después |
| Comparativa contra el período anterior ("+12% vs. la semana pasada") | Tentador y barato en SQL, pero duplica la superficie de la instantánea y no se pidió |

### 1.2 Historias

| # | Historia | Rol |
|---|---|---|
| 1 | Ver de un vistazo el estado del turno al abrir la app | los tres, filtrado por permiso |
| 2 | Que esos números sean reales y se actualicen solos | los tres |
| 3 | Ver ventas, pedidos y ticket promedio de hoy / la semana / el mes | admin |
| 4 | Ver qué platillos se piden más | admin |
| 5 | Ver cuánto vendió cada mesero | admin |
| 6 | Que sin conexión la app **no mienta** sobre las ventas | admin |

---

## 2. El primer módulo solo-lectura: cómo se ve el patrón

No hay precedente en el proyecto: los 4 módulos con datos reales (Menú, Empleados, Mesas,
Clientes) tienen outbox, `Sincronizador`, `estado_sync` por fila y `Result<T>`. Pero no hay que
inventar nada — el contrato del proyecto tiene cuatro mitades y este módulo usa **tres**:

| Miembro del contrato | Módulos con escritura | Reportes |
|---|---|---|
| Lecturas `LiveData<T>` que no fallan | ✅ | ✅ igual |
| Disparo de refresco | `sincronizar()` vía WorkManager | `refrescar(rango)`, **directo** |
| `LiveData<EstadoSincronizacion>` | lo empuja el `SyncWorker` | lo empuja el propio repositorio |
| Escrituras `Result<T>` | ✅ | ❌ **no existe** |
| Outbox, `TipoOperacion`, `estado_sync`, `Sincronizador` | ✅ | ❌ **no existe** |

> **La regla, para el próximo módulo de este tipo:** un agregado derivado no tiene cola, no
> tiene marca de agua y no tiene nada que reintentar en segundo plano. El outbox existe para
> que **el trabajo del usuario** no se pierda; acá el usuario no produce trabajo.

### 2.1 No entra al `SyncWorker` único — y el reflejo es decir que sí

Es la decisión de arquitectura de este plan, así que conviene argumentarla. Si
`SincronizadorReportes` estuviera en la lista de `SyncApplication.FactoryDeSync`, la agregación
**más cara del sistema** se dispararía:

- en el periódico de 15 min, **en los 25 dispositivos** — incluido el teléfono de cocina, que
  no tiene permiso de ver reportes;
- en cada `sincronizar()` de cualquier ViewModel de cualquier módulo;
- **en cada señal del WebSocket de la Fase 3** — o sea, cada vez que alguien toma un pedido,
  25 dispositivos correrían una agregación sobre `pedido ⋈ detalle_pedido` del mes.

El peor patrón de acceso posible: la consulta más cara, por el evento más frecuente, en todos
los dispositivos, para una pantalla que casi nadie está mirando.

`ReporteRepositorioLocal` **no implementa `Sincronizador`** y **no está en la lista del
worker**. Refresca por tres disparadores explícitos, los tres con un usuario mirando:

1. `onStart` del Fragment, **solo si** la instantánea de ese rango está vieja
   (`ReglasReporte.esVieja` → más de **15 min**). Sin ese umbral, rotar el teléfono dispara
   una agregación.
2. Cambio de chip a un rango cuya instantánea está vieja o no existe.
3. Pull-to-refresh explícito.

El `Executor` se inyecta por constructor, igual que en `MesaRepositorioLocal`.
→ **ADR-013**.

---

## 3. Agregar en el servidor: por qué no rompe offline-first

[[ADR-005 - Offline-first obligatorio desde la Fase 2]] dice que Room es la única fuente de
verdad **para los datos que la app escribe**. Un reporte no es un dato que la app escriba: es
una **lectura derivada** sobre una ventana temporal que el dispositivo **nunca tuvo** — la
Fase 3 fija 48 h de retención local de pedidos, y "ventas del mes" son 30 días. No hay
conflicto que resolver, no hay escritura que perder, no hay LWW que aplicar.

Y la promesa de offline-first se mantiene en su forma correcta:

> Sin red, la app **no miente ni se rompe**: muestra la última instantánea **con su fecha**, o
> dice honestamente que ese rango nunca se descargó.

Lo que **sí** sería una violación es lo contrario: bajar 30 días de pedidos a Room para poder
sumar en el cliente. Eso contradiría R6 de la Fase 3 y los presupuestos de gama baja, para
calcular en un teléfono algo que Postgres hace con un índice.

→ **ADR-012**.

---

## 4. PARTE A — Servidor

### 4.1 Un solo RPC, no cuatro

Cuatro RPCs (`ventas_del_rango`, `top_platillos`, `desempeno_meseros`, `ticket_promedio`)
serían cuatro viajes sobre una conexión mala, cuatro modos de falla y — lo que de verdad
importa — **cuatro estados parciales posibles en la instantánea local**: el top-5 del mes
conviviendo con las ventas de hoy. Un solo RPC que devuelve `jsonb` es una instantánea
**atómica y coherente**.

```sql
create or replace function public.reporte_ventas(p_rango text)
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
-- Guard: solo admin (mismo público que Modulo.REPORTES / Accion.VER en el cliente).
-- El rango lo calcula el SERVIDOR en America/Tegucigalpa; el cliente solo manda
-- 'HOY' | 'SEMANA' | 'MES'. Los pedidos Cancelados (id_estado_pedido = 5) NO son ventas.
-- Devuelve: { generado_en, rango, desde, hasta, total_ventas, cantidad_pedidos,
--             ticket_promedio, top_platillos: [...], desempeno_meseros: [...] }
$$;

revoke execute on function public.reporte_ventas(text) from public, anon;
grant  execute on function public.reporte_ventas(text) to authenticated;
```

Tres decisiones que van dentro y hay que respetar:

1. **El rango lo calcula el servidor**, en `America/Tegucigalpa`. Si lo calculara el cliente,
   el "hoy" dependería del reloj y la zona del teléfono, y dos dispositivos verían ventas
   distintas del mismo día.
2. **Los cancelados no son ventas.** Filtrar `id_estado_pedido <> 5` en las tres métricas.
3. **`generado_en` viaja en la respuesta.** Es lo que la UI muestra como "datos al …" — importa
   la edad del **dato**, no la del archivo local.

### 4.2 Índices

Los dos de `detalle_pedido` los crea la 3b (§5.5 de ese plan). Verificar que existan antes de
medir; si la 3b se saltó ese punto, crearlos acá.

### 4.3 Pruebas de aceptación de la Parte A

Todas con datos sembrados dentro de `BEGIN … ROLLBACK` — **no son opcionales**: sin sembrar,
todo da cero y las pruebas pasan sin probar nada.

| # | Caso | Esperado |
|---|---|---|
| A1 | `reporte_ventas('HOY')` con 3 pedidos sembrados hoy | Total y cantidad correctos |
| A2 | Como **mesero** | Excepción / vacío por el guard de rol |
| A3 | Como **cocina** | Ídem |
| A4 | Con un pedido **Cancelado** entre los sembrados | **No** suma a las ventas |
| A5 | `'SEMANA'` con pedidos dentro y fuera de la ventana | Solo los de adentro |
| A6 | `'MES'` ídem | Solo los del mes |
| A7 | **Zona horaria**: un pedido a las 23:30 hora local | Cuenta en el día local, no en UTC |
| A8 | `top_platillos` con 7 platillos distintos | Devuelve 5, ordenados desc |
| A9 | Rendimiento con ~5.000 pedidos sembrados | Tiempo aceptable; si no, ver Riesgos |
| A10 | `get_advisors(security)` | 0 errores |

---

## 5. El dashboard: local salvo un número, y ese número se unifica

| Tarjeta | Origen |
|---|---|
| Pedidos pendientes | **Room** — `PedidoDao.observarConteoPorEstado(1)` |
| Pedidos en preparación | **Room** — `observarConteoPorEstado(2)` |
| Mesas ocupadas (X de Y) | **Room** — `MesaDao.observarConteoPorEstadoOperativo()` |
| Clientes registrados | **Room** — `ClienteDao.observarConteoActivos()` |
| Platillos activos | **Room** — `PlatilloDao.observarConteoActivos()` |
| Empleados activos | **Room** — `EmpleadoDao.observarConteoActivos()` |
| **Ventas del día** | **Servidor** |

**La tarjeta de ventas no llama al servidor por su cuenta: lee la fila `HOY` de la misma tabla
de instantáneas que escribe Reportes.** Se puede porque esa tarjeta ya está condicionada a
`Modulo.REPORTES / Accion.VER` — o sea, **solo admin la ve**, exactamente el mismo público que
el RPC. Y el rango que necesita, `HOY`, ya es una de las tres claves de la instantánea.

Lo que se gana:

- **Un solo agregado en todo el proyecto**: un solo lugar donde equivocarse con la zona horaria
  y con "los cancelados no cuentan".
- Abrir Reportes deja el dashboard actualizado gratis, y al revés.
- Para mesero y cocina el dashboard es **100 % local**: nunca dispara una llamada que iba a
  fallar con 403.

Diseñar dos caminos distintos para el mismo número sería garantizar que algún día digan cosas
diferentes.

---

## 6. Comportamiento sin conexión

| Pantalla | Sin red |
|---|---|
| Dashboard, 6 tarjetas locales | Funcionan completo. Room ya tiene todo |
| Dashboard, **"Ventas de hoy"** | Valor cacheado + subtítulo `"al 14:32"`. Si **nunca** se descargó: `—` y `"sin conexión"`. **Nunca `L 0.00`** — decir "cero" cuando lo que pasa es "no sé" es, en un dashboard de ventas, el error más caro posible |
| Reportes, rango **con** instantánea | Se muestra completa, con franja `"Datos al 5 ago, 14:32 · sin conexión"` |
| Reportes, rango **sin** instantánea | Vacío honesto: *"Todavía no descargaste este rango. Conectate para verlo."* + Reintentar. Los otros chips siguen navegables |
| Reportes con red pero el refresco falla | Se sigue mostrando la instantánea vieja + el error. **Nunca se borra lo que ya se tenía** por una falla de refresco |

Las tres instantáneas persisten por separado, así que cambiar de chip offline sigue mostrando
lo que haya de cada rango.

---

## 7. PARTE B — Android

### 7.1 `domain`

`RangoReporte` (enum `HOY`/`SEMANA`/`MES`), `ReporteVentas`, `ConteoPlatillo`,
`DesempenoMesero`, `ResumenInicio` (los 7 valores del dashboard), `ReglasReporte`
(`esVieja(generadoEn, ahora)` con el umbral de 15 min — **el reloj se inyecta**, regla 3 de
[[Estrategia de Pruebas Android]]), y los dos contratos: `ReporteRepository`,
`ResumenRepository`. Ninguno devuelve `Result<T>` (§2).

### 7.2 `data`

Room **v7**: tres entidades de instantánea (`ReporteVentasEntity` con PK `rango`,
`ConteoPlatilloEntity`, `DesempenoMeseroEntity`), `ReporteDao` con **`reemplazarRango`
transaccional** (borrar las filas del rango + insertar las nuevas, en una sola transacción —
si no, un rango con menos meseros que antes deja filas huérfanas), mapper,
`Migraciones.DE_6_A_7` + test.

Las **5 consultas de conteo** se agregan a los DAOs existentes (`PedidoDao`, `MesaDao`,
`ClienteDao`, `PlatilloDao`, `EmpleadoDao`) — no hay entidades nuevas para eso.

`SupabaseReporteApi` + DTOs + `ReporteRemoto`. `ReporteRepositorioLocal` (caché, edad,
degradación sin red, su propio `EstadoSincronizacion`). `ResumenRepositorioLocal`
(`MediatorLiveData` que combina los 6 contadores locales + la instantánea `HOY`).

### 7.3 `ui`

`ReportesViewModel` + `EstadoReportes` + factory, con el `ChipGroup` **funcionando** y el rango
sobreviviendo a la rotación (vive en el ViewModel, y el chip se re-marca **desde el estado**,
nunca al revés). `InicioViewModel` + `EstadoInicio` + `TarjetaInicio` — hoy `InicioFragment` no
tiene ViewModel y arma la grilla en un bucle; el filtrado por permiso se mueve al ViewModel.
Tres adapters nuevos (`ListAdapter` + `DiffUtil`).

### 7.4 Lo que se borra

| Qué | Acción |
|---|---|
| `ui/maqueta/DatosMaqueta.java` | **Eliminar el archivo** |
| `ui/maqueta/` | **Eliminar el paquete** |
| Strings y colores que solo usaba la maqueta | Auditar y eliminar |

---

## 8. Entregables

| # | Entregable | Parte | Tests |
|---|---|---|---|
| **E0** | Verificar el nombre real de la columna de nombre en `public.usuarios` y que existan los índices de `detalle_pedido`. Documentar en [[Esquema de Base de Datos]] | A | — |
| **E1** | Parte A: `reporte_ventas(text)` + grants + las 10 pruebas de §4.3 | A | 10 SQL |
| **E2** | `domain`: `RangoReporte`, `ReporteVentas`, `ConteoPlatillo`, `DesempenoMesero`, `ResumenInicio`, `ReglasReporte`, los dos contratos | B | ~18 |
| **E3** | Room v7: 3 entidades, `ReporteDao` (incl. `reemplazarRango`), mapper, `DE_6_A_7` + test de migración | B | ~14 |
| **E4** | Las 5 consultas de conteo en los DAOs existentes | B | ~8 |
| **E5** | `SupabaseReporteApi` + DTOs + `ReporteRemoto` | B | ~10 |
| **E6** | `ReporteRepositorioLocal` — caché, edad, degradación sin red, `EstadoSincronizacion` propio | B | ~14 |
| **E7** | `ResumenRepositorioLocal` — `MediatorLiveData` de 6 contadores + instantánea `HOY` | B | ~10 |
| **E8** | `ReportesViewModel` + `EstadoReportes` + factory + `ChipGroup` funcionando | B | ~16 |
| **E9** | `InicioViewModel` + `EstadoInicio` + `TarjetaInicio` + filtrado por permiso en el ViewModel | B | ~12 |
| **E10** | UI: los dos Fragments reescritos, 3 adapters, chips en XML, franja de "datos al …", estados vacíos | B | — (manual) |
| **E11** | **Borrar `ui/maqueta/` completo** + limpiar strings/colores huérfanos + ajustar tests que la referencien | B | ajustar |
| **E12** | Documentación: [[Módulo Reportes]], sección de Inicio en [[Arquitectura Actual]], **ADR-012 y ADR-013** | — | — |

**Piso de la suite:** entrando desde ≥ 570 (post-3b), al cerrar: **≥ 660**.

### ADR que se escriben al ejecutar

| ADR | Decisión |
|---|---|
| **ADR-012** | Reportes se agregan en el servidor; Room guarda una **instantánea fechada**, no la verdad. Incluye por qué no contradice a ADR-005 y qué se muestra sin red |
| **ADR-013** | Los módulos de solo lectura **no entran al `SyncWorker`**. Define el contrato reducido y la regla del refresco por demanda con edad mínima |

Sin ADR, va en [[Módulo Reportes]]: el rango lo calcula el servidor en `America/Tegucigalpa`;
los cancelados no son ventas; un solo RPC y no cuatro.

---

## 9. Pruebas de aceptación de la Parte B

| # | Caso | Esperado |
|---|---|---|
| B1 | `cambiarRango(MES)` con instantánea fresca | **No** llama al remoto; emite la cacheada |
| B2 | `cambiarRango(MES)` sin instantánea | Estado vacío + llamada al remoto |
| B3 | Refresco que falla habiendo instantánea previa | Se conserva la instantánea, se muestra el error. **No se borra nada** |
| B4 | `reemplazarRango` con menos meseros que antes | Los sobrantes desaparecen (sin filas huérfanas) |
| B5 | `ReglasReporte.esVieja` en los bordes (14:59 / 15:01) | Correcto |
| B6 | Rotación con `SEMANA` seleccionado | El ViewModel conserva el rango; el chip se re-marca desde el estado |
| B7 | `InicioViewModel` como **cocina** | Solo sus tarjetas, **sin** "Ventas de hoy", y **cero** llamadas al remoto |
| B8 | `InicioViewModel` como admin sin instantánea `HOY` | La tarjeta muestra `—`, **no** `L 0.00` |
| B9 | Room emite un pedido nuevo | El contador de pendientes sube sin refrescar |
| B10 | `grep -r "DatosMaqueta" app/src` | **0 resultados** |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| **Sin la 3b, `detalle_pedido` está vacío y todo da 0** — el módulo se ve "terminado" y nadie sabe si funciona | Orden estricto 3b → 3c. Las pruebas A1/A4/A8 **exigen sembrar datos** dentro de `ROLLBACK`; no son opcionales |
| El RPC se pone lento con el volumen | A9 lo mide con ~5.000 pedidos. Si aparece, la salida es una **vista materializada** refrescada por `pg_cron`: no cambia una línea de Android, el contrato sigue siendo el mismo `jsonb` |
| Alguien "mejora" el módulo metiéndolo al `SyncWorker` | ADR-013 + un test que fija la lista de `FactoryDeSync` |
| **Zona horaria**: reportes desfasados que nadie nota | A7 lo prueba explícitamente. Es la clase de bug que solo se detecta con una prueba escrita a propósito |
| El dashboard dispara el RPC en cada `onStart` | `ReglasReporte.esVieja` con umbral de 15 min. Cubierto por B1 |
| La instantánea guarda datos de ventas en Room sin cifrar | Menos sensible que **P-027** (datos personales), pero es información del negocio. Se anota junto a P-027, no se resuelve acá |

---

## Relaciones

- [[Protocolo de Ejecución de un Plan]] — se lee antes que esto
- [[Plan Fase 3b - Toma del Pedido]] — **precondición**: sin ella todo el reporte da cero
- [[Plan Fase 3 - Pedidos en Tiempo Real]] — de ahí salen los contadores de pedidos
- [[ADR-005 - Offline-first obligatorio desde la Fase 2]] — por qué agregar en el servidor no lo contradice
- [[Offline-First con Room y Outbox]] · [[Estrategia de Pruebas Android]]
- [[Presupuestos de Rendimiento en Gama Baja]] · [[Librerias Java-Friendly vs Kotlin-Only]]
- [[Deuda Técnica - Pendientes]] — P-027 (la instantánea sin cifrar se anota ahí)
- [[Esquema de Base de Datos]] · [[Roadmap de Fases]] · [[Gate de Autoverificación]]
- [[Guía de Diseño Visual]] · [[Arquitectura Actual]]
- [[Módulo Reportes]] — **todavía no existe**: lo crea E12 de este plan
