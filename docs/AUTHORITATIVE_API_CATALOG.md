# Authoritative API Catalog - GameMasterX

This catalog documents backend APIs discovered by inspecting the repository and exercising the running application. All endpoints are under base path `http://localhost:5172/api`.

## Ports
- Backend/API: 5172 (server/src/main/resources/application.properties)
- Frontend/Dev-Server: 5322 (client/angular.json)

## Authentication
- `POST /api/auth/login` – session cookie authentication, withCredentials true
- `POST /api/auth/logout` – invalidate session

## Campaigns
- `POST /api/campaigns` – create campaign, caller becomes OWNER
- `GET /api/campaigns` – list campaigns visible to caller
- `GET /api/campaigns/{id}` – read campaign, requires OBSERVER+
- `PUT /api/campaigns/{id}` – update campaign, requires GAME_MASTER+
- `POST /api/campaigns/{id}/archive` – archive campaign, requires GAME_MASTER+
- `GET /api/campaigns/{id}/dashboard` – aggregated dashboard, requires OBSERVER+

## Membership
- `GET /api/campaigns/{campaignId}/members` – list members
- `POST /api/campaigns/{campaignId}/members` – invite/join
- `PUT /api/campaigns/{campaignId}/members/{userId}` – change role

## Characters
- `GET /api/characters` – list owned characters
- `POST /api/characters` – create character
- `GET /api/characters/{id}` – read character sheet
- `PUT /api/characters/{id}` – update character

## Adventures
- `GET /api/adventures` – list adventures
- `POST /api/adventures/import` – import adventure package
- `GET /api/adventures/{id}` – read adventure

## Encounters
- `POST /api/encounters` – create encounter
- `GET /api/encounters/{id}` – read encounter

## Tactical Map
- `GET /api/campaigns/{campaignId}/map` – map assets
- `POST /api/map/assets` – upload map asset

## Dice & Gameplay
- `POST /api/dice/roll` – auditable dice roll
- `POST /api/gameplay/ability-check` – ability check
- `POST /api/gameplay/skill-check` – skill check
- `POST /api/gameplay/saving-throw` – saving throw

## AI Operations
- `GET /api/ai/operations/schemas` – operation schemas catalog
- `POST /api/ai/operations/propose` – propose operation

## Error Format
All errors return ErrorResponse with errorCode, message, correlationId, timestamp, fieldErrors, diagnostics. HTTP mappings as documented in AUTHORITATIVE_CONVENTIONS.md.

## DTO Conventions
Contracts in contracts/ with stable id, schemaVersion, revision, createdAt, updatedAt, ISO-8601 timestamps.

## Angular Patterns Confirmed
- Standalone components
- Signals state facades
- RxJS API clients
- Lazy routing via loadComponent
- Shared components: error-message, loading, empty-state
- StatusService central UI state
- HttpErrorInterceptor registered
