---
title: Result Pattern
tags:
  - patron
  - manejo-errores
  - java
aliases:
  - Result<T>
---

# Result Pattern

> [!abstract] Definición
> En lugar de dejar que una excepción de red/parseo llegue cruda hasta la UI, los métodos de `data` retornan un objeto `Result<T>` que puede ser éxito o fallo. El llamador (`ViewModel`) maneja ambos casos explícitamente.

---

## El tipo Result

```java
public final class Result<T> {
    private final boolean success;
    private final T value;
    private final String error;

    private Result(T value) { this.success = true; this.value = value; this.error = null; }
    private Result(String error) { this.success = false; this.value = null; this.error = error; }

    public static <T> Result<T> ok(T value) { return new Result<>(value); }
    public static <T> Result<T> fail(String error) { return new Result<>(error); }

    public boolean isSuccess() { return success; }
    public T getValue() { return value; }
    public String getError() { return error; }
}
```

## Repositorio con Result

```java
public Result<Sesion> login(String correo, String contrasenia) {
    try {
        Response<LoginResponseDto> response = api.login(new LoginRequestDto(correo, contrasenia)).execute();
        if (!response.isSuccessful() || response.body() == null) {
            return Result.fail("Credenciales inválidas o servidor no disponible");
        }
        return Result.ok(mapToSesion(response.body()));
    } catch (IOException ex) {
        return Result.fail("Error de conexión: " + ex.getMessage());
    }
}
```

## ViewModel con Result

```java
public void login(String correo, String contrasenia) {
    estado.setValue(EstadoLogin.cargando());
    executor.execute(() -> {
        Result<Sesion> resultado = authRepository.login(correo, contrasenia);
        if (resultado.isSuccess()) {
            estado.postValue(EstadoLogin.exito(resultado.getValue()));
        } else {
            estado.postValue(EstadoLogin.error(resultado.getError()));
        }
    });
}
```

---

## Beneficios

| Sin Result | Con Result |
|---|---|
| Excepciones sin contexto llegan a la Activity | Error con mensaje descriptivo, listo para mostrar |
| `try/catch` disperso en la UI | Manejo explícito y centralizado en el ViewModel |
| UI se rompe silenciosamente (crash) | UI muestra un mensaje de error controlado |
| Difícil de testear | `resultado.isSuccess()` es trivial de testear con JUnit |

---

## Estado en el proyecto

> [!success] Fase 1
> - ✅ `Result<T>` implementado en `domain`
> - ✅ `SupabaseAuthRepository.login(...)` retorna `Result<Sesion>`
> - ⬜ Próximos repositorios (Menú, Pedidos) deben seguir el mismo contrato desde el día uno

---

## Relaciones

- [[Base Repository con manejo de errores]]
- [[Clean Architecture]]
- [[Repository Pattern]]
- [[SOLID]]
