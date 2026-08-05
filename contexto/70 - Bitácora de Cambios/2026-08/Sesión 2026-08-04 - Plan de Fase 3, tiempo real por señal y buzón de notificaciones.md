---
title: "Sesión 2026-08-04 — Plan de Fase 3, tiempo real por señal y buzón de notificaciones"
tags:
  - sesion
  - fase3
  - pedidos
  - realtime
  - planificacion
date: 2026-08-04
branch: feat/fase2cd-mesas-clientes
autor_cambios: Claude (Opus 5) + usuario
---

# Sesión 2026-08-04 — Plan de Fase 3, tiempo real por señal y buzón de notificaciones

> [!info] Sesión de **planificación**, no de implementación
> No se tocó una línea de código de `app/`. Lo que se produjo es el plan, su ADR, la nota de
> referencia del protocolo y dos hallazgos de auditoría.

## Qué se pidió

Pedidos como Fase 3, con tres exigencias que no estaban en fases anteriores:

1. Los pedidos que entran se ven en los demás dispositivos **sin refrescar**.
2. Un **buzón de notificaciones** en el menú ⋮, reemplazando al selector "Ver como otro rol".
3. Soportar **≥ 25 dispositivos concurrentes** sin cuello de botella, con un debounce de 3 s
   para colapsar las ráfagas, y una lista que **no cargue todo de golpe** (FIFO + scroll).

## Decisiones tomadas con el usuario

| Pregunta | Respuesta |
|---|---|
| Alcance | **Tablero en tiempo real primero.** La toma del pedido (carrito + detalle) se difiere a una Fase 3b |
| Eventos del buzón | Pedido nuevo (cocina/admin), pedido listo (el mesero que lo tomó), errores de sincronización |
| Selector de rol | **Eliminarlo completo** — el ⋮ queda solo para el buzón |

## La decisión de fondo: el canal transporta una señal, no datos

Está completa en [[ADR-008 - Tiempo real como señal, por Broadcast desde la base]]. El
resumen de por qué importa:

El impulso natural es que el WebSocket traiga la fila. Si lo hiciera, harían falta un segundo
camino de escritura a Room, un segundo resolvedor de conflictos, un segundo mapper y un
segundo lugar donde equivocarse con la marca de agua — o sea **una copia del sincronizador**
que se desincroniza el día que alguien toque uno de los dos.

Con la señal (`{"t":"pedido"}`, 14 bytes), el canal es **un cuarto disparador** de la cadena
que ya existe: `SyncScheduler.solicitar()` → `SyncWorker` → `SincronizadorPedidos` → Room →
LiveData. `data/realtime` no conoce ni una clase del dominio de Pedidos.

Y resuelve el requisito de las 25 conexiones por la misma razón. La documentación de Supabase
es explícita sobre *Postgres Changes*: *"a single change to a table with 100 subscribed users
[performs] 100 authorization checks"*, en un solo hilo. El costo escala con la **cantidad de
suscriptores**. Con Broadcast desde un trigger `FOR EACH STATEMENT`, Postgres hace trabajo
**O(1)** por escritura sin importar cuántos escuchen — el abanico lo hace el servidor de
Realtime.

## Dos premisas viejas que resultaron falsas

El Javadoc de `SyncScheduler` (escrito el mismo 2026-08-04, más temprano) había descartado el
push real con dos argumentos. **Los dos se cayeron al verificarlos:**

1. *"Necesitaría el SDK que ADR-002 evitó a propósito"* → **falso**. El protocolo es JSON
   sobre WebSocket, y `okhttp3.WebSocket` existe desde OkHttp 3.5. Este proyecto ya tiene
   **OkHttp 3.14.9 en el classpath** como transitiva de Retrofit 2.11.0 (verificado en la
   caché de Gradle). Es Java puro: no arrastra `kotlin-stdlib`. **Cero dependencias nuevas.**
2. *"Una conexión persistente encaja mal con el offline-first"* → **falso si el canal no es la
   fuente de los datos**. Room sigue siendo la única fuente de verdad; el socket es un timbre
   más rápido que el temporizador de 15 minutos, y sin él la app degrada a lo que ya hacía.

El protocolo quedó documentado en
[[Supabase Realtime sin SDK - WebSocket crudo y Broadcast desde la base]] para no volver a
investigarlo: endpoint, envoltorio Phoenix v1.0.0, `phx_join`, heartbeat de 25 s,
`access_token`, códigos de error, cuotas por plan y las firmas reales de `realtime.send` /
`realtime.broadcast_changes` / `realtime.topic` verificadas contra el proyecto.

