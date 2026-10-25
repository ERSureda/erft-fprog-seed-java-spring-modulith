# ADR_Template: [Título Breve de la Decisión]

```yaml
id: ADR_XXX
title: "[Título Breve de la Decisión]"
status: PROPOSED # PROPOSED | ACCEPTED | DEPRECATED | SUPERSEDED
date: YYYY-MM-DD
preset: java-spring-modulith
rules: [REG-01, REG-02] # Reglas normativas vinculadas en AI_SPEC / ARCHITECTURE
scope: "**/ruta/afectada/**" # Patrón glob de paquetes o archivos
guardians:
  - ArchTestClassName.ruleMethodName # Test automatizado que audita la decisión
  - IntegrationTestClassName
```

## 1. Problem Statement
[Descripción sintética en 1 o 2 frases del conflicto de diseño, limitación técnica o disyuntiva que motiva esta decisión. Cero narrativa prescindible.]

## 2. Decision Matrix
| Directiva | Regla | Especificación Técnica |
| :--- | :---: | :--- |
| **`MUST`** | `REG-01` | [Obligación técnica estricta y verificable.] |
| **`NEVER`** | `REG-02` | [Prohibición taxativa. Patrón o import vetado.] |
| **`ALLOWED`** | `REG-03` | [Excepción o patrón autorizado bajo condiciones específicas.] |

## 3. Canonical Architecture
```text
[Flujo / Topología en árbol simple de texto, sin cajas decorativas pesadas]
Capa / Componente A ──► Capa / Componente B
                        ├── Elemento 1
                        └── Elemento 2
```

## 4. Trade-Offs & Mitigations
* **[Compromiso Asumido]:** [Efecto colateral aceptado].
  * *Mitigación:* [Mecanismo técnico o automatización que amortigua el coste].
