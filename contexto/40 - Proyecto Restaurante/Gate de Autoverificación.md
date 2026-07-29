---
title: "Gate de Autoverificación"
tags:
  - restaurante
  - proceso
  - calidad
date: 2026-07-29
---

# Gate de Autoverificación

> [!abstract] Para qué existe
> Sin un checklist explícito, un agente entrega código **plausible** con placeholders y APIs deprecadas. Este gate es lo que convierte la revisión humana en opcional en vez de obligatoria.
>
> Se ejecuta y se **imprime completo** al final de cada entrega. Si un ítem falla, se corrige antes de responder — no después.

---

## Checklist

```
[ ] Compila mentalmente: todos los imports existen, todas las firmas coinciden,
    ningún símbolo inventado
[ ] Toda dependencia usada está en libs.versions.toml y en el build.gradle.kts del módulo
[ ] Ninguna API deprecada (ver Lista Negra); nada que rompa con el targetSdk actual
[ ] domain/ sin un solo import de android.* / androidx.* / Retrofit / Room
[ ] La UI no llama a DAO, ApiService ni DataSource directamente
[ ] Cero trabajo de I/O en el hilo principal; todo I/O en un Executor inyectado
[ ] ViewModel sin Context / Activity / Fragment / Resources / View
[ ] Fragment: binding anulado en onDestroyView(); observers con getViewLifecycleOwner()
[ ] Cero strings, colores o dimens hardcodeados; todo en recursos
[ ] Cero secretos, llaves o URLs de producción en el código fuente
[ ] Nulabilidad anotada (@NonNull/@Nullable) en toda API pública
[ ] Errores traducidos a AppException/Result; ningún catch vacío ni printStackTrace()
[ ] Nomenclatura respetada en clases, métodos, campos, recursos e IDs
[ ] Room: migración escrita si cambió el esquema; sin fallbackToDestructiveMigration
[ ] Escrituras offline-first: local primero + encolado en outbox
[ ] Listas con ListAdapter + DiffUtil; sin notifyDataSetChanged()
[ ] Insets/edge-to-edge manejados; back con OnBackPressedDispatcher
[ ] Accesibilidad: contentDescription y objetivos táctiles ≥ 48dp
[ ] Pruebas incluidas y con aserciones reales (no assertTrue(true))
[ ] Impacto en arranque/tamaño evaluado; nada pesado en Application.onCreate()
```

---

## Cómo se usa

1. **Al terminar de escribir código**, recorrer el checklist ítem por ítem.
2. Marcar ✅ o ❌ **con honestidad**. Un ítem marcado ✅ sin verificar es peor que no tener el gate: crea confianza falsa.
3. Los ítems que **no aplican** se marcan `➖ N/A` con una palabra de justificación (ej. `➖ N/A — sin Room todavía`).
4. Cualquier ❌ se corrige antes de entregar, o se convierte en un ítem `P-NNN` de [[Deuda Técnica - Pendientes]] con justificación explícita de por qué se acepta.

> [!warning] Un ❌ aceptado siempre deja rastro
> Si algo no se cumple y se decide seguir igual, eso **no desaparece**: se registra como deuda. Deuda documentada es una decisión; deuda no documentada es una trampa para el siguiente agente.

---

## Ejemplo de gate aplicado a la Fase 1

Así se vería el gate ejecutado sobre el código de login existente hoy:

| Ítem | Estado |
|---|---|
| Compila | ✅ `./gradlew assembleDebug` → BUILD SUCCESSFUL |
| Dependencias en el catálogo | ✅ |
| Sin APIs deprecadas | ⚠️ `findViewById` (ver P-015) |
| `domain` sin Android | ✅ |
| UI no toca `ApiService` | ✅ |
| I/O fuera del hilo principal | ✅ pero el Executor no está inyectado (P-005) |
| ViewModel sin `Context` | ✅ |
| Binding anulado | ➖ N/A — usa `Activity`, no `Fragment` (P-015) |
| Sin hardcodear recursos | ❌ color de error `#D32F2F` en el layout (P-011) |
| Sin secretos | ✅ vía `BuildConfig` |
| Nulabilidad anotada | ⚠️ parcial |
| Errores como `Result` | ✅ |
| Nomenclatura | ⚠️ IDs en `snake_case` (P-011) |
| Insets/edge-to-edge | ❌ `LoginActivity` no los maneja (P-004) |
| Accesibilidad | ❌ sin `contentDescription` ni `labelFor` (P-010) |
| Pruebas | ❌ ninguna (P-005) |

**Resultado: la Fase 1 no pasa el gate.** Todos los ❌ están registrados en [[Deuda Técnica - Pendientes]] y agrupados en la **Fase 0** de [[Roadmap de Fases]].

---

## Relaciones

- [[Estándar de Ingeniería Android]]
- [[Lista Negra de APIs Android]]
- [[Deuda Técnica - Pendientes]]
- [[Estrategia de Pruebas Android]]
- [[Convenciones Java]]