## Sobre el debounce que se pidió

Se pidió "un debounce de 3 segundos para recibir las 25 peticiones en una sola". Implementado
literal —esperar 3 s de silencio— cumpliría el requisito pero **le sumaría 3 segundos a todos
los pedidos**, incluido el caso normal de uno solo. Se implementa como *throttle con borde de
salida*: dispara la primera al instante, traga la ráfaga, y garantiza una pasada de cierre.
Con 25 señales en 500 ms da **2** sincronizaciones, no 25.

También se agregó algo que el enunciado no pedía y hace falta igual: **jitter de 0–1500 ms**.
Sin él los 25 dispositivos reciben el mismo broadcast en el mismo milisegundo y disparan 25
`GET` idénticos a la vez — se cambiaría un cuello de botella por otro.

Y quedó anotado por qué `ExistingWorkPolicy.KEEP` no alcanzaba: ya descarta pedidos
concurrentes, pero **con pérdida** — si la última señal llega justo después de que el worker
leyó su página, ese cambio espera hasta 15 minutos. El borde de salida existe para eso.

## Hallazgos de la auditoría del código

### P-029 — tres sincronizadores todavía pierden filas al paginar el delta

Leyendo los cuatro sincronizadores para decidir cuál copiar, se encontró que la corrección del
delta paginado del 2026-08-04 (marca fija + `offset` + tope de páginas + página en una
transacción) **se aplicó solo a `SincronizadorMenu`**. Mesas, Clientes y Empleados siguen
avanzando la marca dentro del bucle, sin `offset`.

Hoy no muerde porque son tablas de 4 filas. En Pedidos **mordería seguro**: una tanda del
mediodía comparte `actualizado_en`, y con más de 50 filas iguales las excedentes no se bajan
nunca. Registrado como **P-029**; el plan solo se compromete a no reintroducirlo.

### `pedido` no tiene con qué sincronizarse todavía

Verificado contra la base real: `pedido` no tiene `actualizado_en` ni estado operativo propio,
`pedido.fecha` es `TIMESTAMP` sin zona (pendiente anotado en [[Esquema de Base de Datos]] desde
julio), y la publicación `supabase_realtime` existe pero **está vacía** — que es justo como
debe quedar. Las tres tablas de pedidos están vacías, así que toda la Parte A se aplica sin
migrar datos: el momento de arreglar `fecha` es ahora y es gratis.

## Archivos de la bóveda

| Archivo | Acción |
|---|---|
| `40 - Proyecto Restaurante/Plan Fase 3 - Pedidos en Tiempo Real.md` | **Nuevo** |
| `45 - Decisiones/ADR-008 - Tiempo real como señal, por Broadcast desde la base.md` | **Nuevo** |
| `50 - Referencia/Supabase Realtime sin SDK - WebSocket crudo y Broadcast desde la base.md` | **Nuevo** |
| `40 - Proyecto Restaurante/Roadmap de Fases.md` | Fase 3 asignada a Pedidos; se agrega 3b |
| `40 - Proyecto Restaurante/Deuda Técnica - Pendientes.md` | **P-029** + su fila de historial |
| `40 - Proyecto Restaurante/Arquitectura Actual.md` | Próximo paso 7 actualizado |

## Qué falta

- **Ejecutar el plan.** Parte A (servidor) primero y verificada, Parte B después — el patrón
  que funcionó en 2a y 2c/2d.
- **P-009 sube de prioridad**: sin refresh de token, el canal muere al vencer la sesión y no
  se recupera solo. El plan lo maneja sin bucle de reintentos, pero es un límite real.
- La verificación de R1 ("se ve en < 5 s en otro dispositivo") **necesita dos dispositivos
  reales**. Es prueba del usuario, no del agente.

---

## Relaciones

- [[Plan Fase 3 - Pedidos en Tiempo Real]] — el plan producido
- [[ADR-008 - Tiempo real como señal, por Broadcast desde la base]]
- [[Supabase Realtime sin SDK - WebSocket crudo y Broadcast desde la base]]
- [[Sesión 2026-08-04 - La carga inicial del Menú y el trabajo único envenenado]] — de donde salió la corrección del delta que P-029 dice que falta portar
- [[Offline-First con Room y Outbox]] · [[Deuda Técnica - Pendientes]] · [[Roadmap de Fases]]
- [[Esquema de Base de Datos]] · [[Arquitectura Actual]]
