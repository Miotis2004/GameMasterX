# Backend Authorization Policy

GameMasterX enforces role-based authorization **server-side** for every
campaign and membership operation. Authorization is not merely a client-side
concern: each state-changing endpoint is guarded by an authentication context
established by the `AuthFilter` and a per-operation role check backed by the
`MembershipRole` hierarchy.

## Roles and hierarchy

Roles follow a linear hierarchy (highest to lowest authority):

`OWNER` > `GAME_MASTER` > `PLAYER` > `OBSERVER`

A role is authorized for an action when its level is greater than or equal to
the level of the required role. Authority flows downward: an `OWNER` is
implicitly a `GAME_MASTER`, a `PLAYER`, and an `OBSERVER`. This hierarchy is
defined by `MembershipRole` and enforced by `Membership.isAuthorizedFor`.

## How authorization is enforced

1. **Authentication context.** The `AuthFilter` (registration order 2)
   establishes the authenticated user id from the session under
   `AuthFilter.AUTH_USER_ATTR`. State-changing HTTP methods (`POST`, `PUT`,
   `DELETE`) require an authenticated session; unauthenticated requests to
   those methods receive a consistent `401 UNAUTHORIZED` response.

2. **Per-operation role check.** Each campaign/membership operation declares
   the minimum role it requires and enforces it through
   `MembershipService.assertAuthorized(campaignId, actor, requiredRole)`, which
   consults the user's membership and throws an `AuthorizationException` when
   the caller is missing or does not hold the required role.

3. **Consistent error response.** `AuthorizationException` is mapped by the
   `GlobalExceptionHandler` to a `403 Forbidden` response in the shared
   `ErrorResponse` format with error code `FORBIDDEN`. Genuine "resource not
   found" problems continue to map to `404 NOT_FOUND`, and input validation to
   `400 BAD_REQUEST`, so the error code unambiguously distinguishes an
   authorization denial from other failures.

## Per-operation role requirements

### Campaign operations (`/api/campaigns`)

| Method | Path | Required role | Enforcement |
| ------ | ---- | ------------- | ----------- |
| POST   | `/api/campaigns` | Authenticated (creator becomes `OWNER`) | `CampaignService.createCampaign` grants an `OWNER` membership to the creator. |
| GET    | `/api/campaigns` | Authenticated | `CampaignController.listCampaigns` requires an authenticated actor. |
| GET    | `/api/campaigns/{id}` | `OBSERVER` (membership) | `CampaignService.findById` requires membership in the campaign. |
| PUT    | `/api/campaigns/{id}` | `GAME_MASTER` | `CampaignService.updateCampaign`. |
| POST   | `/api/campaigns/{id}/archive` | `GAME_MASTER` | `CampaignService.archiveCampaign`. |

### Membership operations (`/api/campaigns/{id}/members`)

| Method | Path | Required role | Enforcement |
| ------ | ---- | ------------- | ----------- |
| POST   | `/{id}/members` | `GAME_MASTER` | `MembershipController.addMember` |
| GET    | `/{id}/members` | `OBSERVER` (membership) | `MembershipController.listMembers` |
| GET    | `/{id}/members/{userId}/role` | `OBSERVER` (membership) | `MembershipController.getRole` |
| GET    | `/{id}/members/{userId}/authorize` | Authenticated | `MembershipController.authorize` |
| GET    | `/members/{userId}/role` | Authenticated | `MembershipController.getHighestRole` |
| PUT    | `/{id}/members/{userId}/role` | `GAME_MASTER` for others; own-role rule for self | `MembershipService.updateRole` / `enforceRoleChangeAuthority` |
| POST   | `/{id}/members/{userId}/accept` | Caller must be the member | `MembershipService.acceptMembership` |
| POST   | `/{id}/members/{userId}/revoke` | Caller must be the member | `MembershipService.revokeMembership` |
| DELETE | `/{id}/members/{userId}` | `GAME_MASTER` | `MembershipService.removeMember` |

### Invites and join codes (`/api/invites`)

| Method | Path | Required role |
| ------ | ---- | ------------- |
| POST   | `/``{campaignId}`/`invites` | `GAME_MASTER` |
| POST   | `/``{campaignId}`/`join-codes` | `GAME_MASTER` |
| POST   | `/``{code}`/`redeem` | Caller (own code) |
| POST   | `/join-codes/``{code}`/`redeem` | Caller (own code) |
| DELETE | `/``{code}``?campaignId=``{id}` | `GAME_MASTER` |

## Ownership

Creating a campaign (`POST /api/campaigns`) grants the creator an `OWNER`
membership via `MembershipService.grantOwner`, so that all later operations on
that campaign can be authorized against the membership hierarchy. The creator is
the only user who can be an `OWNER`, and an `OWNER` holds every role, so an
`OWNER` is authorized for every campaign operation.

## Ports

The backend API serving these endpoints runs on port **5172**, persisted in
`server/src/main/resources/application.properties` (`server.port=5172`). The
client development server runs on port **5322**, as documented in the repo root
README and architecture docs. No default ports (3000, 4200, 5000, 5173, 8000,
8080, 8081, 8888) are used.
