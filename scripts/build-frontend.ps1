# GameMasterX Frontend Production Build Script (PowerShell)
# Builds Angular client independently, offline.

$RepoRoot = Split-Path -Parent $PSScriptRoot
$ClientDir = Join-Path $RepoRoot "client"
Set-Location $ClientDir

npm run build:prod

Write-Host "Frontend production build complete: $ClientDir\dist"
