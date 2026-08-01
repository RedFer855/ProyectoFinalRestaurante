---
title: "Sesión 2026-07-31 — Plan técnico de Fase 2a (CRUD de Menú) y preparación de Supabase"
tags:
  - sesion
  - restaurante
  - fase2
  - fase2a
  - menu
  - storage
  - supabase
date: 2026-07-31
branch: feat/fase2-menu
autor_cambios: Claude Code
---

# Sesión 2026-07-31 — Plan técnico de Fase 2a y preparación de Supabase

> [!success] Resultado
> Arranca la Fase 2 de verdad: rama `feat/fase2-menu` creada desde la última versión de
> Fase 1, **todo el lado servidor del Menú aplicado y verificado en Supabase**, y el plan
> ejecutable escrito para un agente que solo tiene acceso al código. Cero cambios de
> código Android en esta sesión — es deliberado.

---

## Problema / motivo

La Fase 2a (CRUD de platillos y categorías, con la foto del platillo en Supabase Storage)
la va a implementar un agente **sin acceso a Supabase**. Para que eso funcione, alguien
con el conector tiene que dejar el servidor listo *antes* y escribir contra qué está
programando. Esta sesión es esa mitad.

## Corrección previa — la bóveda afirmaba algo falso

[[Conocimiento Principal]] y la nota
[[Sesión 2026-07-31 - Remediación P-005 P-012 P-013 P-020 y arranque Fase 2]] decían que
la Fase 2 ya había arrancado, con rama `feat/fase2-menu` y *"fundación de Room + outbox
agregada"*. **Nada de eso existía**: `git branch -a` no mostraba la rama y no había una
sola clase de Room en el repo. Se corrigieron las dos notas antes de seguir. Vale como
recordatorio de la regla 10 del [[contexto/AGENTS|protocolo de la bóveda]]: la bóveda es
la fuente de verdad, pero una nota escrita en futuro-presente ("se arranca…") envejece
como una afirmación falsa.

## Git

`feat/fase1-login` tenía 9 archivos modificados y 3 sin trackear sin commitear (la
remediación P-005/P-012/P-013/P-020 y sus tests). Se commitearon y pushearon ahí
(`dc1528e`), y **desde ese commit** se creó `feat/fase2-menu`. No sale de `master`:
`master` sigue en el bootstrap (`886033e`) y no tiene ni login funcional ni Empleados.

## Cambios aplicados en Supabase

Cuatro migraciones sobre el proyecto `mxarlisuueovxvttytcm`:

1. **`menu_estado_imagen_y_auditoria`** — `id_estado` y `actualizado_en` en `platillo` y
   `categoria`; `ruta_imagen` en `platillo`; `CHECK (precio > 0)`; únicos
   case-insensitive sobre `lower(btrim(...))` del nombre y de la descripción; índice del
   filtro por categoría; trigger `tocar_actualizado_en()`.
2. **`menu_vistas_y_reglas_de_borrado`** — `vista_platillos` y `vista_categorias`
   (`security_invoker = on`), y los dos triggers `BEFORE DELETE` que impiden borrar un
   platillo y borrar una categoría que todavía tiene platillos.
3. **`menu_bucket_storage_platillos`** — bucket `platillos`, público para lectura, 2 MB,
   solo `image/jpeg|png|webp`, con tres policies de escritura restringidas a `admin`.
4. **`menu_revocar_rpc_de_funciones_de_trigger`** — corrección de un problema que
   introdujeron las dos primeras (ver abajo).

### Tres decisiones que valen para lo que sigue

**`ruta_imagen` guarda la ruta, no la URL.** Guardar la URL completa deja todas las filas
apuntando a la nada el día que cambie el proyecto, el dominio o el nombre del bucket. La
fila guarda `x.jpg`; la URL pública la arma el cliente desde `BuildConfig.SUPABASE_URL`.

**El bucket es público para lectura, y es una decisión, no un descuido.** Uno privado
obliga a pedir una *signed URL* por imagen y a invalidar la caché de Glide al expirar:
mucho código en la ruta más caliente de la pantalla, en teléfonos de 2 GB. La foto de un
platillo es material de menú. Escribir sigue siendo solo de `admin`, y **no se creó
policy de `SELECT`** sobre `storage.objects`, así que *listar* el bucket está bloqueado
aunque *leer una foto por su ruta* funcione.

