# Architecture Overview

## Monorepo Structure

GameMasterX uses a monorepo layout to colocate related workspaces:

- **client/** - Frontend applications
- **server/** - Backend services
- **contracts/** - Shared types and schemas
- **content/** - Static game assets and data
- **scripts/** - Automation tooling
- **docs/** - Documentation

## Design Principles

- Separation of concerns between client and server
- Shared contracts for type safety
- Port isolation: server 5172, client 5322
- No application source code until foundations are established

## Technology Constraints

- Ports must be in range 5150-5999
- No common default ports (3000, 4200, 5000, 5173, 8000, 8080, 8081, 8888)
- Ports persisted in configuration/scripts
