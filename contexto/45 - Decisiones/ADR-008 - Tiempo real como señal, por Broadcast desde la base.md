---
title: "ADR-008 — Tiempo real como señal, por Broadcast desde la base"
tags:
  - adr
  - decision
  - realtime
  - sincronizacion
date: 2026-08-04
estado: propuesto
---

# ADR-008 — Tiempo real como señal, por Broadcast desde la base

## Contexto

La Fase 3 pide que los pedidos que entran aparezcan en los demás dispositivos **sin que nadie
refresque**, con al menos **25 dispositivos concurrentes** y sin generar un cuello de botella
en la base.

Lo que ya existe (Fase 2b): Room como única fuente de verdad, outbox particionado por módulo,
un `SyncWorker` **único** y un sync delta con marca de agua por tabla. Cuatro disparadores lo
activan — app a primer plano, periódico de 15 min, sync-on-launch de cada ViewModel y cada
escritura local. Funciona, pero la latencia peor caso de un pedido entrante es de **15
minutos**.

Ya se había mirado el problema el 2026-08-04 (ver el Javadoc de `SyncScheduler`) y se optó por
"casi-tiempo-real sin push", con esta justificación: un push real *"necesitaría el SDK que
[[ADR-002 - Supabase Auth via REST directo (Retrofit) en vez del SDK Kotlin]] evitó a
propósito, y una conexión persistente encaja mal con el requisito no funcional #1"*.

**Ese razonamiento se revisa acá, porque las dos premisas resultaron falsas** (ver
[[Supabase Realtime sin SDK - WebSocket crudo y Broadcast desde la base]]):

1. No hace falta el SDK: el protocolo es JSON sobre WebSocket, y **OkHttp 3.14.9 —Java puro—
   ya está en el classpath** como transitiva de Retrofit.
2. La conexión persistente no pelea con el offline-first **si no es la fuente de los datos**.

## Decisión

**Tres decisiones encadenadas:**

### 1. El canal transporta una señal, no datos

El mensaje que viaja es `{"t":"pedido"}` — 14 bytes que significan *"hay novedades en el
módulo Pedidos"*. Quien lo recibe no escribe nada: llama a `SyncScheduler.solicitar()`, y el
`SincronizadorPedidos` que ya existe baja su delta por PostgREST como siempre.

El canal es **un cuarto disparador** de la cadena que ya está, no un camino paralelo.

### 2. Broadcast desde un trigger, no Postgres Changes

Un trigger `AFTER INSERT OR UPDATE OR DELETE ... FOR EACH STATEMENT` llama a
`realtime.send(…, 'pedidos', private => true)`. La tabla `pedido` **no** se agrega a la
publicación `supabase_realtime`.

### 3. Un socket por proceso, con debounce y jitter

El socket vive en la composition root (`SyncApplication`), no en un Fragment. Las señales
pasan por un *throttle con borde de salida* de 3 s (dispara la primera al instante, colapsa la
ráfaga, garantiza una pasada de cierre) más un jitter aleatorio de 0–1500 ms.

## Alternativas consideradas

| Opción | Pro | Contra | ¿Elegida? |
|---|---|---|---|
| **Seguir con el periódico de 15 min** | Cero código nuevo, cero conexiones | No cumple el requisito: un pedido puede tardar 15 min en verse. En un restaurante eso es no tener la función | ❌ |
| **Postgres Changes** (agregar `pedido` a la publicación) | Es "la" función de Realtime, un renglón de configuración, trae la fila lista | **Escala con la cantidad de suscriptores, no de escrituras**: 1 escritura × 25 suscriptores = 25 evaluaciones de RLS **en un solo hilo** (documentado por Supabase). Y trae la fila, así que habría que escribirla en Room por un segundo camino: duplica el sincronizador | ❌ |
| **Broadcast con la fila adentro** (`realtime.broadcast_changes`) | Un viaje menos: el dato llega con la señal | Segundo camino de escritura a Room → segundo resolvedor de conflictos, segundo mapper, segunda marca de agua. Y el payload esquiva la RLS del `GET`, que es donde la autorización está probada | ❌ |
| **SDK `supabase-kt`** | Reconexión, heartbeat y auth resueltos | Kotlin Multiplatform con `suspend`; contradice ADR-002 y ADR-004, y suma `kotlin-stdlib` al APK contra [[Presupuestos de Rendimiento en Gama Baja]] | ❌ |
| **Polling agresivo** (cada 30 s) | Trivial, sin conexión persistente | 25 dispositivos × 2 consultas/min = 3.000 consultas/hora **aunque no pase nada**. Peor para la batería y para la base que un socket ocioso | ❌ |
| **Señal por Broadcast desde trigger** | O(1) en Postgres por escritura sin importar cuántos escuchen; cero duplicación; degrada limpio; cero dependencias nuevas | Hay que escribir a mano el framing Phoenix, el heartbeat, el backoff (~200 líneas). Un viaje extra (señal → `GET`) | ✅ |