**Unicidad insensible a mayúsculas y espacios.** `uq_platillo_nombre` es un índice único
sobre `lower(btrim(nombre))`, no sobre `nombre`. "Baleada", "baleada" y `"Baleada "` son
el mismo platillo para un mesero, y tres filas así vuelven inútil el buscador.

### Un problema que las migraciones introdujeron y se corrigió en el momento

`get_advisors(security)` señaló que `impedir_borrado_platillo()` e
`impedir_borrado_categoria_con_platillos()` — funciones de trigger, `SECURITY DEFINER` —
quedaban invocables como `/rest/v1/rpc/<nombre>` **incluso por `anon`**. Llamarlas sueltas
falla por falta de contexto de trigger, pero no tienen por qué estar en la superficie de
la API. Se revocó `EXECUTE` a `anon` y `authenticated` sobre las tres funciones nuevas.

> [!tip] Regla general que deja
> **Toda función de trigger creada en el esquema `public` queda expuesta por PostgREST
> como un endpoint RPC.** Si se crea una, se le revoca `EXECUTE` en la misma migración.

## Verificación

Ocho casos ejercitados simulando cada rol dentro de una transacción **revertida al final**
(se confirmó después que `platillo` seguía con 5 filas y el precio del id 1 intacto en
35.00):

| Caso | Resultado |
|---|---|
| Mesero lee `vista_platillos` | ✅ 5 filas |
| Mesero edita un platillo | 🚫 0 filas afectadas (RLS) |
| Cocina crea un platillo | 🚫 *violates row-level security policy* |
| Admin edita → `actualizado_en` avanza solo | ✅ |
| Admin borra un platillo | 🚫 *"Los platillos no se borran, se desactivan…"* |
| Insertar `"  baleada SENCILLA "` | 🚫 *duplicate key … uq_platillo_nombre* |
| Insertar con `precio = 0` | 🚫 *violates check constraint* |
| Borrar `Entradas` (tiene platillos) | 🚫 *"No se puede borrar una categoría…"* |

`get_advisors(security)` → **0 errores** tras la cuarta migración. Los `WARN` que quedan
son los preexistentes de exposición por GraphQL, comunes a todo el esquema.

## Documentación escrita

- [[Plan de Fase 2 - Menu]] — el paraguas: por qué la fase va partida en **2a** (CRUD +
  Storage), **2b** (Room + outbox) y **2c** (P-017/P-011), y **cuál es el costo aceptado**
  de escribir 2a contra la red pese a lo que advierte **P-014**.
- [[Plan Fase 2a - CRUD de Platillos y Categorias]] — el plan ejecutable. Incluye cómo se
  trabaja con la bóveda de Obsidian, el contrato HTTP exacto, los 7 entregables, y una
  sección de trampas concretas.
- [[Esquema de Base de Datos]], [[Roadmap de Fases]], [[Conocimiento Principal]] y
  [[Deuda Técnica - Pendientes]] actualizados.

## Deuda registrada

**P-023** — nadie limpia los archivos huérfanos del bucket `platillos`. Sale del propio
diseño: se sube la foto antes de tocar la fila, y cada reemplazo usa una ruta nueva. Se
pide compensar borrando, pero esa compensación también puede fallar. Requiere acceso a
Supabase, así que no es trabajo del agente de código.

## Lo que NO se hizo

- **Cero código Android.** La rama `feat/fase2-menu` tiene, por ahora, solo documentación.
- **Room, outbox y `SyncWorker`** — Fase 2b, explícitamente fuera del alcance de 2a.
- Los 3 pendientes de Fase 1 que solo puede cerrar el usuario (probar en un dispositivo,
  verificar P-004 en un teléfono físico, y la política de contraseñas del dashboard —
  **S-2**) siguen abiertos. `feat/fase1-login` **no se mergea a `master`** todavía.

---

## Relaciones

- [[Plan de Fase 2 - Menu]]
- [[Plan Fase 2a - CRUD de Platillos y Categorias]]
- [[Esquema de Base de Datos]]
- [[Plan Fase 1d - Modulo Empleados Funcional]] — el patrón que 2a replica
- [[Deuda Técnica - Pendientes]] — P-014, P-017, P-023
- [[Offline-First con Room y Outbox]]
- [[Roadmap de Fases]]
- [[Sesión 2026-07-31 - Remediación P-005 P-012 P-013 P-020 y arranque Fase 2]]
