#!/usr/bin/env bash
set -euo pipefail

# GameMasterX Backend Production Build Script
# Builds the Spring Boot Maven project independently, offline.
# Requires Java 21+ and Maven wrapper present in server/.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVER_DIR="${REPO_ROOT}/server"

cd "$SERVER_DIR"

# Use Maven wrapper offline mode to ensure no internet dependency
./mvnw -o -q package

echo "Backend production build complete: ${SERVER_DIR}/target/server-0.0.1-SNAPSHOT.jar"
