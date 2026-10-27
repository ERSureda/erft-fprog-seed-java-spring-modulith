---
name: generate-domain-enums
description: >-
  Genera exclusivamente enums de dominio puros en Java 21 dentro de
  <base>/<module_id>/domain/model/enums/ a partir de los tipos CREATE TYPE <module_id>.<tipo> AS ENUM
  de la migración Flyway o de docs/modules/<module_id>/DOMAIN.md.
---

# Skill: Generación de Enums de Dominio (`generate-domain-enums`)

## 1. ENTRADAS Y SALIDAS (I/O)

* **Lectura obligatoria**:
    1. `docs/02-TECH_STACK.md` -> Extraer `base_package` (`<base>`).
    2. `src/main/resources/db/migration/V<N>__init_<module_id>_tables.sql` y `docs/modules/<module_id>/DOMAIN.md` -> Extraer definiciones `CREATE TYPE <module_id>.<tipo> AS ENUM (...)` y estados FSM.
    3. Directorio `src/main/java/<base>/shared/domain/` -> Verificar si un enum transversal ya viene provisto por la semilla (`seed/`).
* **Política de Aborto Inmediato (`Fail-Fast`)**:
    * Abortar listando **todas** las carencias en una sola respuesta si:
        * `DOMAIN.md` o la migración Flyway del módulo no existen o conservan marcadores de plantilla (`[...]`, `<...>`).
        * Falta `package-info.java` (`generate-module-structure`), existe discrepancia entre los valores `AS ENUM` del DDL y los estados de `DOMAIN.md`, o un enum ya existente en el módulo difiere de la especificación.
* **Escritura exclusiva (`Blast Radius = 0`)**:
    * `src/main/java/<base>/<module_id>/domain/model/enums/<NombreEnum>.java`
    * **PROHIBIDO** escribir o modificar archivos dentro de `shared/` (el Kernel Compartido es inmutable en Fase 2; si un enum ya existe en `shared`, simplemente se reutiliza sin regenerarlo). Si el enum del módulo ya existe y coincide, dejarlo intacto.

## 2. REGLAS INMUTABLES (`DOM-01`, `DOM-03`, `ARC-01`, `ADR_005`)

1. **Pureza Absoluta (`DOM-01`)**: Java 21 puro. **PROHIBIDO** importar Lombok (`@Getter`), Jackson (`@JsonProperty`), JPA (`@Enumerated`) o Spring. Cero comentarios o bloques Javadoc.
2. **Ubicación Exacta (`ARC-01`, `DOM-03`, `ADR_005`)**: Todo enum de estado o clasificación del módulo reside obligatoriamente en `<base>.<module_id>.domain.model.enums`. (Esto permite que los adaptadores web locales lo consuman directamente bajo `ADR_005` pasando `WebRulesArchTest`).
3. **Biyección de Nombres**:
    * Tipo SQL `snake_case` (`invoice_status`) ➔ Clase Java `PascalCase` (`InvoiceStatus`).
    * Valores SQL ➔ Constantes `UPPER_SNAKE_CASE` idénticas (`DRAFT`, `ISSUED`, `PAID`, `CANCELLED`).
4. **Predicados Semánticos Puros (Opcional, `DOM-03`)**: Se permiten métodos booleanos puros sin estado mutable (ej. `isTerminal()`, `isSettled()`) si están respaldados por la máquina de estados del módulo.

## 3. TABLA DE BIYECCIÓN DETERMINISTA (SQL ENUM -> JAVA 21)

| Elemento DDL PostgreSQL 17 | Representación en Java 21 (`domain.model.enums`) | Convención de Mapeo |
| :--- | :--- | :--- |
| `CREATE TYPE <module_id>.<name> AS ENUM` | `public enum <NamePascalCase>` | Conversión estricta de `snake_case` a `PascalCase`. |
| Valores literales (`'DRAFT'`, `'PAID'`) | Constantes del enum (`DRAFT`, `PAID`) | Coincidencia idéntica en `UPPER_SNAKE_CASE`. |
| Estados finales / transiciones FSM | Predicados booleanos (`public boolean isSettled()`) | Métodos puros sin estado mutable ni efectos colaterales. |

## 4. PLANTILLA CANÓNICA DE CÓDIGO

```java
package <base>.<module_id>.domain.model.enums;

public enum InvoiceStatus {

    DRAFT,
    ISSUED,
    PAID,
    CANCELLED,
    OVERDUE;

    public boolean isSettled() {
        return this == PAID;
    }

    public boolean isTerminal() {
        return this == PAID || this == CANCELLED;
    }
}
```

## 5. VERIFICACIÓN OBLIGATORIA (GATE)

Ejecutar en terminal antes de finalizar:
1. `./gradlew compileJava compileTestJava`
2. `./gradlew test --tests "*DomainRulesArchTest*" --tests "*ModulithStructureTest*"`
3. Emitir informe final conciso:
    * **Generados**: `<module_id>.<tipo_sql>` -> `<NombreEnum>(VALORES...)`.
    * **Reutilizados de `shared`** / **Intactos (preexistentes)**.
