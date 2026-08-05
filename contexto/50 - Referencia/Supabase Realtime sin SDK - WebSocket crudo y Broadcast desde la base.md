---
title: "Supabase Realtime sin SDK — WebSocket crudo y Broadcast desde la base"
tags:
  - referencia
  - supabase
  - realtime
  - websocket
  - java
date: 2026-08-04
lifecycle: verified
---

# Supabase Realtime sin SDK — WebSocket crudo y Broadcast desde la base

> [!info] Fuente y fecha
> Documentación oficial de Supabase, consultada el **2026-08-04**:
> [Realtime Protocol](https://supabase.com/docs/guides/realtime/protocol),
> [Broadcast](https://supabase.com/docs/guides/realtime/broadcast),
> [Authorization](https://supabase.com/docs/guides/realtime/authorization),
> [Quotas](https://supabase.com/docs/guides/realtime/quotas),
> [Postgres Changes](https://supabase.com/docs/guides/realtime/postgres-changes).
> Verificado además contra el proyecto real `mxarlisuueovxvttytcm`: existen
> `realtime.send`, `realtime.broadcast_changes` y `realtime.topic`.

> [!abstract] Por qué esta nota existe
> El proyecto es **Java puro** y [[ADR-002 - Supabase Auth via REST directo (Retrofit) en vez del SDK Kotlin]]
> descartó el SDK `supabase-kt` por su interop de corrutinas. Realtime plantea la misma
> pregunta de nuevo, y la respuesta vuelve a ser la misma: **el protocolo es JSON sobre
> WebSocket y se habla directo**. Esta nota registra el protocolo para no tener que volver a
> investigarlo.

---

## 1. Realtime tiene tres mecanismos distintos, y se confunden

| Mecanismo | Qué hace | Costo en Postgres |
|---|---|---|
| **Postgres Changes** | Replica cambios de fila (CDC) a los clientes suscritos | **Escala con la cantidad de suscriptores** |
| **Broadcast** | Mensajes arbitrarios entre clientes de un tópico | O(1) por mensaje |
| **Broadcast from Database** | Un trigger de Postgres emite un Broadcast | O(1) por sentencia |

El tercero es el que combina lo útil de los dos: se dispara desde la base, pero el abanico lo
hace el servidor de Realtime (Elixir), no Postgres.

### El costo real de Postgres Changes

Es el dato que decide una arquitectura, citado textual de la documentación:

> *"When you make a single change to a table with 100 subscribed users, Realtime performs 100
> authorization checks — one per user"*

> *"Changes are also processed on a single thread to preserve their order, which means larger
> compute add-ons don't meaningfully increase Postgres Changes throughput"*

O sea: la visibilidad de cada fila depende de la RLS de cada usuario, así que hay que
evaluarla **por suscriptor y por cambio**, en serie. El throughput escala con la cantidad de
gente escuchando, no con la de escrituras. La propia documentación recomienda Broadcast para
volumen, o *"a separate 'public' table without RLS"*.

**Corolario práctico:** una tabla que está en la publicación `supabase_realtime` paga ese
costo; una que no, no. Es una línea de diferencia.

```sql
-- Qué tablas están realmente publicadas (verificar antes de asumir):
select p.pubname, c.relname
from pg_publication p
left join pg_publication_rel pr on pr.prpubid = p.oid
left join pg_class c on c.oid = pr.prrelid;
```

---

## 2. El protocolo sobre WebSocket

### Endpoint

```
wss://<PROJECT_REF>.supabase.co/realtime/v1/websocket?apikey=<PUBLISHABLE_KEY>&vsn=1.0.0
```

`vsn` acepta `1.0.0` (objeto JSON, default) o `2.0.0` (arreglo posicional). **Para Java
conviene `1.0.0`**: los campos con nombre se mapean con Gson sin escribir un deserializador
posicional.

### Envoltorio (v1.0.0)

Cinco campos: `topic`, `event`, `payload`, `ref`, `join_ref`.

```json
{
  "event": "phx_join",
  "topic": "realtime:presence-room",
  "payload": { "config": { "broadcast": { "ack": false, "self": false } } },
  "ref": "1",
  "join_ref": "1"
}
```

El tópico siempre va prefijado con `realtime:`. El nombre real del canal es lo que viene
después (`realtime:pedidos` → tópico `pedidos`), y es lo que devuelve `realtime.topic()` del
lado SQL.

### Unirse a un canal

```json
{
  "config": {
    "broadcast": { "ack": false, "self": true },
    "presence": { "enabled": true, "key": "user_id-827" },
    "postgres_changes": [ { "event": "*", "schema": "public", "table": "messages" } ],
    "private": true
  },
  "access_token": "optional-jwt"
}
```

- `private: true` → Realtime evalúa la RLS de `realtime.messages` (§3).
- `postgres_changes` presente → activa el mecanismo caro de §1. **Omitirlo** si solo se usa
  Broadcast.
- `access_token` → el JWT de la sesión. Sin él, un canal privado rechaza el join.

### Heartbeat

Cada **25 segundos**, sobre el tópico literal `phoenix`:

```json
{ "topic": "phoenix", "event": "heartbeat", "ref": "26", "payload": {} }
```

Sin heartbeat el servidor corta la conexión. OkHttp tiene `pingInterval` a nivel WebSocket,
pero eso es un ping **de protocolo**: no reemplaza al heartbeat de Phoenix, que es un mensaje
de aplicación. Hacen falta los dos.

### Refrescar el token sin re-unirse

```json
{ "topic": "realtime:chat-room", "event": "access_token", "ref": "10",
  "payload": { "access_token": "eyJhbGciOiJIUzI1NiIs..." } }
```

No hay respuesta si sale bien. Si falla: error de sistema y cierre del canal.

### Respuestas del servidor

```json
{ "status": "ok", "response": { "postgres_changes": [] } }
```
```json
{ "status": "error", "response": { "reason": "InvalidJWTExpiration: Token expired" } }
```

Códigos de error a distinguir: `MalformedJWT`, `Unauthorized`, `InvalidJWTExpiration`,
`ClientJoinRateLimitReached`, `InitializingProjectConnection`, `too_many_channels`,
`too_many_connections`, `too_many_joins`.

> [!warning] Un `InvalidJWTExpiration` no se reintenta
> Reconectar con el mismo token vencido produce el mismo error para siempre. La
> documentación recomienda backoff exponencial `[1000, 2000, 5000, 10000]` ms tras un
> `phx_error`, pero eso aplica a fallos **transitorios**. Un token vencido es permanente
> hasta que alguien lo renueve.

### Recibir un broadcast

```json
{ "topic": "realtime:chat-room", "event": "broadcast",
  "payload": { "event": "message", "type": "broadcast",
               "meta": { "id": "006554ce-…" }, "payload": { "content": "oi" } } }
```

El `payload.event` de adentro es el nombre que eligió quien emitió; el `event` de afuera
siempre dice `broadcast`.

---

## 3. Emitir desde Postgres

Firmas reales, verificadas en el proyecto el 2026-08-04:

```
realtime.send(payload jsonb, event text, topic text, private boolean)
realtime.broadcast_changes(topic_name text, event_name text, operation text,
                           table_name text, table_schema text,
                           new record, old record, level text)
realtime.topic()  -- devuelve el tópico que el cliente intenta unir
```

`broadcast_changes` manda la fila entera; `send` manda lo que uno quiera. **Para un diseño de
"señal, no dato", `send` con un payload mínimo es lo correcto** — ver
[[ADR-008 - Tiempo real como señal, por Broadcast desde la base]].

Trigger de ejemplo, de la documentación oficial:

```sql
CREATE OR REPLACE FUNCTION public.your_table_changes()
RETURNS trigger
SECURITY DEFINER SET search_path = ''
AS $$
BEGIN
    PERFORM realtime.broadcast_changes(
        'topic:' || NEW.id::text, TG_OP, TG_OP,
        TG_TABLE_NAME, TG_TABLE_SCHEMA, NEW, OLD);
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;
```

> [!tip] `FOR EACH STATEMENT` cuando el payload no lleva la fila
> Si el mensaje es una señal, un `INSERT` de 20 filas no necesita 20 mensajes. Un trigger
> `FOR EACH STATEMENT` emite uno solo. No se puede usar `NEW`/`OLD` ahí — pero justamente no
> hacen falta.

> [!danger] Toda función creada en `public` queda expuesta como RPC
> Regla ya aprendida en este proyecto (ver [[Protocolo de Ejecución de un Plan]], §1): hay
> que `revoke execute … from public, anon, authenticated` en la misma migración. Postgres le
> da `EXECUTE` a `PUBLIC` por defecto, así que revocarle solo a `anon` **no alcanza**.

---

## 4. Autorización de canales privados

Realtime genera la policy consultando `realtime.messages` **y revirtiendo la consulta** — la
tabla no guarda nada, es el vehículo de la RLS. El campo `extension` distingue `broadcast` de
`presence`.

```sql
create policy "authenticated can receive broadcast"
on "realtime"."messages"
for select to authenticated
using (
  exists (
    select user_id from rooms_users
    where user_id = (select auth.uid())
      and room_topic = (select realtime.topic())
      and realtime.messages.extension in ('broadcast')
  )
);
```

`INSERT` solo hace falta si los **clientes** publican. Si el único emisor es un trigger
`SECURITY DEFINER`, se omite: un cliente que no puede insertar es un cliente que no puede
inyectar mensajes falsos en el canal.

---

## 5. Cuotas (2026-08-04)

| Límite | Free | Pro |
|---|---|---|
| Conexiones concurrentes | **200** | 500 |
| Mensajes por segundo | **100** | 500 |
| Joins de canal por segundo | **100** | 500 |
| Canales por conexión | 100 | 100 |
| Tamaño de payload | 256 KB | 3.000 KB |

Al excederlos el cliente recibe `too_many_channels`, `too_many_connections` o
`too_many_joins`. El dashboard tiene un **Realtime Inspector** para ver los mensajes en vivo —
la forma más rápida de comprobar que un trigger está emitiendo.

---

## 6. Lo que esto significa para un cliente Java

- **No hace falta ninguna dependencia nueva.** `okhttp3.WebSocket` /
  `okhttp3.WebSocketListener` existen desde OkHttp 3.5, y este proyecto ya tiene
  **OkHttp 3.14.9** en el classpath como transitiva de Retrofit 2.11.0 (verificado en la
  caché de Gradle el 2026-08-04). Es Java puro: **no arrastra `kotlin-stdlib`**, a diferencia
  de OkHttp 4.x.
- El framing de Phoenix v1.0.0 son cinco campos JSON: un POJO + Gson, que ya está.
- Lo que sí hay que escribir a mano y el SDK regalaría: el contador de `ref`, el heartbeat de
  25 s, el backoff de reconexión y el reenvío de `access_token`. Son ~200 líneas y se
  testean sin red si el `ScheduledExecutorService` se inyecta.

---

## Relaciones

- [[ADR-008 - Tiempo real como señal, por Broadcast desde la base]] — la decisión que usa esto
- [[Plan Fase 3 - Pedidos en Tiempo Real]] — el plan que lo implementa
- [[ADR-002 - Supabase Auth via REST directo (Retrofit) en vez del SDK Kotlin]] — el precedente
- [[Librerias Java-Friendly vs Kotlin-Only]] — por qué se evita todo lo Kotlin-first
- [[Offline-First con Room y Outbox]] — dónde encaja la señal
- [[Supabase Auth REST - Login Android]] · [[Esquema de Base de Datos]]
