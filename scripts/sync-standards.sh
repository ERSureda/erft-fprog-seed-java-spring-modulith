#!/usr/bin/env bash
# ==============================================================================
# FProg Standards & Rails — Script de Sincronización e Hidratación (Bash)
# Gate 2: Punto de Inflexión (syncStandards)
# ==============================================================================
set -euo pipefail

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
TECH_STACK_FILE="${PROJECT_ROOT}/docs/02-TECH_STACK.md"
LOCAL_FPROG_ROOT="${1:-}"

if [ ! -f "$TECH_STACK_FILE" ]; then
    echo -e "${RED}ABORT_MISSING_TECH_STACK: No existe 'docs/02-TECH_STACK.md'. Completa el Paso 2 antes de sincronizar.${NC}"
    exit 1
fi

if ! grep -Eq '\*\*Estado Gate 2\*\*[[:space:]]*\|[[:space:]]*`?LOCKED`?' "$TECH_STACK_FILE"; then
    echo -e "${RED}ABORT_UNLOCKED_GATE_2: 'docs/02-TECH_STACK.md' no tiene Estado Gate 2 en 'LOCKED'.${NC}"
    echo -e "Aprueba y bloquea primero el Gate 2 antes de ejecutar sync-standards."
    exit 1
fi

extract_yaml_val() {
    local key="$1"
    grep -E "^[[:space:]]*${key}:" "$TECH_STACK_FILE" | head -n 1 | sed -E 's/^[[:space:]]*[^:]+:[[:space:]]*"?([^"]*)"?[[:space:]]*$/\1/'
}

PRESET_ID="$(extract_yaml_val "preset_id")"
PRESET_REPO="$(extract_yaml_val "preset_repository")"
PRESET_VER="$(extract_yaml_val "preset_version")"

if [ -z "$PRESET_ID" ] || [[ "$PRESET_ID" == \<* ]]; then
    echo -e "${RED}ABORT_INVALID_LOCK: 'preset_id' no está definido en el bloque fprog_lock de docs/02-TECH_STACK.md.${NC}"
    exit 1
fi

echo -e "${BLUE}========================================================${NC}"
echo -e "${BLUE}  FProg Gate 2 — Hidratación de Standards y Semilla     ${NC}"
echo -e "${BLUE}========================================================${NC}"
echo -e "Preset ID:      ${GREEN}${PRESET_ID}${NC}"
echo -e "Preset Version: ${GREEN}${PRESET_VER}${NC}"
echo -e "Repositorio:    ${GREEN}${PRESET_REPO}${NC}"
echo ""

TEMP_CLONE_DIR=""
SOURCE_PRESET_DIR=""

cleanup() {
    if [ -n "$TEMP_CLONE_DIR" ] && [ -d "$TEMP_CLONE_DIR" ]; then
        rm -rf "$TEMP_CLONE_DIR"
    fi
}
trap cleanup EXIT

if [ -n "$LOCAL_FPROG_ROOT" ] && [ -d "${LOCAL_FPROG_ROOT}/presets/${PRESET_ID}" ]; then
    SOURCE_PRESET_DIR="${LOCAL_FPROG_ROOT}/presets/${PRESET_ID}"
    echo -e "${YELLOW}[INFO] Usando preset local desde '${SOURCE_PRESET_DIR}'...${NC}"
else
    TEMP_CLONE_DIR="$(mktemp -d)"
    echo -e "${BLUE}[INFO] Clonando ${PRESET_REPO} (@${PRESET_VER})...${NC}"
    git clone --depth 1 --branch "$PRESET_VER" "$PRESET_REPO" "$TEMP_CLONE_DIR" >/dev/null 2>&1 || \
        git clone --depth 1 "$PRESET_REPO" "$TEMP_CLONE_DIR" >/dev/null 2>&1
    SOURCE_PRESET_DIR="$TEMP_CLONE_DIR"
fi

# 1. Sincronizar standards/ -> .standards/
if [ -d "${SOURCE_PRESET_DIR}/standards" ]; then
    mkdir -p "${PROJECT_ROOT}/.standards"
    cp -R "${SOURCE_PRESET_DIR}/standards/." "${PROJECT_ROOT}/.standards/"
    echo -e "${GREEN}✓ Normativas (.standards/) sincronizadas.${NC}"
else
    echo -e "${RED}Error: No se encontró la carpeta 'standards/' en el preset '${PRESET_ID}'.${NC}"
    exit 1
fi

# 2. Hidratar seed/ -> raíz del proyecto
if [ -d "${SOURCE_PRESET_DIR}/seed" ]; then
    cp -R "${SOURCE_PRESET_DIR}/seed/." "${PROJECT_ROOT}/"
    echo -e "${GREEN}✓ Semilla de código (seed/) hidratada en la raíz del proyecto.${NC}"
else
    echo -e "${YELLOW}[AVISO] El preset no incluye carpeta 'seed/' física aún; se sincronizaron exclusivamente los estándares.${NC}"
fi

echo ""
echo -e "${BLUE}✓ Gate 2 completado. Ya puedes avanzar al Paso 3 (docs/03-SUBDOMAINS.md con spec-03-subdomains.md).${NC}"