## Consecuencias

**Se gana:**

- **Cero duplicación de la lógica de sincronización.** `data/realtime` no conoce ninguna clase
  del dominio de Pedidos. El día que se agregue tiempo real a Mesas, es una línea de tópico.
- **Postgres hace trabajo constante por escritura**, sin importar si escuchan 5 dispositivos o
  50. El abanico lo hace el servidor de Realtime, que es lo que sabe hacer.
- **Room sigue siendo la única fuente de verdad** — regla 1 de
  [[Offline-First con Room y Outbox]] intacta.
- **Degradación limpia**: sin socket, los cuatro disparadores viejos siguen. No hay dos modos
  de funcionamiento.
- **Cero dependencias nuevas**, cero Kotlin en el APK.
- La RLS se sigue evaluando en el `GET`, donde ya está probada por rol.

**Se sacrifica:**

- **Un viaje extra.** La señal no trae el dato: hay que pedirlo. A cambio, ese pedido pasa por
  el camino ya probado. Con el debounce y el jitter el costo agregado es del orden de un
  segundo, contra los 15 minutos de antes.
- **~200 líneas de protocolo escritas a mano** (contador de `ref`, heartbeat de 25 s, backoff,
  reenvío de `access_token`). Testeables sin red si el `ScheduledExecutorService` se inyecta.
- **Una trampa a un renglón de distancia.** Agregar `pedido` a la publicación
  `supabase_realtime` "para probar" reintroduce exactamente el costo que esta decisión evita,
  sin romper nada visible. Queda documentado en el plan (§2.6) y se verifica con:
  ```sql
  select c.relname from pg_publication p
  join pg_publication_rel pr on pr.prpubid = p.oid
  join pg_class c on c.oid = pr.prrelid
  where p.pubname = 'supabase_realtime';   -- debe seguir sin 'pedido'
  ```
- **Depende de P-009.** Sin refresh de token, la sesión vence y el canal muere sin poder
  recuperarse solo. Se maneja sin bucle de reintentos y se cae al disparador periódico, pero
  P-009 sube de prioridad.

**Revisar esta decisión si:** el restaurante pasa de ~50 dispositivos concurrentes (cuota Free
= 200 conexiones), o si aparece un caso donde la latencia del viaje extra importe de verdad —
por ejemplo, mostrar el detalle de un pedido en vivo mientras se edita.

---

## Relaciones

- [[Plan Fase 3 - Pedidos en Tiempo Real]] — el plan que la implementa
- [[Supabase Realtime sin SDK - WebSocket crudo y Broadcast desde la base]] — el protocolo y las cuotas
- [[ADR-002 - Supabase Auth via REST directo (Retrofit) en vez del SDK Kotlin]] — el precedente de "REST directo, sin SDK"
- [[ADR-004 - Java + Views en vez de Kotlin + Compose]] — por qué el APK no debe arrastrar Kotlin
- [[ADR-007 - Estados operativos en catálogos propios, separados de estado_general]] — el otro ADR que sale de este dominio
- [[Offline-First con Room y Outbox]] — la infraestructura que se reutiliza
- [[Deuda Técnica - Pendientes]] — P-009, P-029
- [[Arquitectura Actual]]
