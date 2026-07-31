---
title: "Sesión 2026-07-31 — Alta del primer usuario admin"
tags:
  - sesion
  - supabase
  - roles
  - login
date: 2026-07-31
branch: feat/fase1-login
autor_cambios: Claude Code (Sonnet 5)
---

# Sesión 2026-07-31 — Alta del primer usuario admin

> [!success] Resultado
> Primer usuario real del sistema dado de alta, con rol admin, enlazado en las tres tablas relevantes (`perfiles`, `empleados`, `usuarios`). El login de la Fase 1 queda listo para probarse de punta a punta por primera vez.

---

## Problema / motivo

Bootstrap del primer administrador: el flujo "admin invita a un empleado" (Edge Function, discutido pero no implementado todavía) necesita que ya exista al menos un admin. Ese primero se crea a mano.

---

## Cambios aplicados

### Cuenta de Auth — creada por el usuario, no por el agente

El usuario creó la cuenta desde el dashboard de Supabase (Authentication → Users → Add user, con *Auto Confirm User*). Se verificó con `select ... from auth.users where id = ...` antes de insertar nada: correo `fbarahona280@gmail.com`, confirmado, UUID `f121a2ad-4e17-440d-aaf1-0ea907191da5`.

### Datos vinculados (una sola transacción SQL, vía CTE)

```sql
with nuevo_empleado as (
  insert into public.empleados (nombres, apellidos, identidad, correo, id_estado)
  values ('Fernando José', 'Barahona Castro', '0801200307196', 'fbarahona280@gmail.com', 1)
  returning id_empleado
),
nuevo_perfil as (
  insert into public.perfiles (id, nombre, rol, activo)
  values ('f121a2ad-4e17-440d-aaf1-0ea907191da5', 'Fernando José Barahona Castro', 'admin', true)
  returning id
)
insert into public.usuarios (apodo_usuario, id_rol, id_empleado, id_estado, id_auth_user)
select 'fbarahona', 1, nuevo_empleado.id_empleado, 1, 'f121a2ad-4e17-440d-aaf1-0ea907191da5'
from nuevo_empleado;
```

Un solo `INSERT` atómico con CTEs — evita tener que adivinar el `id_empleado` generado.

| Tabla | Fila |
|---|---|
| `perfiles` | `id`=UUID, `rol='admin'`, `activo=true` — **la que usa `SupabaseAuthRepository` hoy** |
| `empleados` | Fernando José / Barahona Castro / `0801200307196` / `id_estado=1 (Activo)` |
| `usuarios` | `apodo_usuario='fbarahona'`, `id_rol=1 (admin)`, `id_auth_user`=mismo UUID |

---

## Verificación

- `select` de las 3 tablas con join → una sola fila, todo consistente (ver query en la sesión).
- ⬜ **Falta probar el login desde la app** (Android) — con `perfiles` cargado, el camino feliz debería funcionar por primera vez. Si se prueba en dispositivo físico, sigue bloqueado por **P-003** (`minSdk 37`); en emulador con API 37+ debería andar.

---

## Lo que NO cambió

- Ningún código Java tocado.
- No se implementó todavía la Edge Function de "admin invita empleado" (queda para cuando se necesite un segundo usuario).
- Sigue sin resolverse si `perfiles` y `usuarios` conviven permanentemente o se consolidan — ver **P-021** (resuelto el problema de la contraseña, pendiente la convivencia de las dos tablas).

---

## Relaciones

- [[Plan de Conexión con Supabase]]
- [[Módulo Login]]
- [[Esquema de Base de Datos]]
- [[Sesión 2026-07-29 - Resolución P-021 y admin pendiente de datos]]
