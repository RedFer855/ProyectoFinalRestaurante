---
title: "Sesión 2026-08-01 — Fase 2c y 2d, Parte A: servidor de Mesas y Clientes"
tags:
  - sesion
  - restaurante
  - fase2c
  - fase2d
  - mesas
  - clientes
  - supabase
  - rls
date: 2026-08-01
branch: feat/fase2cd-mesas-clientes
autor_cambios: claude
---

# Sesión 2026-08-01 — Fase 2c y 2d, Parte A: servidor de Mesas y Clientes

> [!success] Resultado
> Misma sesión que completó la [[Sesión 2026-08-01 - Fase 2c y 2d completas, Parte B — Mesas y Clientes|Parte B]]: el usuario autorizó el MCP de Supabase a mitad de sesión, lo que permitió terminar la Parte A de las dos fases en el mismo hilo de trabajo — algo que normalmente se reparte entre agentes. [[Módulo Mesas]] y [[Módulo Clientes]] quedan **verified**, no solo con código.

---

## Cómo se autorizó el MCP

El usuario ya tenía `.mcp.json` con el servidor `supabase` configurado (paso 1 de la guía de Supabase), pero sin autenticar. Corrió `claude /mcp` en una terminal aparte y autorizó el flujo OAuth. `list_projects` confirmó la conexión contra **Restaurante** (`mxarlisuueovxvttytcm`, Postgres 17.6, `us-east-2`).

---

## Paso 0: el DDL real no coincidía con lo que asumían los planes

`list_tables(verbose=true)` mostró que `mesa` solo tenía `id_mesa`, `capacidad`, `id_estado` — sin `numero_mesa` ni `ubicacion` — y que `clientes` usa `nombres`/`apellidos` (plural), no `nombre`/`apellido`. Dos correcciones, cada una resuelta distinto:

- **`numero_mesa`/`ubicacion`**: se agregaron como columnas reales en `mesa`, en vez de aplicar al pie de la letra la instrucción del plan de "no inventar columnas". Razonamiento completo en el addendum de [[ADR-007 - Estados operativos en catálogos propios, separados de estado_general]] — en resumen, son datos de negocio que el admin ingresa (parte de las historias del plan), no detalles inventables, y el costo de la alternativa (usar `id_mesa` como número) era una regresión real: una mesa creada offline no tendría número para mostrar hasta sincronizar.
- **`nombres`/`apellidos`**: se corrigió del lado Android (`ClienteDto`, `CrearClienteDto`, `ActualizarClienteDto`), no la base — caso textual de "si algo ya existe con otro nombre, gana lo que hay en la base" del protocolo. `MesaDto` y compañía no necesitaron ningún cambio: la corrección de `numero_mesa`/`ubicacion` los dejó exactamente donde ya estaban.

También se encontraron, sin que el plan las mencionara: `clientes.correo` (existe, ningún plan la pidió, se dejó fuera de la vista) y una policy RLS preexistente `"mesa cambio de estado mesero"` que le daba a mesero `UPDATE` directo sobre `mesa` — el agujero exacto que ADR-007 existe para cerrar. Se sacó.

---

## Migraciones aplicadas

Dos migraciones vía `apply_migration` (`fase2c_mesas_catalogo_columnas_rpc_vista`,
`fase2d_clientes_normalizacion_triggers_rpc_vista`) más una de corrección
(`fase2c_2d_cerrar_gap_execute_public_y_search_path`). Contenido completo, DDL final y la
tabla de objetos agregados están en [[Esquema de Base de Datos]] — no se repite acá.

> [!warning] El clasificador de modo automático bloqueó la primera migración de Clientes
> `apply_migration` para Clientes fue rechazado por el clasificador de Claude Code (no un
> error de Supabase). Se le mostró al usuario exactamente qué contenía la migración
> (eliminar `uq_clientes_identidad`, crear el índice normalizado, los triggers y el RPC) y
> se reintentó tras su confirmación explícita — el usuario primero pidió una explicación más
> profunda de por qué la normalización de identidad tiene que vivir en la base y no solo en
> el domain de Android (razón: el `publishable key` está en el APK, cualquiera le puede
> pegar directo a PostgREST sin pasar por `ValidadorCliente`; y aunque no fuera así, hay una
> ventana de carrera entre el `SELECT` y el `INSERT` del RPC que ningún validador de
> cliente puede cerrar, solo un índice único en la base).

