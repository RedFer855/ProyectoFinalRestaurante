---
title: Módulo Mesas
tags:
  - restaurante
  - modulo
  - mesas
  - offline-first
date: 2026-08-01
lifecycle: verified
---

# Módulo Mesas

> [!success] Estado
> 🟢 **Funcional y local-first** (2026-08-01). Parte B (Android: Room + outbox + UI, 73
> tests) y Parte A (servidor: catálogo, RPC, RLS) ejecutadas y verificadas las dos en la
> misma sesión — algo infrecuente en este proyecto, que normalmente reparte el trabajo entre
> agentes con y sin acceso a Supabase. Ver [[ADR-007 - Estados operativos en catálogos propios, separados de estado_general]]
> y [[Esquema de Base de Datos]] para el DDL real.

---

## Qué hace

| Historia | Offline | Quién |
|---|---|---|
| Ver el salón: mesas con número, capacidad y estado | ✅ | admin, mesero |
| Cambiar el estado (libre ↔ ocupada ↔ reservada) | ✅ se encola | admin, **mesero** |
| Crear una mesa | ✅ se encola | solo admin |
| Editar una mesa | ✅ se encola | solo admin |
| Dar de baja / reactivar (nunca se borra) | ✅ se encola | solo admin |
| Filtrar por estado y buscar por número/ubicación | ✅ (en memoria) | los dos |

Cocina **no** tiene el módulo: `Permisos.java` no le da ninguna entrada para `Modulo.MESAS`
(ni `VER`), así que ni aparece en su menú lateral. Del lado del servidor, la policy
`"mesa lectura admin y mesero"` coincide exactamente — cocina no puede leer `vista_mesas`
aunque modificara el APK.

---

## Arquitectura

```
ui/mesas/         MesasFragment · MesaAdapter · MesasViewModel · EstadoMesas
                   MesasViewModelFactory · FormularioMesaDialog · EstadoMesaUi
domain/           model/Mesa · model/NuevaMesa · model/EstadoMesa
                   ValidadorMesa · ReglasMesa · repository/MesaRepository
data/local/       entity/MesaEntity · entity/EstadoMesaEntity
                   dao/MesaDao · dao/EstadoMesaDao · mapper/MesaMapper
data/repository/  MesaRepositorioLocal (local-first) · MesaRemoto (red)
data/sync/        SincronizadorMesas
```

Mismo patrón que [[Módulo Menú]]: identidad `idLocal`/`idServidor`, `CREAR_MESA` en el outbox
(a diferencia de Empleados, Mesas **sí** nace offline), y last-write-wins en el delta.

### La mesa tiene dos estados ortogonales (ADR-007)

`Mesa.activo` (baja lógica, `estado_general`) y `Mesa.estado` (operativo, catálogo
`estado_mesa`) son independientes. `EstadoMesa.porId(int)` devuelve `null` ante un id que el
cliente no conoce — nunca lo redondea a `LIBRE` por defecto, porque eso le mentiría al mesero
sobre una mesa que en realidad está, por ejemplo, en mantenimiento.

### `numero_mesa` y `ubicacion` no existían — se agregaron, no se descartaron

El DDL real de `mesa` (verificado 2026-08-01) solo tenía `id_mesa`, `capacidad`,
`id_estado`. El plan asumía también `numero_mesa` y `ubicacion`. Se agregaron como columnas
reales en vez de sacarlas del contrato — razonamiento completo en el addendum de
[[ADR-007 - Estados operativos en catálogos propios, separados de estado_general]]. Resultado
práctico: **cero cambios** en `MesaDto`/`CrearMesaDto`/`ActualizarMesaDto`, que ya estaban
escritos exactamente contra ese contrato.

### El catálogo `estado_mesa` se siembra local, no se sincroniza

El plan pedía "cachear el catálogo en Room" (§5.4), pero el contrato HTTP de la Parte B (§3)
no define un endpoint para listarlo — solo `vista_mesas`. `MesaRepositorioLocal` siembra
`estados_mesa` con los tres valores fijos de `EstadoMesa` (que coinciden con los que la Parte
A insertó: `1=Libre, 2=Ocupada, 3=Reservada`) en vez de agregar una llamada de red que el
plan no pidió. Si el servidor llega a exponer un catálogo dinámico, este sembrado se
reemplaza por un delta real sin tocar el resto del módulo.

### Crear una mesa y cambiarle el estado antes de que suba

`CrearMesaDto` no lleva `id_estado_mesa` (el servidor siempre crea en Libre). Si el mesero
cambia el estado de una mesa que todavía no se subió, `SincronizadorMesas.crearMesa()` lo
detecta y hace un segundo viaje (`cambiar_estado_mesa`) inmediatamente después del `POST`, en
vez de perder el cambio en silencio. La baja/alta lógica **no** tiene esta compensación: se
consideró un caso demasiado raro para justificar la complejidad extra.

