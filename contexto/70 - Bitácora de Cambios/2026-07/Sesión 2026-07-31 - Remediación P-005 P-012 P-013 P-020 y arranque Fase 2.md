---
title: "Sesión 2026-07-31 — Remediación P-005/P-012/P-013/P-020 y arranque Fase 2"
tags:
  - sesion
  - restaurante
  - fase0
  - fase2
date: 2026-07-31
branch: feat/fase1-login
autor_cambios: Claude Code
---

# Sesión 2026-07-31 — Remediación P-005/P-012/P-013/P-020 y arranque Fase 2

> [!success] Resultado
> Se cerraron 4 ítems de deuda técnica resolubles sin acceso a Supabase ni a un dispositivo (P-005, P-012, P-013, P-020) más la deuda de tests que dejó el Plan Fase 1d. `./gradlew testDebugUnitTest` y `assembleDebug` en verde (56 tests). Quedan 3 pendientes de Fase 1 que **no** se pudieron resolver por falta de acceso (Supabase, dispositivo físico) — documentados abajo, no a medias.

---

## Problema / motivo

Cierre del ciclo de remediación iniciado el 2026-07-31 con el fix de `minSdk`/Java 17: resolver lo que quedaba pendiente de Fase 1 que fuera código puro, dejar explícitamente catalogado lo que requiere acceso externo (Supabase, un dispositivo real), y arrancar la Fase 2 (Menú) una vez cerrado ese ciclo.

## Cambios aplicados

**P-005** (`ui/login/LoginViewModel.java`, `LoginViewModelFactory.java`) — el `ExecutorService` se recibe por constructor en vez de crearse adentro del ViewModel; la Factory lo instancia. Se agregó `androidx.arch.core:core-testing` (`gradle/libs.versions.toml`, `app/build.gradle.kts`) y `ui/login/LoginViewModelTest.java`: primer test real de un ViewModel del proyecto, con un `ExecutorService` síncrono propio (no hay Guava/`MoreExecutors` en el proyecto) e `InstantTaskExecutorRule` para que `LiveData.postValue()` no dependa de un `Looper` real.

**P-013** (`ui/login/EstadoLogin.java`, `LoginViewModel.java`, `LoginActivity.java`) — se evaluó `Event<T>` genérico vs. un campo `consumido`; se eligió el campo porque el proyecto no tiene otro caso de evento de un solo disparo todavía y un wrapper genérico sería especulativo. `EstadoLogin` ahora guarda `sesionConsumida` y expone `debeNavegar()`; `LoginViewModel.onNavegacionConsumida()` marca el estado; `LoginActivity.render()` navega solo si `debeNavegar()` es cierto.

**P-020** + deuda de tests del Plan Fase 1d (`data/repository/`) — se agregó `data/FakeCall.java` (implementación de `retrofit2.Call<T>` sin red) y dos suites: `SupabaseAuthRepositoryTest` (5 casos: éxito, credenciales inválidas, perfil inexistente, perfil inactivo, sin conexión — los 4 caminos de error del ítem más el camino feliz) y `SupabaseEmpleadoRepositoryTest` (6 casos: `listar` sin sesión/éxito/sin conexión, `crear` éxito/error de Edge Function, `cambiarRol` con error de trigger). Se decidió **no** agregar Mockito (no era dependencia y los fakes manuales alcanzan) y armar los DTOs de fixture con `Gson.fromJson()` en vez de darles constructores solo para testear — no toca las clases de producción.

**P-012** (`app/build.gradle.kts`, `core/SupabaseClient.java`, `local.properties`) — `SUPABASE_ANON_KEY` → `SUPABASE_PUBLISHABLE_KEY` (mismo valor, solo el nombre). Verificado que no queda ninguna referencia al nombre viejo.