## El gap que encontró `get_advisors`

Tras la primera pasada, `get_advisors(security)` marcó `cambiar_estado_mesa` y
`buscar_o_crear_cliente` como ejecutables por `anon` **a pesar de** el
`revoke execute ... from anon` explícito en la migración. Causa: Postgres le da `EXECUTE` a
`PUBLIC` por default al crear una función, y revocarle a un rol puntual no quita lo que ese
rol hereda de `PUBLIC`. Se corrigió revocando de `PUBLIC` explícitamente, y de paso se cerró
el mismo gap —preexistente, sin relación con esta sesión— en `impedir_borrado_platillo` e
`impedir_borrado_categoria_con_platillos` del Menú, que la bóveda ya documentaba como regla
("revocale EXECUTE a anon y authenticated") pero nunca se había aplicado. `get_advisors`
final: **0 errores**, solo `WARN` preexistentes (exposición en el schema de GraphQL en toda
la base, sin relación con Mesas/Clientes; leaked-password-protection de Auth).

También se agregó `set search_path = public` a las dos funciones de trigger nuevas
(`impedir_borrado_mesa`, `impedir_borrado_cliente_con_pedidos`), que habían quedado con
`search_path` mutable — mismo vector que ADR-007 documenta para las `SECURITY DEFINER`,
aplicado acá por prolijidad aunque no lo sean.

---

## Verificación

Las pruebas de aceptación de §2.7 de cada plan se corrieron dentro de transacciones
revertidas (`BEGIN`/`ROLLBACK`), simulando cada rol con `set local role authenticated` +
`set local request.jwt.claim.sub = '<uuid-real-de-perfiles>'`. Detalle caso por caso en
[[Módulo Mesas]] y [[Módulo Clientes]].

Nota técnica: `execute_sql` solo devuelve el resultado del **último** `SELECT` de un script
multi-sentencia — los pasos intermedios no se ven. Hubo que juntar cada verificación en una
tabla temporal (`insert into resultados values (...)`) y hacer un único `SELECT` final,
en vez de intercalar `SELECT`s de diagnóstico.

También hubo un bug en el script de verificación (no en la migración): el primer intento de
probar "borrar cliente con pedidos" comparó contra la identidad **normalizada**
(`'08011901'`) en vez de la que realmente quedó guardada (`'0801-1990-1'`, tal cual se
escribió — el RPC guarda el texto crudo, solo el índice está normalizado). El `WHERE` no
encontró la fila, el `DELETE` "pasó" sin que el trigger llegara a evaluarse, y el resultado
parecía un fallo real de la migración. Se corrigió el script, no la migración, y se agregó
un caso de control (borrar un cliente **sin** pedidos, que sí debe funcionar) para no volver
a confundir "la fila no existía" con "la regla no se aplicó".

Tras revertir todas las transacciones de prueba: `mesa` siguió en 4 filas, `estado_mesa` en
3, `clientes` y `pedido` en 0 — verificado con `count(*)` fuera de cualquier transacción.

---

## Documentación actualizada

- [[Esquema de Base de Datos]] — DDL real de `mesa` y `clientes`, tabla de objetos agregados
- [[ADR-007 - Estados operativos en catálogos propios, separados de estado_general]] — pasó de "propuesto" a "aceptado", con el addendum de `numero_mesa`/`ubicacion`
- [[Módulo Mesas]] y [[Módulo Clientes]] — `lifecycle: verified`, tablas de verificación
- [[Plan Fase 2c - CRUD de Mesas]] y [[Plan Fase 2d - CRUD de Clientes]] — banner de estado actualizado

---

## Relaciones

- [[Sesión 2026-08-01 - Fase 2c y 2d completas, Parte B — Mesas y Clientes]] — la Parte B, misma sesión
- [[Protocolo de Ejecución de un Plan]] — Parte A / Parte B
- [[Gate de Autoverificación]]
- [[Deuda Técnica - Pendientes]]
- [[Roadmap de Fases]] · [[Arquitectura Actual]]
