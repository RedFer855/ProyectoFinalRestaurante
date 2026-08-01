---
title: Protocolo de Ejecución de un Plan
tags:
  - restaurante
  - proceso
  - agentes
  - calidad
date: 2026-08-01
lifecycle: verified
---

# Protocolo de Ejecución de un Plan

> [!danger] Si te asignaron un plan de este proyecto, esto se lee **antes** de escribir una línea
> No importa qué agente seas (Claude, Codex, opencode, Antigravity, Copilot, Cursor…) ni qué
> plan te toque. Todos los planes `Plan Fase N*` apuntan acá en vez de repetir estas reglas,
> así que **saltearte esta nota es saltearte el contrato de ingeniería del proyecto entero**.

Esta nota existe por la regla anti-duplicados del [[contexto/AGENTS|protocolo de la bóveda]]:
antes vivía copiada dentro de cada plan y se desincronizaba. Si algo de acá cambia, cambia
para todos los planes a la vez.

---

## 1. ¿Tenés acceso a Supabase? — la primera pregunta que tenés que contestarte

Cada plan de este proyecto está partido en **dos partes independientes**:

| Parte | Qué es | Quién la ejecuta |
|---|---|---|
| **A — Servidor** | Migraciones SQL, funciones, triggers, policies RLS, buckets de Storage | **Solo** un agente con el conector de Supabase o acceso al dashboard |
| **B — Código Android** | `domain`, `data`, ViewModels, UI, pruebas | Cualquier agente |

**Regla dura:**

> [!warning] Sin acceso a Supabase → hacés **solo la Parte B**, y lo decís
> No corras SQL. No crees migraciones. No toques `supabase/`. **No inventes el esquema.**
> Si te falta una columna, una vista o una función que la Parte B necesita, **parás y lo
> escribís en tu nota de sesión** como bloqueo: lo resuelve el agente que sí tiene acceso.
>
> Improvisar el esquema desde el cliente es exactamente lo que esta división busca evitar.
> Ya pasó una vez que el diagnóstico correcto fue "no era Supabase" — ver
> [[Sesión 2026-08-01 - Rediseño de la tarjeta de platillo y filtro que escondía lo guardado]].

Al revés también vale: si **sí** tenés acceso, la Parte A se ejecuta y se **verifica** antes
de que nadie escriba código contra ella. El patrón que funcionó en la Fase 2a fue: servidor
listo y verificado primero, código después, dos sesiones distintas.

### Si ejecutás la Parte A

1. **Verificá el estado real antes de migrar.** La bóveda documenta lo que se aplicó, pero
   la fuente de verdad es la base. Listá las columnas, constraints y policies que ya existen
   antes de escribir el `ALTER`.
2. **Migraciones idempotentes** (`IF NOT EXISTS`, `CREATE OR REPLACE`), con nombre
   descriptivo en `snake_case`.
3. **Verificá con la transacción revertida**: ejercé cada caso simulando cada rol dentro de
   un `BEGIN … ROLLBACK`, y confirmá después que los datos quedaron intactos.
4. **Corré `get_advisors(security)`** al final. Tiene que dar **0 errores**.
5. **Toda función de trigger creada en `public` queda expuesta como RPC por PostgREST.**
   Revocale `EXECUTE` a `anon` y `authenticated` en la misma migración. Esta regla salió de
   un hallazgo real — ver [[Sesión 2026-07-31 - Plan técnico de Fase 2a (CRUD de Menú) y preparación de Supabase]].
6. **Documentá las columnas reales** en [[Esquema de Base de Datos]]. Si el plan asumía algo
   distinto de lo que había, corregí el plan.

---

## 2. Orden de lectura obligatorio

1. [`AGENTS.md`](../../AGENTS.md) de la raíz — reglas de oro y build.
2. [[Estándar de Ingeniería Android]] — el contrato: stack permitido y prohibiciones.
3. [[Arquitectura Actual]] — el estado **real** del sistema, distinto del ideal.
4. [[Deuda Técnica - Pendientes]] — la brecha entre ambos, en ítems `P-NNN`.
5. [[contexto/CLAUDE|CLAUDE]] — convenciones de código detalladas.
6. La nota del módulo que vas a tocar → [[Módulo Menú]], [[Módulo Login]]…
7. [[contexto/AGENTS|AGENTS de la bóveda]] — cómo clasificar y guardar lo que hagas.

**El módulo de referencia es [[Módulo Menú]]** (Fase 2a) y, antes de él, Empleados
([[Plan Fase 1d - Modulo Empleados Funcional]]). El [[Módulo Login]] se escribió **antes**
de adoptar el estándar: no copies sus patrones sin leer su lista de deuda.

---

## 3. Las reglas de oro del código (no negociables)

Completas en [`AGENTS.md`](../../AGENTS.md). Las que más muerden:

1. **`domain` nunca referencia `data`**, y no importa nada de `android.*`, Retrofit, Room ni
   Glide. La dependencia va `ui → domain ← data → core`. Única excepción admitida:
   `androidx.annotation` (`@Nullable`/`@NonNull`), que es un JAR de anotaciones sin runtime.
2. `ui` habla **solo** con interfaces de `domain`. Nunca con un `ApiService`, un DAO ni un DTO.
3. **Un único objeto de estado inmutable por pantalla**, con los cuatro estados reales
   (cargando · con datos · vacío · error) y `isVacio()` **derivado**, nunca una bandera
   suelta que pueda contradecir a la lista.
