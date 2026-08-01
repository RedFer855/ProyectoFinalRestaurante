---
title: Módulo Empleados
tags:
  - restaurante
  - modulo
  - empleados
  - offline-first
date: 2026-08-01
lifecycle: verified
---

# Módulo Empleados

> [!info] Estado
> 🟢 **Funcional y local-first** (2026-08-01). CRUD real contra Supabase con Room + outbox;
> el alta pasa por una Edge Function. Nació en [[Plan Fase 1d - Modulo Empleados Funcional]]
> y se migró a offline-first al cerrar **P-014**.

---

## Qué hace

| Historia | Offline | Quién |
|---|---|---|
| Ver la lista de empleados | ✅ | solo admin |
| Buscar por nombre, identidad, correo o rol | ✅ | solo admin |
| Editar datos personales | ✅ se encola | solo admin |
| Cambiar el rol | ✅ se encola | solo admin |
| Activar / desactivar | ✅ se encola | solo admin |
| **Dar de alta un empleado** | ❌ **exige conexión** | solo admin |

Para los demás roles el módulo ni aparece en el menú lateral, y del lado del servidor la
RLS bloquea las tablas aunque alguien modifique el APK.

---

## Por qué el alta exige conexión

Es la decisión que define la forma del módulo, y no es una limitación que quedó: es una
elegida.

Crear un empleado llama a la Edge Function `crear-empleado`, que le da de alta su cuenta en
Supabase Auth con una **contraseña temporal**. Encolar eso en el outbox tendría dos costos
inaceptables:

1. Habría que **guardar la contraseña en el dispositivo** hasta que aparezca la red. El
   proyecto exige cifrar hasta el token de sesión (**P-009**); dejar una credencial en claro
   en SQLite es peor.
2. Es un **`POST` no idempotente que crea cuentas**. [[Offline-First con Room y Outbox]] lo
   prohíbe sin *idempotency key*: un reintento crea un empleado duplicado con una cuenta de
   acceso duplicada.

La app lo dice con todas las letras — *"Para crear un empleado necesitás conexión: hay que
darle de alta su cuenta de acceso. El resto de los cambios sí se guardan sin internet."* —
en vez de fingir que se guardó.

---

## Arquitectura

```
ui/empleados/     EmpleadosFragment · EmpleadoAdapter · EmpleadosViewModel
                  EstadoEmpleados · EmpleadosViewModelFactory · FormularioEmpleadoDialog
domain/           model/Empleado · model/NuevoEmpleado · ReglasEmpleado
                  repository/EmpleadoRepository  ← LiveData para leer
data/local/       entity/EmpleadoEntity · dao/EmpleadoDao · mapper/EmpleadoMapper
data/repository/  EmpleadoRepositorioLocal (local-first) · EmpleadoRemoto (red)
data/sync/        SincronizadorEmpleados
```

### Tres diferencias con el Menú, y por qué

**La PK local es `id_empleado`, no un `id_local` propio.** Un platillo puede existir
localmente antes de que el servidor le dé un id, así que necesita identidad local y un
`id_servidor` nulable. Un empleado no: como el alta exige conexión, **toda fila local ya
vino del servidor**. Inventar un `id_local` acá sería una indirección que no resuelve nada.

**No hay `CREAR_EMPLEADO` en el outbox.** Consecuencia de lo mismo: desaparece el caso "fila
sin id de servidor" y el plegado de ediciones sobre un CREAR pendiente, que en el Menú es de
lo más delicado.

**El outbox está particionado por módulo.** La tabla `operaciones_pendientes` es una sola,
compartida. Sin la columna `modulo` habría dos formas de romperse, y las dos son silenciosas:
el `default` del sincronizador del Menú **descartaría** las operaciones de Empleados como
"tipo desconocido", y `deFila(idLocal)` confundiría el platillo 3 con el empleado 3.

> [!tip] Por qué una cola compartida y no una por módulo
> Dos colas habrían pedido dos workers, y la regla 3 de [[Offline-First con Room y Outbox]]
> es explícita: **un `SyncWorker` único**, para que nunca haya dos drenando en paralelo. La
> partición por columna cuesta un `WHERE` y respeta la regla.

---

## El servidor

`vista_empleados` gana `actualizado_en`, calculado como
`greatest(empleados.actualizado_en, perfiles.actualizado_en)`.

> [!warning] Por qué el máximo de dos tablas y no una
> Los datos personales viven en `empleados`, pero **el rol y el estado viven en `perfiles`**.
> Si la marca saliera solo de `empleados`, cambiar el rol de alguien no la movería y el sync
> delta **se perdería ese cambio para siempre**. `usuarios` no necesita marca: su único campo
> mutable (`id_rol`) lo escribe el trigger `sincronizar_rol_usuario()` a partir de
> `perfiles.rol`, que sí mueve la suya.

Migración `empleados_actualizado_en_para_sync_delta`: columna + trigger en las dos tablas,
índices, y la vista recreada conservando `security_invoker = on`.

---

## Conflictos

Last-write-wins, igual que el Menú: el servidor gana si la fila local está sincronizada, o
si —estando pendiente/error— trae una marca más nueva. Cuando pisa un cambio local no subido,
la pantalla avisa *"Un cambio de empleado se perdió: el servidor tenía una versión más
reciente."* No se lo traga en silencio.

El caso permanente típico **no es un bug**: es el trigger `proteger_admins()` rechazando que
un admin toque a otro. Su mensaje está escrito para que lo lea una persona, así que se
propaga tal cual hasta el banner.

---

## Deuda que deja

| Ítem | Qué falta |
|---|---|
| 🟢 **P-009** | El token no se persiste ni se refresca: al reabrir la app hay que loguearse |
| 🟢 **P-025** | `actualizado_en` usa `now()` (inicio de transacción) — ventana en el delta |
| 🟢 **P-002** | DI manual por Factory |
| 🟢 **P-011** | IDs de vista en `snake_case` — bloqueado por **P-017** |

⬜ **Sin probar en dispositivo:** el flujo offline completo (editar en avión, ver "Sin subir",
recuperar red, ver que sube) no se pudo verificar desde este entorno.

---

## Relaciones

- [[Plan Fase 1d - Modulo Empleados Funcional]] — de dónde salió el módulo
- [[Sesión 2026-08-01 - Empleados offline-first y cierre de P-014]]
- [[Módulo Menú]] — el otro módulo local-first
- [[Offline-First con Room y Outbox]] — las 8 reglas que sigue
- [[Plan Fase 2b - Offline-First con Room y Outbox]] — la infraestructura que reusa
- [[Esquema de Base de Datos]] · [[Arquitectura Actual]] · [[Deuda Técnica - Pendientes]]
- [[Seguridad y Privacidad Android]] — por qué no se guarda la contraseña temporal
