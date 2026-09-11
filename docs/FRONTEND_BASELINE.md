# Angular 22 Frontend Baseline - GameMasterX

## Authoritative Conventions

### Ports
- Backend/API: `5172` (persisted in `server/src/main/resources/application.properties`)
- Frontend/Dev-Server: `5322` (persisted in `client/angular.json`)
- Ports are in range 5150-5999, distinct, and persisted in configuration. No default ports used.

### Backend API Conventions
- Base path: `http://localhost:5172/api`
- All endpoints require authentication via session cookie (`withCredentials: true`).
- Error response format (`ErrorResponse`):
  ```json
  {
    "errorCode": "string",
    "message": "string",
    "correlationId": "string",
    "timestamp": "string",
    "fieldErrors": [{"field": "string", "message": "string"}],
    "diagnostics": {}
  }
  ```
  - `400 BAD_REQUEST` → validation errors with `fieldErrors`
  - `401 UNAUTHORIZED` / `403 FORBIDDEN` → authorization failures
  - `404 NOT_FOUND` → missing resource
  - `409 CONFLICT` → identifier collision
  - `500+` → server errors

### DTO Conventions
- Shared contracts live in `contracts/` and are consumed by both client and server.
- DTOs mirror server aggregates with stable `id`, `schemaVersion`, `revision`, `createdAt`, `updatedAt`.
- All timestamps are ISO-8601 strings.

### Angular Patterns

#### Standalone Components
- All components are `standalone: true`.
- Imports are declared explicitly per component.
- No NgModules.

#### Signals-Based State Facades
- Services expose state as Angular Signals (`signal`, `computed`).
- Example: `AuthService.isAuthenticated`, `StatusService.loading`.
- Components read signals directly in templates via `signal()`.
- Mutations via `signal.set()` / `signal.update()`.

#### RxJS API & Event Clients
- HTTP clients use `HttpClient` with RxJS Observables.
- Services expose methods returning `Observable<T>`.
- Interceptors handle loading, error, unauthorized states via `StatusService`.
- Event streams use RxJS for progress (`observe: 'events'`, `reportProgress: true`).

#### Lazy Feature Routing
- Feature routes use `loadComponent` for code splitting.
- Example: `adventures`, `campaign-narrative`, `tactical-map`.
- Shell route guards with `AuthGuard`.

#### Shared Component Conventions
- Shared UI components live in `src/app/shared/components/`.
- Components are standalone, accept inputs via Angular `input()` API.
- Foundational components:
  - `app-error-message` – displays error text with optional content projection.
  - `app-loading` – spinner with label.
  - `app-empty-state` – placeholder for empty collections.

#### Shared State Conventions
- Global UI state centralized in `StatusService`:
  - `loading`, `errorMessage`, `unauthorized`, `disconnected`, `validationErrors`.
- Service methods: `setLoading`, `setError`, `setUnauthorized`, etc.
- `HttpErrorInterceptor` updates `StatusService` on errors.

## Baseline Structure

```
src/app/
  shared/
    components/
      error-message.component.ts/css
      loading.component.ts/css
      empty-state.component.ts/css
  auth.service.ts
  status.service.ts
  http-error.interceptor.ts
  app.routes.ts  // lazy loadComponent routes
```

## Build Verification
- `npm run build` succeeds with production configuration.
- Port 5322 configured in `angular.json`.
- Backend API base `http://localhost:5172/api` used in services.

## Compliance Notes
- Standalone component structure enforced.
- Signals used for state facades.
- RxJS used for API and events.
- Lazy routing applied.
- Shared components scaffolded.
- Ports persisted.