4. **Todo I/O fuera del hilo principal, con `Executor` inyectado por constructor.** Crearlo
   dentro del ViewModel es la deuda **P-005**, ya cerrada: no la reintroduzcas.
5. El `ViewModel` **nunca** recibe `Context`, `Activity`, `View` ni `Resources`.
6. Todo método de repositorio devuelve `Result`/`Result<T>`. Nunca una excepción cruda a la
   UI, nunca `catch (Exception e) {}` vacío, nunca `printStackTrace()`.
7. **Cero strings, colores o dimens hardcodeados.** Todo en recursos.
8. **Todo código nuevo trae su prueba.** Sin prueba, la entrega está incompleta. El proyecto
   usa **fakes manuales**, no Mockito — ya existe `app/src/test/java/.../data/FakeCall.java`.
9. Cero `// TODO`, cero placeholders, cero APIs de la [[Lista Negra de APIs Android]].
10. Toda dependencia nueva se declara en `gradle/libs.versions.toml` **y** en
    `app/build.gradle.kts`, en la misma entrega. **Nunca inventes un número de versión:**
    verificalo en Maven Central o en Google Maven, o documentalo como supuesto.

### Lo que la seguridad **no** es

`Permisos` y `VistaPorPermiso` mejoran la experiencia — no mostrar un botón que va a
fallar — pero **un APK se modifica**. Quien impide que un mesero cambie un precio es la
policy RLS de Postgres. Programá asumiendo que el servidor va a decir que no, y mostrá bien
ese "no".

---

## 4. Verificación — cuándo una entrega está terminada

```bash
./gradlew testDebugUnitTest assembleDebug
```

Ambos tienen que terminar en **BUILD SUCCESSFUL**, y el número de tests tiene que quedar
**más alto que como lo encontraste**, no igual. Al 2026-08-01 hay **127 tests** en verde.

Al final imprimís el [[Gate de Autoverificación]] **ítem por ítem**, con honestidad. Un ✅
sin verificar es peor que no tener el gate. Cualquier ❌ se corrige antes de entregar, o se
convierte en un ítem `P-NNN` con justificación explícita.

> [!note] Lo que el agente **no** puede verificar
> No hay dispositivo ni `adb` en el entorno del agente. La prueba funcional en emulador o
> teléfono **es del usuario**, y se dice explícitamente en la entrega en vez de darla por
> hecha. Un layout que compila no es un layout que se ve bien.

---

## 5. Al terminar, documentás

No es opcional. Como mínimo:

- **Nota de sesión** en `contexto/70 - Bitácora de Cambios/AAAA-MM/`, con el formato
  `Sesión AAAA-MM-DD - Título descriptivo.md` y los campos `branch:` y `autor_cambios:`.
  Usá `contexto/_templates/plantilla-sesion.md`.
- **Actualizar** [[Arquitectura Actual]] y el estado en [[Conocimiento Principal]].
- **Nota del módulo** (`Módulo X.md` en `40 - Proyecto Restaurante/`) si creaste uno, con
  el formato de [[Módulo Menú]].
- **Deuda nueva** como ítem `P-NNN` dentro de [[Deuda Técnica - Pendientes]], más su fila en
  la tabla de historial. **Uno por agente**, no reserves rangos. El último asignado es
  **P-024**.
- **Marcar el plan** que ejecutaste: checkboxes, `lifecycle: verified` y un callout de
  cierre apuntando a tu nota de sesión.

Reglas de la bóveda que se aplican siempre: frontmatter YAML con `title`, `tags` y `date`
**absoluta**; toda nota cierra con `## Relaciones`; enlaces `[[wikilink]]`; **antes de crear
una nota buscá si ya existe y actualizala** (varios agentes trabajan sin conocerse, duplicar
es el riesgo #1); y **nunca escribas una contraseña ni una llave en la bóveda** — se versiona
en git, anotarla ahí equivale a publicarla.

---

## 6. Reportá con honestidad

- Si algo quedó a medias, se dice. Si un test falla, se muestra la salida.
- Si te desviaste del plan, lo escribís **con el porqué** en una tabla de desvíos. Un desvío
  justificado y documentado es una decisión; uno silencioso es una trampa para el próximo.
- Si el plan se contradice a sí mismo o choca con la realidad del repo, **decilo y corregí
  el plan** — no lo ejecutes al pie de la letra sabiendo que está mal.

---

## Relaciones

- [[Estándar de Ingeniería Android]] — el contrato de stack y prohibiciones
- [[Gate de Autoverificación]] — el checklist que se imprime al final
- [[Lista Negra de APIs Android]] — qué está prohibido y por qué
- [[Convenciones Java]] — nomenclatura de clases, recursos e IDs
- [[contexto/AGENTS|AGENTS de la bóveda]] — taxonomía, frontmatter, anti-duplicados
- [[Arquitectura Actual]] · [[Deuda Técnica - Pendientes]] · [[Roadmap de Fases]]
- [[Módulo Menú]] — el módulo de referencia vigente
- [[Plan Fase 2b - Offline-First con Room y Outbox]]
- [[Plan Fase 2c - CRUD de Mesas]]
- [[Plan Fase 2d - CRUD de Clientes]]