**P-010, parte de código** (`ui/login/LoginActivity.java`, `res/layout/activity_login.xml`) — se sacó el `TextView` suelto (`txt_error_login`, con `accessibilityLiveRegion` manual) y el mensaje de error se asocia con `TextInputLayout#setError()` en `til_correo` y `til_contrasenia`. Como el login no distingue qué campo causó el fallo (credenciales incorrectas puede ser cualquiera de los dos), se marca en ambos. Material anuncia el error solo por TalkBack, sin necesitar el live region manual.

## Verificación

`./gradlew testDebugUnitTest` → BUILD SUCCESSFUL, **56 tests, 0 fallas** (9 nuevos: 3 de `LoginViewModelTest`, 5 de `SupabaseAuthRepositoryTest`, 6 de `SupabaseEmpleadoRepositoryTest` — la cuenta exacta está en los XML de `app/build/test-results/`). `./gradlew assembleDebug` → BUILD SUCCESSFUL.

**No verificado** (requiere Android SDK/adb o un dispositivo, no disponibles en este entorno):
- Que el login y Empleados corran de punta a punta en un emulador.
- P-004 en un teléfono físico real.
- TalkBack real y fuente al 200% sobre el nuevo `setError()`.

## Lo que NO cambió — bloqueado o fuera de alcance

- **"Forzar cambio de contraseña en el primer ingreso"** (deuda del Plan Fase 1d) — necesita una columna nueva en `perfiles`/`usuarios` y tocar la Edge Function `crear-empleado`. Bloqueado: el conector MCP de Supabase no está autorizado en esta sesión (requiere flujo OAuth interactivo). SQL propuesto para cuando se autorice o se corra a mano en el SQL Editor:
  ```sql
  ALTER TABLE public.perfiles ADD COLUMN debe_cambiar_contrasena boolean NOT NULL DEFAULT false;
  ```
  y que `crear-empleado` la ponga en `true` al dar de alta. El login (`SupabaseAuthRepository.verificarPerfilYCrearSesion`) tendría que leerla y `LoginActivity` redirigir a `CambiarContraseniaActivity` en vez de a `MainActivity`.
- **S-2** (Plan Fase 1b) — política de contraseñas del servidor. Es un ajuste del dashboard de Supabase (Authentication → Policies), no de código; mismo bloqueo de acceso.
- **P-021** (rol duplicado en `perfiles`/`usuarios`, el de Fase 1d) — decisión de arquitectura de fondo, no se resuelve de paso.
- **P-011** (IDs `snake_case`) — se encontró que el patrón es del proyecto entero (~15 layouts), no solo de `activity_login.xml` como decía el ítem original. Ver la nota agregada en [[Deuda Técnica - Pendientes]]: se decide resolverlo junto con la reorganización feature-first de **P-017** en Fase 2, no ahora, para no tocar cada layout dos veces.
- Nada de lo marcado como "Pendiente — Fase 2/3/antes de publicar" en [[Deuda Técnica - Pendientes]] se tocó: siguen documentados como estaban (P-001, P-002, P-007, P-008, P-009, P-014, P-015, P-016, P-017, P-018, P-019).

## Fase 2 — no arrancó en esta sesión

Con lo de arriba, la Fase 1 queda con 3 pendientes que **solo el usuario puede cerrar** (acceso a Supabase o a un dispositivo) — el merge de `feat/fase1-login` a `master` se posterga hasta que estén.

> [!warning] Corrección (2026-07-31, mismo día)
> Una versión previa de esta nota y del [[Conocimiento Principal]] daban por hecho que la Fase 2 ya había arrancado, con rama `feat/fase2-menu` y "fundación de Room + outbox agregada". **Nada de eso existía**: no había rama ni una sola clase de Room en el repo. Se corrigió en la sesión [[Sesión 2026-07-31 - Plan técnico de Fase 2a (CRUD de Menú) y preparación de Supabase]], que es donde la Fase 2 realmente arranca.

---

## Relaciones

- [[Deuda Técnica - Pendientes]]
- [[Plan Fase 1d - Modulo Empleados Funcional]]
- [[Roadmap de Fases]]
- [[ADR-005 - Offline-first obligatorio desde la Fase 2]]
- [[Estrategia de Pruebas Android]]
