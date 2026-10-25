<#
.SYNOPSIS
    FProg Standards & Rails - Script de Sincronizacion e Hidratacion (PowerShell)
    Gate 2: Punto de Inflexion (syncStandards)
.DESCRIPTION
    Lee la configuracion bloqueada en docs/02-TECH_STACK.md (fprog_lock), verifica que el
    Gate 2 este en estado LOCKED, y sincroniza de forma inmutable la carpeta .standards/
    (ARCHITECTURE.md, EXCEPTIONS.md, TESTING.md, dag-manifest.yaml, skills/build/) y la
    semilla de codigo (seed/) en la raiz del proyecto.
.PARAMETER LocalFprogRoot
    Ruta opcional al repositorio maestro erft-fprog local para sincronizacion offline/desarrollo.
#>
param(
    [Parameter(Mandatory=$false)]
    [string]$LocalFprogRoot = ""
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ProjectRoot = Split-Path -Parent $ScriptDir
$TechStackFile = Join-Path $ProjectRoot "docs\02-TECH_STACK.md"

if (-not (Test-Path $TechStackFile)) {
    Write-Host "ABORT_MISSING_TECH_STACK: No existe 'docs\02-TECH_STACK.md'. Completa el Paso 2 antes de sincronizar." -ForegroundColor Red
    exit 1
}

$content = [System.IO.File]::ReadAllText($TechStackFile, [System.Text.Encoding]::UTF8)

if ($content -notmatch '\*\*Estado Gate 2\*\*\s*\|\s*`?LOCKED`?') {
    Write-Host "ABORT_UNLOCKED_GATE_2: 'docs\02-TECH_STACK.md' no tiene Estado Gate 2 en 'LOCKED'." -ForegroundColor Red
    Write-Host "Aprueba y bloquea primero el Gate 2 antes de ejecutar sync-standards."
    exit 1
}

function Extract-YamlValue([string]$text, [string]$key) {
    if ($text -match "(?m)^\s*${key}:\s*`"([^`"]+)`"") {
        return $Matches[1].Trim()
    }
    return ""
}

$PresetId   = Extract-YamlValue $content "preset_id"
$PresetRepo = Extract-YamlValue $content "preset_repository"
$PresetVer  = Extract-YamlValue $content "preset_version"

if ([string]::IsNullOrWhiteSpace($PresetId) -or ($PresetId -like "<*")) {
    Write-Host "ABORT_INVALID_LOCK: 'preset_id' no esta definido en el bloque fprog_lock de docs\02-TECH_STACK.md." -ForegroundColor Red
    exit 1
}

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  FProg Gate 2 — Hidratacion de Standards y Semilla     " -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "Preset ID:      $PresetId" -ForegroundColor Green
Write-Host "Preset Version: $PresetVer" -ForegroundColor Green
Write-Host "Repositorio:    $PresetRepo" -ForegroundColor Green
Write-Host ""

$TempCloneDir = $null
$SourcePresetDir = $null

try {
    if (-not [string]::IsNullOrWhiteSpace($LocalFprogRoot)) {
        $Candidate = Join-Path $LocalFprogRoot "presets\$PresetId"
        if (Test-Path $Candidate) {
            $SourcePresetDir = $Candidate
            Write-Host "[INFO] Usando preset local desde '$SourcePresetDir'..." -ForegroundColor Yellow
        }
    }

    if (-not $SourcePresetDir) {
        $TempCloneDir = Join-Path ([System.IO.Path]::GetTempPath()) ("fprog-preset-" + [System.Guid]::NewGuid().ToString("N"))
        Write-Host "[INFO] Clonando $PresetRepo (@$PresetVer)..." -ForegroundColor Cyan
        git clone --depth 1 --branch $PresetVer $PresetRepo $TempCloneDir 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            git clone --depth 1 $PresetRepo $TempCloneDir 2>&1 | Out-Null
        }
        $SourcePresetDir = $TempCloneDir
    }

    # 1. Sincronizar standards/ -> .standards/
    $SourceStandards = Join-Path $SourcePresetDir "standards"
    $TargetStandards = Join-Path $ProjectRoot ".standards"
    if (Test-Path $SourceStandards) {
        New-Item -ItemType Directory -Path $TargetStandards -Force | Out-Null
        Copy-Item -Path (Join-Path $SourceStandards "*") -Destination $TargetStandards -Recurse -Force
        Write-Host "[OK] Normativas (.standards/) sincronizadas." -ForegroundColor Green
    } else {
        Write-Host "Error: No se encontro la carpeta 'standards/' en el preset '$PresetId'." -ForegroundColor Red
        exit 1
    }

    # 2. Hidratar seed/ -> raiz del proyecto (si existe en el preset)
    $SourceSeed = Join-Path $SourcePresetDir "seed"
    if (Test-Path $SourceSeed) {
        Copy-Item -Path (Join-Path $SourceSeed "*") -Destination $ProjectRoot -Recurse -Force
        Write-Host "[OK] Semilla de codigo (seed/) hidratada en la raiz del proyecto." -ForegroundColor Green
    } else {
        Write-Host "[AVISO] El preset no incluye carpeta 'seed/' fisica aun; se sincronizaron exclusivamente los estandares." -ForegroundColor Yellow
    }

    Write-Host ""
    Write-Host "[OK] Gate 2 completado. Ya puedes avanzar al Paso 3 (docs\03-SUBDOMAINS.md con spec-03-subdomains.md)." -ForegroundColor Cyan
}
finally {
    if ($TempCloneDir -and (Test-Path $TempCloneDir)) {
        Remove-Item -Path $TempCloneDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}
