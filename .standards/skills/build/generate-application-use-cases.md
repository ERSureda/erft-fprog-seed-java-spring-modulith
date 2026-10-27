---
name: generate-application-use-cases
description: >-
  Genera exclusivamente los puertos primarios de entrada (*UseCase) de un Bounded Context
  en Java 21 como interfaces con un único método execute(...) dentro de application/port/in/,
  a partir de docs/modules/<module_id>/API.md y ensamblando los *Command,
  *Query y *Result ya existentes.
---

# Skill: Generación de Puertos de Entrada (`generate-application-use-cases`)

## 1. ENTRADAS Y SALIDAS (I/O)

* **Lectura obligatoria**:
    1. `docs/02-TECH_STACK.md` -> Extraer `base_package` (`<base>`).
    2. `docs/modules/<module_id>/API.md`: `Ruta Base REST` (o `Base Path REST`), tabla Sección 1 (`ID`, `Caso de Uso (*UseCase) y Comando (*Command)`, `Canal de Entrada (REST | ASYNC_WORKER | INTERNAL_PORT)`, `Tipo (ADR-006)`, `HTTP Status (@ResponseStatus)`), tabla Sección 2 (`ID`, `Caso de Uso (*UseCase) y Consulta (*Query)`, `Canal de Entrada (REST | INTERNAL_PORT)`, `Contenedor de Salida (*Result | PageResult | CursorResult)`, `HTTP Status`) y bloques de detalle `3.X` / `3.4` (`DTO de Salida Aplicación/Web`, `DTO de Salida Paginado`, `Parámetros QRY-xx`, filas `OUT (Result)` y `DTO Objetivo (*Result)`).
    3. Clases existentes en `<base>.<module_id>.application`: `command/*Command`, `query/*Query` y `result/*Result`.
* **Política de Aborto Inmediato (`Fail-Fast`)**:
    * Abortar listando **todas** las carencias en una sola respuesta si:
        * `API.md` no existe, contiene marcadores de plantilla (`[...]`, `<...>`) o carece del bloque `3.X` para alguna operación en alcance.
        * Falta `package-info.java` (`generate-module-structure`), falta el `<Operacion>Command` (`generate-application-commands`), falta el `<Operacion>Query` cuando la consulta lleva parámetros (`generate-application-queries`), o falta el `*Result` correspondiente cuando la operación declara salida (`generate-application-results`).
        * La consulta no declara ningún parámetro pero existe un `<Operacion>Query` huérfano, o si ya existe un `<Operacion>UseCase` cuya firma difiere de la especificación.
* **Escritura exclusiva (`Blast Radius = 0`)**:
    * `src/main/java/<base>/<module_id>/application/port/in/<Operacion>UseCase.java`
    * **PROHIBIDO** crear o modificar `*Command`, `*Query`, `*Result`, `*Service`, controladores o tests. Si el `*UseCase` ya existe y su firma coincide, dejarlo intacto.

## 2. REGLAS INMUTABLES (`APP-01`, `APP-03`, `APP-04`, `ARC-01`)

1. **Alcance**: Procesar todas las filas de las secciones 1 y 2 que declaren un `<Operacion>UseCase` (o un `*Command` / `*Query`) en la columna `Caso de Uso (*UseCase) y Comando/Consulta` (o solo los IDs indicados por el usuario). Omitir filas cuyo contrato declare exclusivamente un puerto `*Port` sin `*UseCase` (ej. `VerifyActiveUserPort`), reportándolas como omitidas en el informe final.
2. **Nombre de la Interfaz**: Tomar directamente el nombre `<Operacion>UseCase` declarado en la columna `Caso de Uso (*UseCase) y Comando (*Command)` / `Caso de Uso (*UseCase) y Consulta (*Query)` (o reemplazar el sufijo `Command` / `Query` por `UseCase` si solo figura uno de ellos, ej. `RegisterUserCommand` -> `RegisterUserUseCase`).
3. **Determinación del Parámetro de `execute(...)`**:
    * Operación de escritura (`*Command`) ➔ `<Operacion>Command command`
    * Consulta (`*Query`) con parámetros (`Parámetros QRY-xx`, `Parámetro de Ruta`, cabeceras o query params) ➔ `<Operacion>Query query`
    * Consulta sin parámetros ➔ `execute()` (sin argumentos).
