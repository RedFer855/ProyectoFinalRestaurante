---
title: Plan Fase 1d — Módulo Empleados funcional
tags:
  - restaurante
  - plan
  - fase1d
  - empleados
  - edge-functions
  - rls
date: 2026-07-31
lifecycle: verified
---

# Plan Fase 1d — Módulo Empleados funcional

> [!success] Completada el 2026-07-31
> Los 5 entregables terminados. Empleados pasó de maqueta a módulo real: crear un
> empleado le crea su cuenta de acceso, y las reglas de quién puede tocar a quién se
> sostienen **en el servidor**. 47 tests en verde (eran 33).

---

## La restricción que definió la arquitectura

Crear un usuario en Supabase Auth exige la llave `service_role`. La documentación de
GoTrue es explícita: *"anon/publishable keys can [never] call them"*. Esa llave **no
puede vivir en el APK**. Por eso el alta pasa por una **Edge Function** — la Propuesta D
que ya estaba anotada en [[Plan de Conexión con Supabase]].

**Todo lo demás no la necesita:** listar, editar, cambiar rol y activar/desactivar van
directo por PostgREST con RLS. Mantener chica la superficie de la función fue
deliberado: es el único código del proyecto que corre con privilegios elevados.

---

## Decisiones tomadas

| Tema | Decisión |
|---|---|
| Contraseña del empleado nuevo | Temporal, puesta por el admin, con `email_confirm: true` — entra de inmediato |
| "Eliminar" empleado | **Solo desactivar.** Borrar rompería la trazabilidad de `pedido.id_usuario` |
| Auto-edición del admin | Puede corregir sus datos, **no** cambiarse el rol ni desactivarse |

---

## E1 — Servidor: vista y triggers

| Objeto | Qué hace |
|---|---|
| `vista_empleados` | Une empleados + usuarios + perfiles. `security_invoker = on` para que respete la RLS de quien consulta |
| `proteger_admins()` | `BEFORE UPDATE` en `perfiles`: bloquea editar a otro admin y auto-cambiarse el rol |
| `sincronizar_rol_usuario()` | `AFTER UPDATE`: propaga `perfiles.rol` → `usuarios.id_rol` |

> [!tip] Por qué un trigger y no una policy RLS
> Una policy **no puede comparar la fila vieja contra la nueva**: `USING` solo ve la
> vieja, `WITH CHECK` solo la nueva, y no hay forma de cruzarlas. Un `BEFORE UPDATE` ve
> `OLD` y `NEW` a la vez — que es exactamente lo que estas dos reglas necesitan.
>
> Regla general que deja: **los invariantes que comparan estado anterior contra nuevo
> van en triggers; la RLS decide quién ve o toca qué fila.**

**Sincronización del rol:** el rol vive duplicado en `perfiles.rol` (texto, lo lee el
login) y `usuarios.id_rol` (FK, el modelo de negocio) — deuda **P-021**, resuelta a
medias. En vez de arrastrar la duplicación al código Android, `perfiles.rol` quedó como
**única vía de escritura** y el trigger propaga. La app nunca escribe `usuarios.id_rol`.

**Válvula de escape deliberada:** si no hay sesión (`auth.uid()` es `null`), el trigger
no aplica. Permite reparar la base desde el SQL Editor si algo sale mal.

---

## E2 — Edge Function `crear-empleado`

`supabase/functions/crear-empleado/index.ts`, desplegada con `verify_jwt: true`.

1. **Verifica quién llama** leyendo su rol de la base — no alcanza con que la app no
   muestre el botón.
2. Valida el cuerpo, incluida la contraseña con la **misma política** que
   `ValidadorContrasenia` del cliente.
3. Crea la cuenta con `POST /auth/v1/admin/users` y `email_confirm: true`.
4. Inserta `empleados` → `usuarios` → `perfiles`.
5. **Si algún insert falla, borra la cuenta de Auth recién creada.**

Usa `fetch` plano en vez del SDK, por coherencia con [[ADR-002 - Supabase Auth via REST directo (Retrofit) en vez del SDK Kotlin]].
**Cero `console.log`** — imposible que filtre la contraseña a los logs.

`SUPABASE_SERVICE_ROLE_KEY` la inyecta Supabase sola: no hubo que configurar ningún
secreto a mano.

---

## E3 — Dominio y capa de datos

```
domain/model/Empleado.java, NuevoEmpleado.java
domain/ReglasEmpleado.java            ← espejo del trigger, para la UI
domain/repository/EmpleadoRepository.java
data/remote/SupabaseEmpleadoApi.java  + 6 DTOs
data/repository/SupabaseEmpleadoRepository.java
core/SupabaseClient#getEmpleadoApi()
```

Decisiones:
- **El token entra por un `Supplier<String>` inyectado**, no leyendo `SesionActual`
  directo: `data` no depende de dónde vive la sesión y el repositorio es testeable.
- **`ReglasEmpleado` es más estricto que el servidor en un punto:** el trigger permite
  que un admin se desactive a sí mismo; la app no lo ofrece. Que la UI sea más estricta
  que el servidor es seguro — al revés sería el problema.
- **El filtro del `PATCH` es parámetro obligatorio** en la interfaz Retrofit: un `PATCH`
  sin filtro en PostgREST actualiza **todas** las filas.
- **`ActualizarPerfilDto` usa factories** (`soloRol`, `soloEstado`, `soloNombre`): Gson
  omite los nulos, así cambiar el rol no pisa el estado.

