# Campaign Membership, Acceptance, Revocation, and Roles

This document describes the membership lifecycle for a campaign in the
GameMasterX backend, including how a member accepts an invite, revokes their
own membership, and how authorized roles change another member's role. It
complements [`CAMPAIGN_INVITES.md`](./CAMPAIGN_INVITES.md), which covers the
invite and join-code mechanics that create memberships.

## Overview

A `Membership` links a user to a campaign with a specific
[`MembershipRole`](../server/src/main/java/com/gamemasterx/server/campaign/membership/model/MembershipRole.java)
and a [`MembershipStatus`](../server/src/main/java/com/gamemasterx/server/campaign/membership/model/MembershipStatus.java):

- `ACTIVE` - the member is fully admitted.
- `PENDING` - the member redeemed a join code and awaits approval.
- `REVOKED` - the membership has been revoked.

Roles follow the hierarchy (highest to lowest): `OWNER`, `GAME_MASTER`,
`PLAYER`, `OBSERVER`. Authority flows downward, so a higher role authorizes
every action permitted to the roles below it.

## Endpoints

All membership endpoints live under `/api/campaigns`. State-changing endpoints
require an authenticated session (enforced by the server auth filter) and are
authorized server-side by [`MembershipService`](
../server/src/main/java/com/gamemasterx/server/campaign/membership/service/MembershipService.java).

| Method | Path | Description |
| ------ | ---- | ----------- |
| POST   | `/api/campaigns/{id}/members/{userId}/accept` | Accept your own pending membership (PENDING -> ACTIVE). |
| POST   | `/api/campaigns/{id}/members/{userId}/revoke` | Revoke your own membership (-> REVOKED). |
| PUT    | `/api/campaigns/{id}/members/{userId}/role?role=...` | Change a member's role (requires GAME_MASTER authority for others). |
| DELETE | `/api/campaigns/{id}/members/{userId}` | Remove a member (requires GAME_MASTER authority). |
| POST   | `/api/campaigns/{id}/members` | Add a user with a role. |
| GET    | `/api/campaigns/{id}/members` | List a campaign's members. |

## Accepting an invite

When a join code is redeemed it creates a `PENDING` membership. The joiner then
accepts it through their own authenticated session:

```
POST /api/campaigns/{campaignId}/members/{userId}/accept
```

- Only the member themselves may accept their own pending membership; the
  caller's authenticated user id must match `userId`.
- Only a `PENDING` membership can be accepted; an `ACTIVE` membership returns a
  `404`.
- Acceptance transitions the membership to `ACTIVE`, recording a bumped
  revision and an updated `updatedAt` timestamp.

## Revoking your own membership

A member may leave a campaign by revoking their own membership:

```
POST /api/campaigns/{campaignId}/members/{userId}/revoke
```

- Only the member themselves may revoke their own membership; the caller's
  authenticated user id must match `userId`.
- A membership that is already `REVOKED` returns a `404`.
- Revocation transitions the membership to `REVOKED`, recording a bumped
  revision and an updated `updatedAt` timestamp.

A member cannot revoke another member's membership through this endpoint; use
`DELETE /api/campaigns/{id}/members/{userId}` instead (requires
`GAME_MASTER` authority).

## Changing a member's role

```
PUT /api/campaigns/{id}/members/{userId}/role?role=PLAYER
```

Authorization rules:

- Changing **another** member's role requires the caller to hold at least the
  `GAME_MASTER` role in the campaign.
- Changing **your own** role is permitted only when the new role does not grant
  more authority than the role you currently hold (a member cannot elevate
  themselves).

Role changes record a bumped revision and an updated `updatedAt` timestamp.

## Revisions and timestamps

Every membership document carries a monotonic `revision` counter (managed as an
optimistic-concurrency `@Version` on the MongoDB document) and `createdAt` /
`updatedAt` timestamps. Acceptance, revocation, role changes, and membership
creation all bump the revision and refresh `updatedAt`, so the full audit trail
of a membership's state changes is preserved in the persisted document.

## Ports

The backend API serving these endpoints runs on port **5172**, persisted in
`server/src/main/resources/application.properties` (`server.port=5172`). The
client development server runs on port **5322**, as documented in the repo root
README and architecture docs. No default ports (3000, 4200, 5000, 5173, 8000,
8080, 8081, 8888) are used.
