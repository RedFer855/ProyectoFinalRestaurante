---
title: "Sesión 2026-07-29 — Carga del esquema relacional en Supabase"
tags:
  - sesion
  - supabase
  - base-de-datos
  - seguridad
date: 2026-07-29
branch: feat/fase1-login
autor_cambios: Claude Code (Opus 5)
---

# Sesión 2026-07-29 — Carga del esquema relacional en Supabase

> [!success] Resultado
> Se subieron las **14 tablas** del esquema del restaurante al proyecto Supabase real. El esquema llegaba **sin RLS**, lo que producía **13 errores nivel ERROR** en el linter de seguridad; se corrigió activando RLS en todas. Documentado en [[Esquema de Base de Datos]]. Detectado un conflicto de fondo entre dos sistemas de autenticación (**P-021**).

---

## Problema / motivo

El usuario pasó el DDL completo del esquema (catálogos, personas, productos, pedidos) para subirlo al proyecto **Restaurante** (`mxarlisuueovxvttytcm`).

---

## Cambios aplicados

### Corrección previa al SQL recibido

El DDL venía **cortado**: `detalle_complemento` no cerraba el paréntesis ni el statement. Se completó respetando la estructura enviada (PK, dos FK a `detalle_pedido` y `complemento`).

### Migraciones aplicadas

| Migración | Contenido |
|---|---|
| `esquema_restaurante_catalogos_y_personas` | `estado_general`, `roles`, `categoria`, `tipo_pedido`, `empresa`, `empleados`, `usuarios`, `clientes` |
| `esquema_restaurante_productos_mesas_y_pedidos` | `mesa`, `platillo`, `complemento`, `pedido`, `detalle_pedido`, `detalle_complemento` |
| `habilitar_rls_en_esquema_restaurante` | `ENABLE ROW LEVEL SECURITY` en las 14 tablas + `REVOKE SELECT` a `anon` sobre `usuarios`, `empleados`, `clientes` |

### El hallazgo de seguridad

Tras aplicar el esquema, `get_advisors(security)` devolvió **13 lints nivel ERROR** — `rls_disabled_in_public` en cada tabla nueva.

**Por qué importaba:** la llave `anon` está embebida en el APK (es pública por diseño). Sin RLS, cualquiera que extraiga esa llave del APK tenía lectura **y escritura** sobre toda la base — incluyendo `usuarios.contrasena` y los datos personales de `clientes` y `empleados`. Es exactamente el escenario que la regla de oro del proyecto prohíbe (ver [[Seguridad y Privacidad Android]], sección 3).

**Después de la corrección:** 0 errores. Los 13 pasaron a nivel **INFO** (`rls_enabled_no_policy`), que es el estado correcto: RLS activa sin políticas = **deny-all**. Cada módulo agregará sus políticas en su fase, junto al código que las consume.

---

## Verificación

- `list_tables` → 15 tablas en `public` (14 nuevas + `perfiles`), **todas con `rls_enabled: true`**, todas con 0 filas.
- `get_advisors(security)` → **0 ERROR**. Quedan INFO (RLS sin policy, esperado) y WARN de exposición en el schema de GraphQL (la tabla es *descubrible*, pero no legible — la policy es la que manda).
- ⬜ No se cargó ningún dato. Los catálogos base están vacíos.
- ⬜ No se tocó código Java: ningún repositorio consume estas tablas todavía.

---

## Hallazgo de fondo — P-021

La base quedó con **dos sistemas de autenticación en paralelo**:

- `auth.users` + `public.perfiles` (uuid, credencial gestionada por Supabase Auth) — **es el que usa el código hoy**.
- `public.usuarios` (INT identity, columna `contrasena VARCHAR(255)`) — creada, sin usar.

Guardar contraseñas propias implica hacerse cargo de hashing, salt, política de fuerza, rate limiting y recuperación — todo lo que Supabase Auth ya resuelve, y que Bimbo documentó como trabajo real en su plan de seguridad. Registrado como **P-021** (🔴), con la solución propuesta: reemplazar `contrasena` por `id_auth_user uuid REFERENCES auth.users(id)`.

**No se tomó la decisión ni se modificó la tabla** — es una decisión de fondo del dueño del proyecto, y cuando se resuelva corresponde un ADR.

---

## Otras observaciones registradas (sin corregir)

- `pedido.fecha` es `TIMESTAMP` sin zona horaria — en Supabase la convención es `TIMESTAMPTZ`. Barato de cambiar ahora, caro con datos cargados.
- `empresa.logo_empresa BYTEA` — imágenes en la fila; Supabase Storage es la ruta natural (Bimbo lo resolvió así con `ServicioLogo`).
- Catálogos vacíos bloquean todo lo demás: `estado_general`, `roles`, `categoria` y `tipo_pedido` son FK obligatorias de casi todas las tablas.

---

## Lo que NO cambió

- Cero código Java tocado. La app sigue compilando igual y su login sigue usando `perfiles`.
- No se creó ninguna política RLS (deliberado — van por módulo/fase).
- No se cargó ningún dato en ninguna tabla.
- No se modificó ni eliminó `perfiles` ni `usuarios`, pese al conflicto — eso lo decide el dueño del proyecto.
- Sigue sin existir un usuario de prueba en Auth (bloqueante del login end-to-end, de la sesión anterior).

---

## Relaciones

- [[Esquema de Base de Datos]]
- [[Deuda Técnica - Pendientes]] — P-021
- [[Seguridad y Privacidad Android]]
- [[Plan de Conexión con Supabase]]
- [[Módulo Login]]
- [[Sesión 2026-07-29 - Conexión real a Supabase y verificación de perfil activo]]
