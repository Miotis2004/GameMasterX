# GameMasterX Backend Production Build Script (PowerShell)
# Builds Spring Boot Maven project independently, offline.

$RepoRoot = Split-Path -Parent $PSScriptRoot
$ServerDir = Join-Path $RepoRoot "server"
Set-Location $ServerDir

# Use Maven wrapper offline mode
& ".\mvnw.cmd" -o -q package

Write-Host "Backend production build complete: $ServerDir\target\server-0.0.1-SNAPSHOT.jar"
