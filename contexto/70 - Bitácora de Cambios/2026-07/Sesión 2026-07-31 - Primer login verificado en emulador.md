---
title: "Sesión 2026-07-31 — Primer login verificado en emulador"
tags:
  - sesion
  - login
  - bug
  - verificacion
date: 2026-07-31
branch: feat/fase1-login
autor_cambios: Claude Code (Sonnet 5)
---

# Sesión 2026-07-31 — Primer login verificado en emulador

> [!success] Resultado
> Se diagnosticó y arregló un crash real (permiso `INTERNET` faltante) usando el log del emulador, y se verificó **por primera vez** que el login corre de punta a punta: `LoginActivity` → Supabase Auth → verificación de perfil admin → `MainActivity` ("¡Bienvenido!").

---

## Problema / motivo

El usuario probó el login manualmente y la app se cerró al tocar "Ingresar". Pidió diagnóstico.

---

## Diagnóstico

Con un emulador ya conectado (`adb devices` → `emulator-5554`), se leyó el logcat directamente en vez de adivinar:

```
java.lang.SecurityException: Permission denied (missing INTERNET permission?)
	at okhttp3.Dns.lambda$static$0(Dns.java:39)
	...
	at com.example.proyectofinalrestaurante.data.repository.SupabaseAuthRepository.login(SupabaseAuthRepository.java:40)
	at com.example.proyectofinalrestaurante.ui.login.LoginViewModel.lambda$login$0...
```

Causa raíz: **`AndroidManifest.xml` nunca tuvo `<uses-permission android:name="android.permission.INTERNET" />`**. Sin ese permiso, cualquier intento de red falla a nivel de sistema operativo — no importaba que el resto del código (URL, llave, endpoints) estuviera bien.

Causa del *crash* específicamente (en vez de un error mostrado en pantalla): `SupabaseAuthRepository.login()` solo tenía `catch (IOException ex)`. `SecurityException` **no hereda de `IOException`**, así que se escapaba sin atrapar del hilo del `ExecutorService` de `LoginViewModel`, y el manejador de excepciones no capturadas de Android mataba todo el proceso.

---

## Cambios aplicados

| Archivo | Cambio |
|---|---|
| `AndroidManifest.xml` | + `<uses-permission android:name="android.permission.INTERNET" />` |
| `data/repository/SupabaseAuthRepository.java` | + `catch (SecurityException ex)` junto al `catch (IOException ex)` existente, devolviendo `Result.fail(...)` en vez de dejar que la excepción se escape |

---

## Verificación — end-to-end real, no solo build

1. `./gradlew assembleDebug` → BUILD SUCCESSFUL.
2. `adb install -r` sobre el emulador ya conectado (`emulator-5554`).
3. `adb logcat -c` (limpiar) + `adb shell am start .ui.login.LoginActivity`.
4. Automatizado con `adb shell input tap/text`: correo `fbarahona280@gmail.com`, contraseña, tap en "Ingresar".
5. `adb logcat` confirmó `Displayed com.example.proyectofinalrestaurante/.MainActivity` — **sin ninguna línea `FATAL EXCEPTION`**.
6. Captura de pantalla final: `MainActivity` mostrando **"¡Bienvenido!"** y el botón "Cerrar sesión".

Es la primera vez, desde que existe el proyecto, que se verifica el camino feliz completo: `LoginActivity` → `LoginViewModel` → `SupabaseAuthRepository` → Supabase Auth real → verificación de `perfiles` (admin, activo) → `Sesion` → navegación a `MainActivity`.

---

## Lo que NO cambió

- Ninguna otra parte del código Java tocada.
- No se instaló en un dispositivo físico (sigue bloqueado por **P-003**, `minSdk=37`) — solo se verificó en el emulador.
- No se agregó ningún test automatizado de este flujo (sigue como deuda, **P-005**/**P-020**).

---

## Relaciones

- [[Módulo Login]]
- [[Plan de Conexión con Supabase]]
- [[Deuda Técnica - Pendientes]] — P-022 resuelto
- [[Sesión 2026-07-31 - Alta del primer usuario admin]]
