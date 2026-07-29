---
title: "Android 16 y 17 — Cambios de Comportamiento (targetSdk 36/37)"
tags:
  - referencia
  - android
  - breaking-changes
date: 2026-07-29
lifecycle: verified
---

# Android 16 y 17 — Cambios de Comportamiento

> [!info] Fuente
> [Behavior changes: apps targeting Android 16+](https://developer.android.com/about/versions/16/behavior-changes-16), [Behavior changes: apps targeting Android 17+](https://developer.android.com/about/versions/17/behavior-changes-17), [Android Developers Blog — resizability y orientación en Android 17](https://android-developers.googleblog.com/2026/02/prepare-your-app-for-resizability-and.html). Verificado el 2026-07-29.

> [!danger] Por qué esta nota existe
> Estos cambios **no dan error de compilación**. Se activan solos al subir `targetSdk` y rompen la app en runtime o visualmente. Son la causa #1 de regresiones silenciosas al migrar.

---

## targetSdk 36 (Android 16)

### 1. Edge-to-edge obligatorio, sin opt-out

- Android 15 (API 35) ya forzaba edge-to-edge, pero se podía desactivar con `R.attr#windowOptOutEdgeToEdgeEnforcement`.
- **En apps que apuntan a API 36, ese atributo está deprecado y desactivado: no hay forma de salirse.**
- Consecuencia: el contenido se dibuja **debajo** de la barra de estado y la de navegación. Si no se manejan los insets, los botones y textos quedan tapados.

**Manejo correcto (obligatorio en cada Activity/Fragment):**

```java
EdgeToEdge.enable(this);
ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.raiz), (v, insets) -> {
    Insets barras = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
    v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
    return insets;
});
```

> [!bug] Hallazgo en este proyecto
> `LoginActivity` **no llama a `EdgeToEdge.enable()` ni maneja insets**, mientras que `MainActivity` sí. Con `targetSdk 37` esto significa que el título del login puede quedar bajo la barra de estado y el botón bajo la de navegación. Registrado como **P-004** en [[Deuda Técnica - Pendientes]].

### 2. `onBackPressed()` deja de invocarse

- Con predictive back activo, `Activity.onBackPressed()` está deprecado y **ya no se llama**.
- Reemplazo: `OnBackPressedDispatcher` + `OnBackPressedCallback` (AndroidX), y `android:enableOnBackInvokedCallback="true"` en el manifiesto.

```java
requireActivity().getOnBackPressedDispatcher().addCallback(
        getViewLifecycleOwner(),
        new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { /* … */ }
        });
```

### 3. Otros

- `USE_FULL_SCREEN_INTENT` debe solicitarse explícitamente (apps de alarmas, llamadas, delivery).
- Restricciones adicionales sobre servicios en primer plano y trabajo en background.

---

## targetSdk 37 (Android 17) — lo que ya aplica a este proyecto

> [!warning] Este proyecto está en `targetSdk 37`, así que estos cambios **ya están activos**.

### 1. Adaptabilidad obligatoria en pantallas grandes

- Android 17 **elimina el opt-out de las restricciones de orientación y redimensionado** en pantallas grandes (`sw > 600dp`).
- `android:screenOrientation` forzado y `resizeableActivity="false"` **se ignoran** en tablets y plegables.
- Consecuencia: los layouts deben ser responsivos de verdad (`sw600dp`, ConstraintLayout flexible). Un layout diseñado solo para teléfono en vertical se verá roto o estirado.

### 2. Permiso `ACCESS_LOCAL_NETWORK`

Nuevo permiso en runtime para acceder a la red local. Relevante si en el futuro la app descubre impresoras de tickets o dispositivos en LAN — caso muy plausible en un restaurante (**comanda a cocina por red local**).

### 3. Contact Picker reemplaza `READ_CONTACTS`

Y Contacts Provider 2 (CP2) restringe columnas con PII.

### 4. Otros

- Retraso de 3 horas en SMS de OTP para apps dirigidas.
- Restricciones de audio en background (migrar de ExoPlayer 2 a Media3).
- ECH (Encrypted Client Hello) oportunista en TLS.

---

## Checklist de migración al subir targetSdk

- [ ] Cada pantalla llama `EdgeToEdge.enable()` y aplica insets a su vista raíz
- [ ] Ningún `onBackPressed()` sobrescrito; todo con `OnBackPressedDispatcher`
- [ ] `android:enableOnBackInvokedCallback="true"` en el manifiesto
- [ ] Ningún `android:screenOrientation` forzado; layouts probados en `sw600dp`
- [ ] Probado en dispositivo/emulador con la versión de Android correspondiente, no solo compilado

---

## Relaciones

- [[Requisitos de Google Play 2026]]
- [[Niveles de API y minSdk - Cobertura Real]]
- [[Lista Negra de APIs Android]]
- [[Deuda Técnica - Pendientes]] — P-004
- [[Estándar de Ingeniería Android]]
