#!/usr/bin/env bash
set -euo pipefail

# GameMasterX Frontend Production Build Script
# Builds the Angular client independently, offline.
# Requires node_modules present and Angular CLI.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLIENT_DIR="${REPO_ROOT}/client"

cd "$CLIENT_DIR"

# Build production bundle offline (node_modules must be present)
npm run build:prod

echo "Frontend production build complete: ${CLIENT_DIR}/dist"
