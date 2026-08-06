---
title: "Sesión 2026-08-05 — La recuperación de contraseña que se rompía en silencio"
tags:
  - sesion
  - login
  - supabase
date: 2026-08-05
branch: master
autor_cambios: Claude (agente)
---

# Sesión 2026-08-05 — La recuperación de contraseña que se rompía en silencio

> [!success] Resultado
> El fallo era del **servicio de correo de Supabase** (429 `over_email_send_rate_limit`, 2 mensajes/hora), no del flujo OTP — que se verificó funcionando de punta a punta en los logs. Pero el código **se tragaba ese 429** y mandaba al usuario a esperar un código que nunca se envió: eso se corrigió y quedó cubierto con pruebas. La configuración de SMTP propio queda como [[Deuda Técnica - Pendientes|P-032]].

---

## Problema / motivo

Reporte: "la recuperación de contraseña falló, no sé si el problema es en Supabase o en el código". Ambas cosas, en distinta medida.

## Diagnóstico

Los logs de Auth del proyecto `mxarlisuueovxvttytcm` (MCP de Supabase, ventana de 24 h) cuentan la historia completa:

```
01:04:32  mail.send  recovery → kelvinizaguirre914@gmail.com
01:04:33  POST /recover  → 200
01:05:12  POST /verify   → 200   login_method: otp
01:05:13  PUT  /user     → 200   user_modified
01:05:13  mail.send  password_changed_notification
--- de acá en adelante, dentro de la misma hora ---
01:54:42  POST /recover  → 429  over_email_send_rate_limit
01:55:49  POST /recover  → 429  over_email_send_rate_limit
01:57:16  POST /recover  → 429  over_email_send_rate_limit
```

Dos conclusiones:

1. **El flujo OTP está bien.** Ese `/verify` + `PUT /user` de las 01:05 es una recuperación completa y exitosa. La plantilla del correo tiene el `{{ .Token }}`, el `type: "recovery"` es el correcto y el `PUT /auth/v1/user` con la sesión temporal funciona.
2. **El correo se acabó.** El proyecto usa el servicio de correo integrado de Supabase, que da **2 mensajes por hora para todo el proyecto** (no por usuario, no por correo). El `recovery` + el `password_changed_notification` de las 01:04-01:05 agotaron la cuota; todo lo que se pidió después de eso, aunque fuera de otro empleado, murió con 429.

El agravante era del lado de la app: `solicitarCodigo()` ignoraba por completo la respuesta HTTP. El 429 se reportaba como éxito, la app navegaba a la pantalla del código, y el usuario esperaba un correo que Supabase nunca mandó. Sin mensaje, sin log, sin nada — el peor modo de fallo posible. La intención original era buena (no revelar si una cuenta existe), pero se aplicó demasiado ancho: un límite de envíos **del proyecto** no dice nada sobre si el correo pedido está registrado.

## Cambios aplicados

- **`data/repository/SupabaseAuthRepository.java`** — `solicitarCodigo()` ahora lee la respuesta y trata el **429** como fallo, con mensaje al usuario. Cualquier otro estado se sigue reportando como éxito, que es lo que protege contra la enumeración de cuentas.
- **`ui/recuperacion/CambiarContraseniaViewModel.java`** — `reenviarCodigo()` era un *fire-and-forget*: descartaba el `Result` del repositorio. Ahora publica el error en el estado. El contador de 60 s se mantiene aunque falle — si el fallo es por límite de envíos, reintentar de inmediato solo empeora.
- **`data/repository/SupabaseAuthRepositoryTest.java`** — 4 casos nuevos para `solicitarCodigo`: 429 → fallo, 400 → éxito (no delatar la cuenta), 200 → éxito, `IOException` → fallo de red.
- **`ui/recuperacion/SolicitarCodigoViewModelTest.java`** *(nuevo)* — el caso que importa: con envío fallido, `correoConfirmado` queda en `null` y la pantalla del código no se abre.

## Verificación

`./gradlew testDebugUnitTest assembleDebug` → **BUILD SUCCESSFUL**, 632 tests, 0 fallas.

No se probó a mano contra Supabase: la cuota de correo del proyecto está agotada, que es precisamente el pendiente P-032.

## Lo que NO cambió

- El flujo OTP (`/recover` → `/verify` → `PUT /user`) — se comprobó correcto en los logs, no se tocó.
- `verificarCodigo()` y `cambiarContrasenia()` conservan su manejo de errores. Sus límites de tasa (360/hora) están lejísimos de ser el problema.
- La configuración de Supabase: el SMTP propio hay que cargarlo desde el panel, no se puede hacer desde el repo. Es P-032.
- Los mensajes de error siguen hardcodeados en el repositorio — sigue siendo P-019, no se amplió acá.

---

## Relaciones

- [[Deuda Técnica - Pendientes]] — abre **P-032** (SMTP propio sin configurar)
- [[Módulo Login]]
- [[Plan Fase 1b - Recuperación de Contraseña y Roles]]
- [[Arquitectura Actual]]
