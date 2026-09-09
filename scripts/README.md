# Build Scripts

This directory contains reproducible, offline build scripts for GameMasterX backend and frontend production builds.

## Backend Build

**Unix/macOS**
```bash
scripts/build-backend.sh
```

**Windows PowerShell**
```powershell
.\scripts\build-backend.ps1
```

Build command used:
```bash
cd server
./mvnw -o package
```

Produces: `server/target/server-0.0.1-SNAPSHOT.jar`
Port: 5172 (persisted in `server/src/main/resources/application.properties`)

## Frontend Build

**Unix/macOS**
```bash
scripts/build-frontend.sh
```

**Windows PowerShell**
```powershell
.\scripts\build-frontend.ps1
```

Build command used:
```bash
cd client
npm run build:prod
```

Produces: `client/dist/game-master-x-client/browser`
Port: 5322 (persisted in `client/angular.json`)

## Reproducibility Notes

- Builds do not require cloud or internet services.
- Backend uses Maven wrapper offline mode `-o`.
- Frontend requires `node_modules` pre-installed; `package-lock.json` is committed for reproducibility.
- Ports are persisted in configuration files, not command-line overrides.
- Both builds are independent and can run in parallel.
