# Campaign Dashboard

The campaign dashboard is a single aggregation endpoint that brings together,
for one campaign, the three views a game master needs to open a session: the
campaign's **members and their roles**, the campaign's **characters**, and the
campaign's **selected adventure**. Providing these in one response avoids the
client issuing three sequentially dependent requests.

## Endpoint

| Method | Path | Required role |
| ------ | ---- | ------------- |
| GET    | `/api/campaigns/{id}/dashboard` | `OBSERVER` (membership) |

```
GET /api/campaigns/{campaignId}/dashboard
```

Returns `200 OK` with the dashboard payload, or:

- `401 UNAUTHORIZED` when the caller is not authenticated (state-changing filter
  context; the dashboard resolves the authenticated actor from the session).
- `403 FORBIDDEN` (error code `FORBIDDEN`) when the caller is not a member of
  the campaign or does not hold the minimum role.
- `404 NOT_FOUND` (error code `NOT_FOUND`) when the campaign does not exist.

## Response shape

```json
{
  "campaign": { "id": "...", "name": "...", "adventureId": "..." },
  "actorId": "...",
  "actorRole": "GAME_MASTER",
  "members": [
    { "id": "...", "userId": "...", "role": "OWNER", "status": "ACTIVE" }
  ],
  "characters": [ { "id": "...", "name": "...", "campaignId": "..." } ],
  "selectedAdventure": { "id": "...", "title": "..." } | null
}
```

- `members` — every membership in the campaign projected through
  `MembershipService.listMembers`, each carrying its `role` and `status`.
- `characters` — every character whose `campaignId` matches, projected through
  `CharacterRepository.findByCampaignId`.
- `selectedAdventure` — the authored adventure the campaign has selected,
  projected through `AdventureRepository.findById`. It is `null` when the
  campaign has not selected an adventure, or when the selected adventure has
  since been removed from the store (a dangling reference yields `null` rather
  than an error).
- `actorRole` — the role the authenticated caller holds in the campaign, so the
  UI can render role-aware controls without a separate authorization request.

## Adventure selection

A campaign references at most one adventure through the `adventureId` field on
the Campaign aggregate. Selecting an adventure is a `GAME_MASTER` operation: it
is applied through `PUT /api/campaigns/{id}` with an `adventureId` in the update
body, and `CampaignService` validates that the target adventure exists before
storing the reference, so a campaign never keeps a dangling `adventureId`.

## Authorization

Dashboard access is authorized **server-side per campaign role**, exactly like
the campaign and membership read endpoints:

1. The `AuthFilter` establishes the authenticated actor from the session under
   `AuthFilter.AUTH_USER_ATTR`.
2. `CampaignDashboardService.getCampaignDashboard` calls
   `MembershipService.assertAuthorized(campaignId, actor, OBSERVER)`, which
   consults the caller's membership and throws an
   `AuthorizationException` when the caller is missing or does not hold the
   minimum role.
3. `AuthorizationException` is mapped by the `GlobalExceptionHandler` to a
   `403 FORBIDDEN` response. Missing campaigns map to `404 NOT_FOUND`, so the
   error code unambiguously distinguishes an authorization denial from a
   missing resource.

Because authority flows downward through the `OWNER > GAME_MASTER > PLAYER >
OBSERVER` hierarchy, any member of the campaign (including passive
`OBSERVER`s) can read the dashboard; only users with no membership are denied.

## Ports

The backend API serving these endpoints runs on port **5172**, persisted in
`server/src/main/resources/application.properties` (`server.port=5172`). The
client development server runs on port **5322**, as documented in the repo root
README and architecture docs. No default ports (3000, 4200, 5000, 5173, 8000,
8080, 8081, 8888) are used.
