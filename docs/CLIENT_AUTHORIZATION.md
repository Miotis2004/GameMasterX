# Client-Side Authorization: Role-Based UI Visibility

This document describes how the GameMasterX client presents controls according to
the caller's campaign role, and — equally importantly — how it does **not** treat
that visibility as a security control. It complements
[`AUTHORIZATION.md`](./AUTHORIZATION.md), which describes the server-side
enforcement boundary.

## The one rule

> **Hiding a control is a UI convenience, never a security control.**

The frontend may show or hide controls based on the caller's campaign role so
that the interface stays focused. This visibility is derived purely from the
caller's role and is always reversible by a client. Therefore:

- The UI must never claim (in text, labels, tooltips, or comments) that a hidden
  control "provides security," "is protected," or "prevents access."
- The UI must never be relied upon as an access decision. Every state-changing
  request is still authorized by the backend
  ([`MembershipService`](../server/src/main/java/com/gamemasterx/server/campaign/membership/service/MembershipService.java)),
  which returns `403 Forbidden` when the caller lacks the required role.
- The backend is the single enforcement boundary.

## How visibility is applied

Controls are shown or hidden based on the caller's **campaign role**, following
the linear hierarchy (highest to lowest authority):

`OWNER` > `GAME_MASTER` > `PLAYER` > `OBSERVER`

The caller's role is obtained from the campaign dashboard
(`actorRole`), and role checks mirror the server-side
[`MembershipRole.isAtLeast`](../server/src/main/java/com/gamemasterx/server/campaign/membership/model/MembershipRole.java)
semantics: a role is authorized for an action when its level is greater than or
equal to the action's required level.

The [`MembershipManagementComponent`](../client/src/app/membership-management.component.ts)
is the primary example:

- A member may accept their own pending membership and revoke (leave) their own
  membership; these controls are shown only for the caller's own row.
- The role control is shown only to members who can manage it: a member may
  always readjust their own role, and a `GAME_MASTER`/`OWNER` may manage other
  members' roles.
- When a member edits their own role, only roles at or below their current role
  are offered, matching the server rule that a member cannot elevate themselves.
  An authorised manager sees every role.

## Route protection

Authenticated navigation is guarded by the [`AuthGuard`](../client/src/app/auth.guard.ts),
which redirects unauthenticated callers to the login page. This enforces
authentication. Role-based *authorization* of individual actions is enforced by
the backend, not by route guards that only adjust what is visible.

## Ports

The backend API enforcing these rules runs on port **5172**; the client
development server runs on port **5322**. No default ports (3000, 4200, 5000,
5173, 8000, 8080, 8081, 8888) are used.
