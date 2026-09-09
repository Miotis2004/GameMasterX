# Development Startup Instructions

This document describes how to start MongoDB, the backend API server, and the frontend client for GameMasterX development on Windows, macOS, and Linux.

## Prerequisites

- **Java 21** or later (backend)
- **Maven 3.9+** or use included `mvnw` wrapper
- **Node.js 18+** and npm 12+ (client)
- **Angular CLI 22.1.4** (installed via npm)
- **MongoDB 6+** running locally or accessible

Port assignments (persisted in configuration):
- Backend/API: `5172`
- Frontend/Dev-Server: `5322`

Do not override ports with command-line flags; use the persisted configuration.

## MongoDB

### Windows
1. Install MongoDB Community Edition via MSI installer.
2. Start the MongoDB service:
   ```powershell
   net start MongoDB
   ```
   Or use Services app to ensure `MongoDB Server` is running.
3. Verify with:
   ```powershell
   mongosh
   ```
   Default database is `game-master-x`. The backend expects `mongodb://localhost:27017/game-master-x` unless `MONGO_URI` is set.

### macOS
1. Install via Homebrew:
   ```bash
   brew tap mongodb/brew
   brew install mongodb-community
   brew services start mongodb-community
   ```
2. Verify:
   ```bash
   mongosh
   ```

### Linux (Ubuntu/Debian)
1. Install:
   ```bash
   sudo apt-get update
   sudo apt-get install mongodb
   sudo systemctl enable mongodb
   sudo systemctl start mongodb
   ```
2. Verify:
   ```bash
   mongosh
   ```

## Backend Startup

Backend is a Spring Boot application in `server/`. Port `5172` is configured in `src/main/resources/application.properties`.

### Windows
```powershell
cd server
.\mvnw.cmd spring-boot:run
```
The server starts on `http://localhost:5172`.

### macOS
```bash
cd server
./mvnw spring-boot:run
```
The server starts on `http://localhost:5172`.

### Linux
```bash
cd server
./mvnw spring-boot:run
```
The server starts on `http://localhost:5172`.

Notes:
- Ensure `JAVA_HOME` points to Java 21.
- MongoDB must be running before starting the backend.
- Development CORS allows `http://localhost:5322` with credentials.

## Client Startup

Client is an Angular application in `client/`. Dev server port `5322` is configured in `angular.json`.

### Windows
```powershell
cd client
npm install
npm start
```
Or:
```powershell
ng serve
```
Open `http://localhost:5322`.

### macOS
```bash
cd client
npm install
npm start
```
Or:
```bash
ng serve
```
Open `http://localhost:5322`.

### Linux
```bash
cd client
npm install
npm start
```
Or:
```bash
ng serve
```
Open `http://localhost:5322`.

Notes:
- First run requires `npm install` to install dependencies.
- The client expects the backend at `http://localhost:5172`.
- Do not change the port; configuration persists port `5322`.

## Full Startup Sequence

1. Start MongoDB service for your OS.
2. Start backend: `server/` → `mvnw` spring-boot:run (port 5172).
3. Start client: `client/` → `npm start` (port 5322).

All three components must be running for local development.
