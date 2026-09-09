# Campaign Invites and Join Codes

This document describes the invite and local join-code feature for campaign
membership in the GameMasterX backend.

## Overview

Campaign membership is granted through two closely related mechanisms, both
scoped to a specific campaign and role:

- **Invites** - targeted, single-use invites issued by a campaign owner or game
  master. Redeeming an invite creates an **active** membership with the role
  granted at issue time.
- **Local join codes** - open, short-lived codes generated for a campaign.
  Redeeming a join code creates a **pending** membership that awaiting approval
  before the joiner is fully admitted.

Both mechanisms are backed by the same `CampaignInvite` aggregate, which maps a
single-use secret code to a campaign and role.

## Roles and authority

Roles follow the hierarchy defined by `MembershipRole` (highest to lowest):
`OWNER`, `GAME_MASTER`, `PLAYER`, `OBSERVER`. Issuing an invite or generating a
join code requires the actor to hold **at least** the `GAME_MASTER` role in the
target campaign.

## Data model

- `campaign_invites` collection holds `CampaignInvite` documents. Each carries
  a unique `inviteCode`, the `campaignId`, the `role` granted, the `issuedBy`
  user, a `redeemMode` (`DIRECT` or `PENDING`), a `status`
  (`PENDING` / `USED` / `REVOKED` / `EXPIRED`), an optional `expiresAt`, and
  timestamps.
- `campaign_memberships` collection holds `Membership` documents, now with a
  `status` (`ACTIVE` / `PENDING` / `REVOKED`).

## API endpoints

All endpoints live under `/api/invites`. State-changing endpoints require an
authenticated session (enforced by the server auth filter).

| Method | Path | Description |
| ------ | ---- | ----------- |
| POST   | `/api/invites/{campaignId}/invites` | Issue a targeted invite. |
| POST   | `/api/invites/{campaignId}/join-codes` | Generate a local join code. |
| GET    | `/api/invites/campaigns/{campaignId}` | List invites for a campaign. |
| GET    | `/api/invites/codes/{code}` | View a single invite. |
| POST   | `/api/invites/{code}/redeem` | Redeem an invite (active membership). |
| POST   | `/api/invites/join-codes/{code}/redeem` | Redeem a join code (pending membership). |
| DELETE | `/api/invites/{code}?campaignId={id}` | Revoke a pending invite. |

### Issue an invite

```
POST /api/invites/{campaignId}/invites
{ "role": "PLAYER", "expiresAfterSeconds": 604800 }
```

### Generate a local join code

```
POST /api/invites/{campaignId}/join-codes
{ "role": "PLAYER" }
```

### Redeem an invite (active membership)

```
POST /api/invites/{code}/redeem
{ "userId": "<joiner user id>" }
```

### Redeem a join code (pending membership)

```
POST /api/invites/join-codes/{code}/redeem
{ "userId": "<joiner user id>" }
```

## Behaviour

- **Scope.** Every invite and join code is bound to the campaign and role
  supplied at issue time. The granted role cannot change afterward.
- **Single use.** Once redeemed, an invite is marked `USED` and can no longer be
  used.
- **Expiry.** Invites never expire unless a positive `expiresAfterSeconds` is
  supplied. Join codes use a default 24-hour lifetime unless overridden. An
  expired invite is reported as unusable.
- **Redemption creates membership.** Direct redemption creates an active
  membership; pending redemption creates a pending membership. If the joiner
  already holds a membership in the campaign, that membership is reused.
- **Revocation.** An issuer with at least the `GAME_MASTER` role can revoke a
  still-pending invite.

## Ports

The backend API serving these endpoints runs on port **5172**, persisted in
`server/src/main/resources/application.properties` (`server.port=5172`). The
client development server runs on port **5322**, as documented in the repo root
README and architecture docs. No default ports (3000, 4200, 5000, 5173, 8000,
8080, 8081, 8888) are used.
