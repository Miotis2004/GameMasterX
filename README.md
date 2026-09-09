# GameMasterX Monorepo

## Project Purpose

GameMasterX is a modular game master platform designed to orchestrate gameplay sessions, content management, and client-server interactions for tabletop and digital gaming experiences. The monorepo provides a unified workspace for client applications, backend services, shared contracts, content assets, automation scripts, and documentation.

## Architecture Overview

- **client/**: Frontend application(s) for players and game masters. Development server runs on port 5322.
- **server/**: Backend/API services handling game logic, persistence, and real-time communication. Runs on port 5172.
- **contracts/**: Shared TypeScript interfaces, API schemas, and data contracts consumed by both client and server.
- **content/**: Game assets, scenarios, templates, and static data.
- **scripts/**: Automation and tooling scripts for development, build, and deployment.
- **docs/**: Project documentation, milestone planning, and architecture decisions.

The architecture follows a monorepo pattern with clear separation of concerns, shared contracts for type safety, and isolated build pipelines per workspace.

## Milestone Scope

### Milestone 1: Repository Skeleton & Foundations
- Create monorepo directory structure: client, server, contracts, content, scripts, docs
- Establish root README with purpose, architecture, and milestone definitions
- Define documentation scaffolding for planning and governance
- Reserve application ports: Backend/API 5172, Frontend/Dev 5322
- No application source code in this milestone

### Future Milestones (Planned)
- Milestone 2: Contract definitions and shared types
- Milestone 3: Server scaffolding with API endpoints
- Milestone 4: Client scaffolding with UI framework
- Milestone 5: Content pipeline and asset management
- Milestone 6: Integration and end-to-end testing

## Development Notes

- Backend services must use port 5172
- Frontend/dev-server must use port 5322
- Do not use common/default ports (3000, 4200, 5000, 5173, 8000, 8080, 8081, 8888)
- Ports are persisted in configuration/scripts, not overridden ad-hoc

## Getting Started

```bash
# Directory structure is initialized. Subsequent milestones will populate workspaces.
ls client server contracts content scripts docs
```

See `docs/` for detailed milestone planning and architecture decisions.
