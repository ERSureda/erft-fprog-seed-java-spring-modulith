---
name: generate-web-controllers
description: >-
  Genera exclusivamente los controladores REST (*Controller) de un Bounded Context en Java 21
  dentro de infrastructure/adapter/in/web/ a partir de docs/modules/<module_id>/API.md,
  ensamblando únicamente contratos ya existentes (*UseCase, *Command,
  *Query, *Result, *HttpRequest).
---

# Skill: Generación de Controladores REST (`generate-web-controllers`)

## 1. ENTRADAS Y SALIDAS (I/O)

* **Lectura obligatoria**:
    1. `docs/02-TECH_STACK.md` -> Extraer `base_package` (`<base>`).
    2. `docs/modules/<module_id>/API.md`: Extraer `Ruta Base REST` (o `Base Path REST`), tabla Sección 1 (`ID`, `Caso de Uso (*UseCase) y Comando (*Command)`, `Canal de Entrada (REST | ASYNC_WORKER | INTERNAL_PORT)`, `Tipo (ADR-006)`, `HTTP Status (@ResponseStatus)`), tabla Sección 2 (`ID`, `Caso de Uso (*UseCase) y Consulta (*Query)`, `Canal de Entrada (REST | INTERNAL_PORT)`, `Contenedor de Salida (*Result | PageResult | CursorResult)`, `HTTP Status`) y bloques de detalle `3.X` / `3.4` (`DTO de Entrada Web`, `DTO de Salida Aplicación/Web`, `DTO de Salida Paginado`, `Parámetro de Ruta`, `Parámetros QRY-xx`, cabeceras de operación, filas `IN (Request)` / `IN (Path)` / `OUT (Result)` y `DTO Objetivo (*Result)`).
    3. Clases existentes en `<base>.<module_id>`: `application/port/in/*UseCase`, `application/command/*Command`, `application/query/*Query`, `application/result/*Result`, `domain/model/enums/*` e `infrastructure/adapter/in/web/dto/*HttpRequest`.
* **Política de Aborto Inmediato (`Fail-Fast`)**:
    * Abortar listando **todas** las carencias en una sola respuesta si:
        * El documento `API.md` no existe, tiene marcadores sin rellenar (`[...]`, `<...>`) o le falta el bloque `3.X` / `3.4` a alguna operación HTTP/REST en alcance.
        * Falta cualquiera de las piezas requeridas por las operaciones HTTP/REST en alcance: `package-info.java` (`generate-module-structure`), `*UseCase` / `*Command` / `*Query` / `*Result`, `*HttpRequest` (`generate-web-http-requests`) o enums referenciados (`generate-domain-enums`).
        * Existe incoherencia entre el contrato y el código (ej. `HTTP Status: 204` pero `execute(...)` no es `void`; respuesta paginada con `CursorResult<T>` pero el retorno no coincide; campos de `OUT (Result)` / `DTO Objetivo (*Result)` ausentes en `*Result`; o parámetros de `toCommand(...)` / constructor de `*Query` que no pueden poblarse sin pasar `null`).
* **Escritura exclusiva (`Blast Radius = 0`)**:
    * `src/main/java/<base>/<module_id>/infrastructure/adapter/in/web/<RecursoSingular>Controller.java`
    * **PROHIBIDO** crear o modificar `*HttpRequest`, `*UseCase`, `*Command`, `*Query`, `*Result`, tests o clases internas/auxiliares en el controlador. Si el controlador ya existe, conservar métodos previos intactos y añadir solo las operaciones nuevas.

## 2. REGLAS INMUTABLES (`INP-01`, `INP-02`, `INP-03`, `INP-05`, `APP-04`, `ADR_005`)

1. **Filtrado de Canal**: Procesar únicamente operaciones cuyo `Canal de Entrada` sea HTTP/REST (`GET`, `POST`, `PUT`, `PATCH`, `DELETE`). Omitir canales `ASYNC_WORKER` e `INTERNAL_PORT` (reportarlos como omitidos al final). Ignorar `Cabeceras de Contexto Requeridas (ApiHeaders)` (`X-Tenant-Id`, `X-User-Id`, `X-Roles`), así como las columnas `Roles Autorizados (ExecutionContext)`, `Método del Agregado Invocado (DOMAIN.md)`, `Evento Outbox / Efecto (DOMAIN.md)`, `Índice de Soporte en schema.sql` y la Sección 4 (`ErrorCode`), ya que se gestionan en el filtro de contexto, la capa de aplicación, la persistencia y `GlobalExceptionHandler`.
2. **Agrupación por Recurso**:
    * Si `Ruta Base REST` ya incluye el segmento del recurso (ej. `/api/v1/<recurso-plural>`), el controlador se mapea a `@RequestMapping("<RutaBaseREST>")` y las subrutas del método son relativas a ella (p. ej. `""`, `"/{id}"`, `"/{id}/<accion>"`). Si `Ruta Base REST` es un prefijo general, `Ruta relativa` = Ruta de la operación menos `Ruta Base REST`, y `Recurso` = primer segmento de la ruta; todas las operaciones del mismo recurso van al mismo `<RecursoSingular>Controller` (ej. `/users` -> `UserController` con `@RequestMapping("<RutaBase>/users")`).
