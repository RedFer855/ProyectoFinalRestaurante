---
title: Base Repository con manejo de errores
tags:
  - patron
  - datos
  - java
lifecycle: draft
---

# Base Repository con manejo de errores

> [!abstract] Definición
> Equivalente al "Base Repository con TryAsync" de un proyecto .NET: centralizar el `try/catch` de las llamadas Retrofit en un solo lugar, para que cada repositorio nuevo no reescriba el mismo bloque.

## Cuándo usarlo

Cuando exista más de un repositorio Retrofit (Fase 2 en adelante — Menú, Pedidos). En Fase 1 solo existe `SupabaseAuthRepository`, así que este patrón está documentado pero **todavía no extraído** — ver estado abajo.

## Cómo se implementaría

```java
public abstract class BaseRepository {
    protected <T> Result<T> tryCall(RetrofitCall<T> call) {
        try {
            Response<T> response = call.execute();
            if (!response.isSuccessful() || response.body() == null) {
                return Result.fail("Error del servidor: " + response.code());
            }
            return Result.ok(response.body());
        } catch (IOException ex) {
            return Result.fail("Error de conexión: " + ex.getMessage());
        }
    }

    @FunctionalInterface
    protected interface RetrofitCall<T> {
        Response<T> execute() throws IOException;
    }
}
```

## Estado en el proyecto

> [!warning] No implementado todavía
> `SupabaseAuthRepository` tiene su propio `try/catch` porque es el único repositorio. Cuando se agregue un segundo repositorio (Fase 2), extraer esta clase base — ver ítem de deuda técnica sugerido en [[Deuda Técnica - Pendientes]].

---

## Relaciones

- [[Result Pattern]]
- [[Repository Pattern]]
- [[Arquitectura Actual]]