---

## El servidor — Parte A ejecutada (2026-08-01)

Todo lo de [[Plan Fase 2c - CRUD de Mesas]] §2 está aplicado sobre `mxarlisuueovxvttytcm`:
catálogo `estado_mesa` sembrado, `mesa.numero_mesa`/`ubicacion`/`id_estado_mesa`/
`actualizado_en`, triggers `trg_mesa_actualizado_en` y `trg_mesa_no_borrar`, el RPC
`cambiar_estado_mesa()`, la vista `vista_mesas` y RLS ajustada.

**Se encontró y se corrigió una policy preexistente que contradecía el diseño:**
`"mesa cambio de estado mesero"` le daba a mesero `UPDATE` directo sobre toda la fila —
exactamente el agujero de seguridad que ADR-007 existe para cerrar (un mesero con esa policy
podría cambiar capacidad y número desde un APK modificado, no solo el estado). Se sacó; el
mesero ahora solo escribe a través del RPC.

**`get_advisors(security)` encontró un gap real tras la primera pasada de la migración:**
`revoke execute ... from anon` en el RPC no alcanza, porque Postgres le da `EXECUTE` a
`PUBLIC` (todos los roles) por default al crear una función, y ese permiso sobrevive aunque
se revoque de un rol puntual. Hubo que revocar de `PUBLIC` explícitamente — y de paso se
cerró el mismo gap en dos funciones de trigger preexistentes del Menú
(`impedir_borrado_platillo`, `impedir_borrado_categoria_con_platillos`) que la bóveda ya
documentaba como regla pero no se había aplicado. `get_advisors` final: 0 errores.

### Verificación (§2.7 del plan, 9 casos)

Corrida dentro de una transacción revertida, simulando cada rol con los usuarios reales de
`perfiles` (admin, mesero, cocina). Las 9 pasaron:

| Caso | Resultado |
|---|---|
| Mesero lee `vista_mesas` | ✅ 4 filas |
| Mesero hace `UPDATE mesa SET capacidad = 99` | ✅ 0 filas afectadas (RLS) |
| Mesero llama `cambiar_estado_mesa(1, 2)` | ✅ la mesa quedó Ocupada |
| Cocina llama `cambiar_estado_mesa(1, 2)` | ✅ rechazado: *"No tenés permiso…"* |
| `cambiar_estado_mesa(1, 99)` | ✅ rechazado: *"Ese estado de mesa no existe."* |
| `cambiar_estado_mesa` sobre mesa dada de baja | ✅ rechazado: *"La mesa no existe o está dada de baja."* |
| Admin borra una mesa | ✅ rechazado por el trigger |
| Admin edita → `actualizado_en` avanza solo | ✅ verificado con `pg_sleep` |
| Insertar dos mesas con el mismo número | ✅ rechazado por el único |

Las 4 mesas y el catálogo `estado_mesa` sembrado quedaron intactos tras revertir la
transacción de prueba — verificado con un `count(*)` posterior.

---

## Conflictos

Last-write-wins, igual que Menú y Empleados: el servidor gana si la fila local está
sincronizada, o si —pendiente/error— trae una marca más nueva. `SincronizadorMesas` avisa
*"Un cambio de mesa se perdió: el servidor tenía una versión más reciente."*

---

## Deuda que deja

| Ítem | Qué falta |
|---|---|
| 🟢 **P-001** | `mensajeDeError(...)` copiado por tercera vez en `MesaRemoto` — candidato a `BaseRepository` |

⬜ **Sin probar en un dispositivo real:** los 73 tests del módulo y las 9 pruebas de
aceptación de la Parte A verifican la lógica; falta que un usuario abra la app en un
teléfono con dos sesiones (admin y mesero) y confirme que el flujo completo —crear mesa,
cambiar estado, offline y recuperar red— se siente bien. Es lo único que el plan deja fuera
del alcance del agente.

---

## Relaciones

- [[Plan Fase 2c - CRUD de Mesas]] — de dónde salió el módulo
- [[ADR-007 - Estados operativos en catálogos propios, separados de estado_general]]
- [[Sesión 2026-08-01 - Fase 2c y 2d completas, Parte B — Mesas y Clientes]]
- [[Módulo Menú]] — el patrón replicado
- [[Módulo Clientes]] — el módulo hermano, mismo estado (Parte A y B completas)
- [[Offline-First con Room y Outbox]] · [[Plan Fase 2b - Offline-First con Room y Outbox]]
- [[Esquema de Base de Datos]] · [[Arquitectura Actual]] · [[Deuda Técnica - Pendientes]]