3. **Regla de Invocación del Caso de Uso (Cero `null`s artificiales)**:
    * **Con cuerpo (`*HttpRequest`)**: `return <op>UseCase.execute(request.toCommand(<args>));` poblando `<args>` con los `@PathVariable` y `@RequestHeader` que pida la firma de `toCommand(...)` declarada en `DTO de Entrada Web` (por nombre, o por orden y tipo compatible).
    * **Sin cuerpo (`*Command` o `*Query`)**: `return <op>UseCase.execute(new <Op>Command|Query(<args>));` casando primero por nombre los `@RequestParam` y `@RequestHeader`, y los restantes por orden con los `@PathVariable` de tipo compatible.
    * **Sin entrada**: `return <op>UseCase.execute();`.
4. **Restricciones de Pureza (`INP-01`, `INP-02`, `INP-03`)**:
    * **PROHIBIDO** `@Transactional`, bloques `try/catch`, chequeos de roles/seguridad, `ResponseEntity` (salvo cabeceras de respuesta dinámicas), MapStruct o importar `..domain.model..` (salvo enums en `..domain.model.enums..` autorizados por `ADR_005`). Cero comentarios o bloques Javadoc. Imports explícitos sin `*`.

## 3. TABLA DE TRADUCCIÓN DETERMINISTA (API.MD -> SPRING MVC)

| Elemento en `API.md` | Traducción en `<Recurso>Controller` |
| :--- | :--- |
| Verbo HTTP + subruta (`Canal de Entrada`) | `@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping`, `@DeleteMapping` (sin path si es raíz del recurso, o `("/{id}")`, `("/{id}/<accion>")`). |
| `{placeholder}` / `Parámetro de Ruta` / `IN (Path)` | `@PathVariable <Tipo> <placeholder>` (nombre literal de la ruta; tipo tomado de `toCommand` o del `*Command`/`*Query`). |
| Cabecera de operación (no `ApiHeaders`) | `@RequestHeader(value = "<Header>", required = <true\|false>) <Tipo> <camelCaseName>` (`UUID` o `String`). |
| `Parámetros QRY-xx` / `Query Params` | `@RequestParam` con el tipo del campo homónimo en `*Query` (opcional -> `required = false`; `por defecto N` / `default N` -> `defaultValue = "N"`; `máx N`/`mín N` -> `@Max(N)`/`@Min(N)`). |
| Cuerpo (`DTO de Entrada Web` / `IN (Request)`) | `@Valid @RequestBody <Operacion>HttpRequest request` |
| `HTTP Status: 201 CREATED` | `@ResponseStatus(HttpStatus.CREATED)` retornando directamente `*Result` (`INP-05`). |
| `HTTP Status: 204 NO CONTENT` | `@ResponseStatus(HttpStatus.NO_CONTENT)` con retorno `void`. |
| `HTTP Status: 200 OK` | Sin `@ResponseStatus`, retornando directamente `*Result`, `PageResult<T>` o `CursorResult<T>`. |

## 4. PLANTILLA CANÓNICA DE CÓDIGO

```java
package <base>.authentication.infrastructure.adapter.in.web;

import <base>.authentication.application.port.in.ListUsersByStatusUseCase;
import <base>.authentication.application.port.in.RegisterUserUseCase;
import <base>.authentication.application.query.ListUsersByStatusQuery;
import <base>.authentication.application.result.ListUsersByStatusItemResult;
import <base>.authentication.application.result.RegisterUserResult;
import <base>.authentication.domain.model.enums.UserStatus;
import <base>.authentication.infrastructure.adapter.in.web.dto.RegisterUserHttpRequest;
import <base>.shared.application.result.CursorResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/users")
@RequiredArgsConstructor
public class UserController {

    private final RegisterUserUseCase registerUserUseCase;
    private final ListUsersByStatusUseCase listUsersByStatusUseCase;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterUserResult registerUser(
            @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey,
            @Valid @RequestBody RegisterUserHttpRequest request
    ) {
        return registerUserUseCase.execute(request.toCommand(idempotencyKey));
    }

    @GetMapping
    public CursorResult<ListUsersByStatusItemResult> listUsersByStatus(
            @RequestParam UserStatus status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Max(100) int limit
    ) {
        return listUsersByStatusUseCase.execute(new ListUsersByStatusQuery(status, cursor, limit));
    }
}
```

## 5. VERIFICACIÓN OBLIGATORIA (GATE)

Ejecutar en terminal antes de finalizar:
1. `./gradlew compileJava compileTestJava`
2. `./gradlew test --tests "*WebRulesArchTest*" --tests "*ModulithStructureTest*"` (y `--tests "*<Recurso>ControllerTest*"` si ya existe).
3. Emitir informe final conciso:
    * **Generadas**: `ID` -> `Verbo Ruta` (`Controller.metodo`).
    * **Omitidas (no HTTP)** / **Fuera de alcance** / **Discrepancias de cabeceras o parámetros**.
    * **Recordatorio `TST-04`**: Queda pendiente generar `<Recurso>ControllerTest` (`@WebMvcTest`).
