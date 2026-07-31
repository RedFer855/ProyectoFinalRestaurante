---
title: "Sesión 2026-07-31 — Plan Fase 1b completo: recuperación de contraseña y roles"
tags:
  - sesion
  - recuperacion
  - roles
  - ui
  - test
date: 2026-07-31
branch: feat/fase1-login
autor_cambios: opencode (DeepSeek V4 Flash Free)
---

# Sesión 2026-07-31 — Plan Fase 1b completo: recuperación de contraseña y roles

> [!success] Resultado
> Se ejecutó **todo** el [[Plan Fase 1b - Recuperación de Contraseña y Roles]] (6 entregables) en la rama `feat/fase1-login`: recuperación de contraseña de 2 pasos contra Supabase Auth, validación de contraseña con tests JUnit, paleta visual **Terracota**, sesión activa en memoria y menú hamburguesa con ítems filtrados por rol. Build y tests verdes; pendiente de **verificación manual en emulador**.

---

## Problema / motivo

Completar la parte de "recuperación de contraseña y roles" de la Fase 1 con calidad según el [[Estándar de Ingeniería Android]]: código nuevo que **no replique** los patrones en deuda (P-005, P-013, P-019), con su test.

---

## Cambios aplicados (un commit por entregable)

| Commit | Entregable | Cambio |
|---|---|---|
| `e2b11b8` | E1 — Capa de datos | `data/remote/dto/RecuperarRequestDto`, `VerificarCodigoRequestDto`, `VerificarCodigoResponseDto`, `CambiarContraseniaRequestDto`; `SupabaseAuthApi` +3 endpoints (`POST auth/v1/recover`, `POST auth/v1/verify`, `PUT auth/v1/user`); `domain/repository/AuthRepository` +3 métodos; `data/repository/SupabaseAuthRepository` los implementa (nunca revela si el correo existe; `cambiarContrasenia` hace logout tras el éxito) |
| `faff17b` | E2 — Validador | `domain/RequisitoContrasenia`, `domain/ResultadoValidacion`, `domain/ValidadorContrasenia` (8+ chars, mayúscula, minúscula, dígito, símbolo) + `ValidadorContraseniaTest` (10 tests) |
| `0f6c4fc` | E3+E4 — Pantallas + paleta | Paleta **Terracota** en `values/colors.xml` (brand_primary `#9C4221`), `values-night/colors.xml` y `themes.xml`; **borrado `values-night/themes.xml`** (apagaba el theme en modo noche). `ui/recuperacion/`: `SolicitarCodigoActivity`/`ViewModel`/`Factory` + `CambiarContraseniaActivity`/`ViewModel`/`Factory` con contador de 60 s **en el ViewModel** (`segundosRestantes`, `onNavegacionConsumida`). TextButton `btn_olvidaste_contrasenia` en el login. Registradas las 2 actividades en el manifest |
| `c0f6855` | E5 — Sesión activa | `core/SesionActual` (guardar/obtener/limpiar) + `SesionActualTest`; `LoginActivity` guarda la sesión; `MainActivity` redirige al login si no hay sesión |
| `2a441ad` | E6 — Menú por rol | `domain/VisibilidadMenu` (matriz admin/mesero/cocina + rol desconocido → solo Inicio) + `VisibilidadMenuTest`; `Sesion` gana `nombre` (constructor 5-arg); `MainActivity` rehecha con `DrawerLayout` + `MaterialToolbar` + `NavigationView` (cabecera con iniciales/nombre/rol), placeholder "Próximamente" para módulos no construidos; 8 iconos vectoriales + `res/menu/menu_navegacion.xml` + `res/layout/nav_header.xml`; dependencia `androidx.drawerlayout:1.2.0` declarada en el catálogo de versiones |

## Verificación

- `./gradlew assembleDebug testDebugUnitTest` → **BUILD SUCCESSFUL** (2 corridas; la primera falló por `findViewById` sin cast a `NavigationView`, corregido).
- Tests unitarios nuevos: `ValidadorContraseniaTest` (10), `SesionActualTest` (3), `VisibilidadMenuTest` (5).
- **Pendiente:** verificación manual en emulador del flujo completo (login → drawer por rol → recuperación de 2 pasos). Sigue bloqueada la e2e real sin credenciales de Supabase en `local.properties` (dejadas vacías a propósito).

## Lo que NO cambió

- No se tocó la Fase 0 ni la deuda crítica (**P-003** `minSdk=37`, **P-004**, **P-006**…).
- No se persistió el `access_token` (sigue **P-009**); la sesión es en memoria y la recuperación termina volviendo al login.
- Las pantallas nuevas siguen `Activity` + `findViewById` e IDs `snake_case` → **extienden el alcance de P-015 y P-011**, no se replicaron P-005/P-013/P-019.
- No se construyó ningún módulo de negocio real (Menú/Pedidos/Mesas) — placeholder en `MainActivity` (decisión del usuario, ver `P-015`/roadmap).

---

## Relaciones

- [[Plan Fase 1b - Recuperación de Contraseña y Roles]]
- [[Módulo Login]]
- [[Arquitectura Actual]]
- [[Guía de Diseño Visual]]
- [[Deuda Técnica - Pendientes]] — P-011/P-015 extendidos en alcance
