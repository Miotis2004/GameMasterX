# Encounter Aggregate

## Overview

The Encounter aggregate models a single combat/session within a campaign. It
tracks participants, who controls each of them, the initiative order, the
current round and turn, grid positions, hit points, conditions and resources,
and its own lifecycle through `DRAFT`, `ACTIVE`, `PAUSED` and `COMPLETED` states.

The aggregate lives under `server/src/main/java/com/gamemasterx/server/encounter`
and follows the same persistence conventions as the Campaign, Membership and
Character aggregates. The document entity is `Encounter`; the API-facing type is
`EncounterDto`; the creation payload is `EncounterCreateRequest`.

## Package layout

| Type | Responsibility |
| ---- | -------------- |
| `model/Encounter` | Persistence-only document entity (the aggregate root) and its embedded value objects. |
| `model/EncounterStatus` | The four lifecycle states. |
| `model/ActorControl` | Who controls each participant (`SELF`, `GM`, `AUTOMATED`). |
| `model/RulesProfile` | The selected rules profile: the supported rules subset and its critical-hit behaviour. |
| `model/EncounterDto` | API-facing representation returned by (and accepted from) the endpoints. |
| `service/RulesProfileService` | Single authority for the rules subset and critical-hit behaviour; validates the profile before an encounter proceeds. |
| `RulesProfileValidationException` | Raised when the rules profile is missing or unsupported before the encounter can proceed. |
| `model/EncounterCreateRequest` | Minimal creation payload. |
| `repository/EncounterRepository` | Spring Data MongoDB repository. |
| `service/EncounterService` | Application service; owns all mutation and the lifecycle. |
| `controller/EncounterController` | REST surface on the backend API port (5172). |
| `EncounterStateTransitionException` | Raised on an illegal lifecycle transition. |

## Persistence conventions (Milestone 1 & 2)

The `Encounter` document carries:

- a **stable id** (`@Id`, a `UUID` assigned on create);
- a **schema version** (`SCHEMA_VERSION`, currently `1`);
- a **rules profile** (`RulesProfile`) selecting the supported rules subset and
  its critical-hit behaviour (defaults to `SRD-5.2-2024`);
- a **revision** counter annotated with `@Version` for optimistic concurrency
  control;
- **created/updated** timestamps.

As with the other aggregates, the document entity is deliberately distinct from
the API-facing `EncounterDto`: the revision counter is not part of the wire
contract.

## State machine

The legal transitions are enforced inside the aggregate itself by
`Encounter.transitionTo(EncounterStatus)`, with `allowedTransitions()` declaring
the permitted edges. There is a single source of truth for the lifecycle:

```
DRAFT  --start-->      ACTIVE  --pause-->     PAUSED
  ^                      |   \                  |   \
  |                      |   \end              |    \end
  |                      |    \--> COMPLETED <--|     \--> COMPLETED
```

`COMPLETED` is terminal. The service methods `startEncounter`, `pauseEncounter`,
`resumeEncounter` and `completeEncounter` each map onto a single permitted
transition and delegate the actual state change to the aggregate. An illegal
transition raises `EncounterStateTransitionException`, which the controller maps
to a `400 Bad Request`.

### Rules profile and critical-hit behaviour

Before an encounter is allowed to proceed (`startEncounter`), the service runs
the server-side gate `RulesProfileService.validateEncounterCanProceed`. It
rejects the start with a `400 Bad Request` (mapped from
`RulesProfileValidationException`) when the encounter has no rules profile, or
when the selected profile names a subset that is no longer supported. The
creation payload (`EncounterCreateRequest.rulesProfile`) is optional; when
omitted the service applies the default profile (`SRD-5.2-2024`).

The selected profile is the single source of truth for critical-hit behaviour:
`RulesProfileService.resolveCriticalHit(profile, roll)` deterministically reports
whether a roll is a critical hit (roll `>=` the profile's threshold) and the
damage multiplier the subset prescribes.

## Aggregated concerns

- **Participants** — embedded `Participant` value objects, each with a stable id.
- **Actor control** — each participant records an `ActorControl` deciding who
  issues actions for it.
- **Initiative** — `initiativeOrder` lists participant ids in descending
  initiative order; it is recomputed by `recomputeInitiativeOrder()`.
- **Rounds / turns** — `round` is the current round number and `turn` is the
  index of the acting participant within the initiative order; `advanceTurn()`
  wraps into the next round.
- **Positions** — each participant carries a grid `Position` (x, y).
- **Hit points** — embedded `HitPoints` (max / current / temporary).
- **Conditions** — a list of embedded `Condition`s with optional round durations.
- **Resources** — a list of embedded `Resource`s (spell slots, action charges, …).

## Authorization

Encounter access is enforced against the campaign membership hierarchy via the
existing `MembershipService`:

- reads require at least the `OBSERVER` role in the campaign;
- lifecycle transitions and draft organisation require the `GAME_MASTER` role.

## Endpoints

All endpoints are scoped under `/api/encounters` on the backend API port
(5172):

| Method & path | Action |
| ------------- | ------ |
| `POST /api/encounters` | Create a draft encounter. |
| `GET /api/encounters?campaignId=...` | List a campaign's encounters. |
| `GET /api/encounters/{id}` | Read an encounter. |
| `POST /api/encounters/{id}/participants` | Add/replace a participant (draft). |
| `DELETE /api/encounters/{id}/participants/{participantId}` | Remove a participant (draft). |
| `POST /api/encounters/{id}/start` | Start the encounter. |
| `POST /api/encounters/{id}/pause` | Pause an active encounter. |
| `POST /api/encounters/{id}/resume` | Resume a paused encounter. |
| `POST /api/encounters/{id}/complete` | Complete an encounter. |
| `POST /api/encounters/{id}/turns/advance` | Advance to the next turn. |

The rules profile is validated server-side before `start` proceeds; a missing or
unsupported profile yields `400 Bad Request`.

## Contract

The client-facing types are shared in `contracts/encounter.ts`, which mirrors the
backend DTOs, including the `RulesProfile` type and the `rulesProfile` fields on
the `Encounter` and `EncounterCreateRequest` interfaces. A dedicated note is
kept in `docs/RULES_PROFILE.md`.
