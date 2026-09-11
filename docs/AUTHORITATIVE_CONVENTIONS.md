# Authoritative Conventions - GameMasterX

## Argus Assigned Ports
- Backend/API: `5172`
- Frontend/Dev-Server: `5322`
Persisted in:
- `server/src/main/resources/application.properties` → `server.port=5172`
- `client/angular.json` → `serve.options.port=5322`
Range: 5150-5999. No default ports used.

## Backend API Conventions
Base URL: `http://localhost:5172/api`
Authentication: Session cookie, `withCredentials: true`.
CORS allows `http://localhost:5322` with credentials.

### Error Format
Server returns `ErrorResponse`:
- `errorCode`: string (e.g., VALIDATION_ERROR, CONFLICT, NOT_FOUND, FORBIDDEN)
- `message`: human-readable
- `correlationId`: request correlation
- `timestamp`: ISO-8601
- `fieldErrors`: array of `{field, message}`
- `diagnostics`: optional map

HTTP mappings:
- 400 → validation, fieldErrors populated
- 401/403 → authorization
- 404 → not found
- 409 → conflict
- 500+ → server error

### DTO Conventions
Contracts in `contracts/`:
- Stable `id`, `schemaVersion`, `revision`, `createdAt`, `updatedAt`
- ISO-8601 timestamps
- TypeScript interfaces mirror server models
- Example: `AdventureResult`, `AdventureCreation`

## Angular Frontend Patterns
- Angular 22.1.4, standalone components only
- Signals for state facades (`signal`, `computed`)
- RxJS for HTTP and events
- Lazy routing via `loadComponent`
- `StatusService` central UI state
- `HttpErrorInterceptor` maps errors to `StatusService`
- Shared components in `src/app/shared/components/`
- Error, loading, empty-state components scaffolded

## Routing Conventions
- Shell route with `AuthGuard`
- Lazy-loaded feature components
- Redirects to `dashboard` by default, `login` for unauthenticated

## API Client Conventions
- `private readonly apiBase = 'http://localhost:5172/api'`
- Use `HttpClient` with Observables
- Progress events for uploads
- Error handling via interceptor + `StatusService`

## Validation & Security
- Backend owns validation; client surfaces field errors
- Role hierarchy: OWNER > GAME_MASTER > PLAYER > OBSERVER
- Authorization enforced server-side only