**Excepción acotada a la regla de mensajes de error:** normalmente nunca se muestra un
mensaje del servidor, pero los de **la Edge Function** y **el trigger** sí — los
escribimos nosotros en lenguaje humano y no filtran nada interno. Verificado que
PostgREST devuelve el texto del trigger en `{"message": "..."}`, que es lo que parsea
`mensajeDeError()`.

---

## E4 — ViewModel

`EstadoEmpleados` con los **cuatro** estados (cargando · datos · vacío · error);
`isVacio()` se **deriva** en vez de guardarse como bandera, que podría contradecir a la
lista. `ExecutorService` inyectado (no se replica **P-005**). El filtro de búsqueda vive
en el ViewModel para sobrevivir a la rotación.

Tras cada operación exitosa **se relee del servidor** en vez de retocar la lista en
memoria: así lo que se ve es lo que la base realmente aceptó, incluidos los efectos de
los triggers.

---

## E5 — UI

- `FormularioEmpleadoDialog` — un mismo diálogo para alta y edición. Al crear muestra
  la contraseña temporal con los requisitos marcándose en vivo (reusa
  `ValidadorContrasenia` de la Fase 1b); al editar no se toca.
- El **rol se cambia desde su propia opción** del ⋮, no desde el formulario: tiene
  reglas más estrictas que editar datos.
- `EmpleadoAdapter` filtra cada opción con `ReglasEmpleado`; si no queda ninguna, el
  botón ⋮ **desaparece** en vez de abrir un menú vacío.
- Se quitó "Eliminar" del menú.
- Se eliminaron `DatosMaqueta.Empleado` y `DatosMaqueta.empleados()` — el resto de
  módulos sigue usando el archivo.

---

## Verificación

**Servidor** (simulando cada rol en transacciones revertidas):

| Caso | Resultado |
|---|---|
| Admin edita a **otro** admin | 🚫 *"No se puede modificar a otro administrador"* |
| Admin cambia **su propio** rol | 🚫 *"No podés cambiar tu propio rol"* |
| Admin edita **sus datos** | ✅ Permitido |
| Vista como mesero | 0 filas |
| Sincronización de rol | `perfiles.rol` → `usuarios.id_rol` coincide |

**Edge Function** (con tokens reales):

| Caso | Resultado |
|---|---|
| Contraseña débil / rol inválido | 🚫 400 |
| Alta válida | ✅ 201 — apodo `mzelaya` derivado solo |
| La empleada nueva **inicia sesión** | ✅ Entra con su clave temporal |
| Ella intenta crear un empleado | 🚫 **403** |
| Ella lee `empleados` | ✅ `[]` — RLS |
| Identidad duplicada | 🚫 400, y **la cuenta de Auth quedó borrada** |

**Estado final:** `auth.users` 3 · `empleados` 3 · `usuarios` 3 · `perfiles` 3 — sin
huérfanos. `get_advisors(security)` → 0 errores. Build debug y 47 tests en verde.

⬜ **Falta:** probar el flujo desde el emulador (no había uno conectado al cerrar).

---

## Cuenta de prueba creada

Se creó un empleado con rol `mesero` (`marta.zelaya@restaurante.hn`) para poder
demostrar que el menú lateral le muestra 5 módulos y no 7, y para el `curl` que prueba
el bloqueo por RLS del guion de la [[Plan Fase 1c - Maqueta Visual por Roles]].

> [!danger] Las contraseñas no se escriben en la bóveda
> Esta bóveda **se versiona en git**, así que anotar una contraseña acá equivale a
> publicarla. Las credenciales de prueba se consultan o se resetean desde el dashboard
> de Supabase (Authentication → Users), nunca desde un archivo del repo.
>
> Ver [[Seguridad y Privacidad Android]], sección 1.

> [!warning] La contraseña de Kelvin no es la que se asumió
> Se descubrió al intentar usar su cuenta para pruebas: el login devuelve
> `invalid_credentials`. Si se necesita, hay que resetearla desde el dashboard.

---

## Pendientes que deja

| Ítem | Detalle |
|---|---|
| Forzar cambio de contraseña en el primer ingreso | Hoy la temporal que puso el admin sirve indefinidamente. **Bloqueado** (2026-07-31): exige una columna nueva en `perfiles`/`usuarios` y tocar la Edge Function `crear-empleado` — sin acceso al conector de Supabase en este entorno. Ver [[Deuda Técnica - Pendientes]]. |
| **S-2** del Plan Fase 1b | La política de contraseñas del servidor sigue sin configurarse: hoy Supabase aceptaría una clave débil aunque la Edge Function la valide. **Bloqueado** (2026-07-31): es un ajuste del dashboard de Supabase (Authentication), no de código. |
| **P-021** | El rol sigue duplicado en dos tablas; el trigger lo mantiene consistente, pero la resolución de fondo (una sola tabla) sigue abierta — decisión de arquitectura, no se resuelve al pasar. |
| ~~Tests del repositorio~~ ✅ | Resuelto 2026-07-31: `SupabaseEmpleadoRepositoryTest` (6 casos, fakes manuales sin Mockito) — mismo patrón que cerró **P-020**. Ver [[Deuda Técnica - Pendientes]]. |

---

## Relaciones

- [[Plan Fase 1c - Maqueta Visual por Roles]]
- [[Esquema de Base de Datos]]
- [[Seguridad y Privacidad Android]]
- [[Plan de Conexión con Supabase]] — Propuesta D, ahora implementada
- [[Deuda Técnica - Pendientes]]
- [[ADR-002 - Supabase Auth via REST directo (Retrofit) en vez del SDK Kotlin]]