4. **Pureza de Interfaz (`APP-01`, `APP-03`, `APP-04`)**: Interfaz Java 21 pública con **un único método `execute`**. **PROHIBIDO** incluir métodos `default`, constantes, anotaciones de Spring (`@Transactional`), excepciones en `throws`, DTOs web (`*HttpRequest`) o entidades/agregados de dominio (`APP-03`, `APP-04`). Cero comentarios o bloques Javadoc. Imports explícitos sin `*`.

## 3. TABLA DETERMINISTA DE RETORNO Y PARÁMETROS (API.MD -> USE CASE)

| Salida Declarada en `API.md` (Sección 2 y Bloques `3.X` / `3.4`) | Tipo de Retorno en `execute(...)` |
| :--- | :--- |
| `HTTP Status: 204` o `N/A (ACK)` sin `DTO de Salida Aplicación/Web` ni filas `OUT (Result)` | `void` |
| Objeto único (`DTO de Salida Aplicación/Web`, `Contenedor de Salida: <Nombre>Result` o filas `OUT (Result)`) | El `<Nombre>Result` explícito declarado en `API.md` (ej. `<Entidad>Result`, `<Entidad>DetailResult`, `<Entidad>StatusResult`), o fallback `<Recurso>Result` / `<Operacion>Result`. |
| Paginación por cursor (`Contenedor de Salida` / `DTO de Salida Paginado: CursorResult<<Item>Result>`) | `CursorResult<<Item>Result>` (o fallback `<Operacion>ItemResult` / `<Recurso>Result`) de `<base>.shared.application.result.CursorResult`. |
| Paginación por offset (`Contenedor de Salida` / `DTO de Salida Paginado: PageResult<<Item>Result>`) | `PageResult<<Item>Result>` (o fallback `<Operacion>ItemResult` / `<Recurso>Result`) de `<base>.shared.application.result.PageResult`. |

## 4. PLANTILLAS CANÓNICAS DE CÓDIGO

### 4.1. Escritura con retorno (`*Result`) y sin retorno (`void`)

* **Operación con retorno (`*Result`)**:

```java
package <base>.<module_id>.application.port.in;

import <base>.<module_id>.application.command.RegisterUserCommand;
import <base>.<module_id>.application.result.RegisterUserResult;

public interface RegisterUserUseCase {

    RegisterUserResult execute(RegisterUserCommand command);
}
```

* **Operación sin retorno (`void`)**:

```java
package <base>.<module_id>.application.port.in;

import <base>.<module_id>.application.command.ProcessOrderPaymentCommand;

public interface ProcessOrderPaymentUseCase {

    void execute(ProcessOrderPaymentCommand command);
}
```

### 4.2. Lectura paginada (`CursorResult` / `PageResult`)

```java
package <base>.<module_id>.application.port.in;

import <base>.<module_id>.application.query.ListUsersByStatusQuery;
import <base>.<module_id>.application.result.ListUsersByStatusItemResult;
import <base>.shared.application.result.CursorResult;

public interface ListUsersByStatusUseCase {

    CursorResult<ListUsersByStatusItemResult> execute(ListUsersByStatusQuery query);
}
```

## 5. VERIFICACIÓN OBLIGATORIA (GATE)

Ejecutar en terminal antes de finalizar:
1. `./gradlew compileJava compileTestJava`
2. `./gradlew test --tests "*ModulithStructureTest*" --tests "*ApplicationRulesArchTest*"`
3. Emitir informe final conciso:
    * **Generados**: `ID` -> firma completa de `<Operacion>UseCase.execute(...)`.
    * **Intactos (preexistentes)** / **Omitidos (`*Port`)** / **Fuera de alcance**.
    * **Recordatorio `APP-01`**: Queda pendiente generar `<Operacion>Service` en `application/service/`.
