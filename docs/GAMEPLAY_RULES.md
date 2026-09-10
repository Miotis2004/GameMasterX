# Gameplay Checks: Ability Checks, Skill Checks and Saving Throws

## Overview

The `gameplay` package resolves the three backend-owned check kinds —
**ability checks**, **skill checks** and **saving throws** — as deterministic,
backend-owned rules. Each check is computed entirely in backend code by
consuming the shared **dice subsystem** (`DiceRoller` + `DiceExpression`) and
combining the die total with a deterministic set of modifiers (ability modifier
and, when it applies, proficiency bonus).

The result is an immutable, auditable `Action` (see `docs/GAMEPLAY_AUDIT.md`)
that records the inputs, the ordered modifiers, the random values that were
drawn, and the final outcome. Through the gameplay controller each resolved
check is also appended to the immutable turn history as a `Turn` document,
bounded by the encounter aggregate's revision before and after.

## Package layout

| Type | Responsibility |
| ---- | -------------- |
| `gameplay.model.CheckType` | The three check kinds and their mapping to an `ActionType`. |
| `gameplay.service.GameplayRulesService` | Single authority that resolves a check into an auditable `ResolvedCheck`. |
| `gameplay.controller.CheckRequest` | Shared request payload for the three check endpoints. |
| `gameplay.controller.GameplayController` | REST surface on the backend API port (5172). |

## What a check is

Every check rolls a single `1d20` through the dice subsystem and adds the
deterministic modifiers:

| Check kind | Formula | Proficiency applied |
| ---------- | ------- | ------------------- |
| **Ability check** (`CheckType#ABILITY_CHECK`) | `1d20 + ability modifier` | only when the actor is proficient |
| **Skill check** (`CheckType#SKILL_CHECK`) | `1d20 + ability modifier + proficiency bonus` | always (skills are professed) |
| **Saving throw** (`CheckType#SAVING_THROW`) | `1d20 + ability modifier` | only when the actor is proficient |

The **ability modifier** is derived server-side from the raw ability score using
the standard formula `floor((score - 10) / 2)`, mirroring
`Character.AbilityModifiers.fromScores`. Callers supply a raw score and never a
trusted modifier, so the modifier can never be tampered with on the wire.

## Determinism

Proficiency and ability modifiers are applied deterministically:

- The ability modifier is a pure function of the ability score.
- The proficiency bonus is applied according to a single, closed rule
  (`CheckType#appliesProficiency`): skill checks always apply it; ability checks
  and saving throws apply it only when the actor is proficient.
- The `1d20` is rolled through the same `DiceRoller` used by the dice API, so a
  seeded roll (`mode = "seeded"`) reproduces the identical die value for a given
  seed, and a random roll (`mode = "random"`, the default) records the individual
  die value on the audit record for later verification.

Because of this, the same inputs always produce the same modifiers and, for a
given seed, the same die value and the same total.

## Outcome

The outcome is derived deterministically from an optional **difficulty class
(DC)**:

- `total >= DC` &rarr; `SUCCESS`
- `total < DC` &rarr; `FAILURE`
- no DC supplied &rarr; `UNKNOWN`

The final `total` is `1d20 + ability modifier + (proficiency bonus, when applied)`.

## API contract

Checks go through the `/api/gameplay` endpoints on the backend API port (5172):

| Method & path | Resolves |
| ------------- | -------- |
| `POST /api/gameplay/ability-check` | an ability check |
| `POST /api/gameplay/skill-check` | a skill check |
| `POST /api/gameplay/saving-throw` | a saving throw |

### Request body (`CheckRequest`)

The three endpoints share a single request shape; the endpoint selects the
`CheckType`, which governs how proficiency is applied.

| Field | Type | Notes |
| ----- | ---- | ----- |
| `actorId` | string | Required. The actor performing the check. |
| `campaignId` | string | Optional. Owning campaign; used when persisting the audit record. |
| `encounterId` | string | Optional. Encounter bounding the audited record's revision. |
| `targetId` | string | Optional. The targeted participant. |
| `targetName` | string | Optional. The target's name. |
| `abilityName` | string | Required. The ability the check is based on, e.g. `Strength`. |
| `abilityScore` | int | Required. Raw ability score; the modifier is derived server-side. |
| `proficient` | boolean | Ignored by skill checks; gates proficiency for ability checks and saving throws. |
| `proficiencyBonus` | int | Proficiency bonus to apply when proficient (`0` = none). |
| `dc` | int | Optional difficulty class; omit for an unqualified roll. |
| `rollMode` | string | `"random"` (default) or `"seeded"`. Case-insensitive. |
| `seed` | string | Required when `rollMode` is `"seeded"`; ignored otherwise. |
| `label` | string | Optional human-readable label for the die result. |
| `note` | string | Optional free-form note on the audit action. |

Example request:

```json
{
  "actorId": "actor-1",
  "campaignId": "campaign-1",
  "encounterId": "encounter-1",
  "abilityName": "Dexterity",
  "abilityScore": 16,
  "proficient": true,
  "proficiencyBonus": 2,
  "dc": 12,
  "rollMode": "seeded",
  "seed": "combat-round-1"
}
```

### Response body (`ResolvedCheck`)

| Field | Type | Notes |
| ----- | ---- | ----- |
| `checkType` | string | `ability-check` / `skill-check` / `saving-throw`. |
| `abilityName` | string | The ability the check was based on. |
| `proficient` | boolean | Whether the actor was proficient. |
| `proficiencyBonus` | int | The declared proficiency bonus. |
| `appliesProficiency` | boolean | Whether the proficiency bonus was actually applied. |
| `abilityModifier` | int | The ability modifier that was applied. |
| `modifiers` | `CheckModifier[]` | The ordered, named modifiers that were applied. |
| `dieResult` | `DiceResult` | The auditable die record (records the drawn die value). |
| `roll` | int | The raw `1d20` value that was rolled. |
| `modifierTotal` | int | The sum of every applied modifier. |
| `total` | int | The final outcome: `roll + modifierTotal`. |
| `dc` | int \| null | The difficulty class, or null. |
| `outcome` | string | `SUCCESS` / `FAILURE` / `PARTIAL` / `UNKNOWN`. |
| `success` | boolean | Whether the total met or exceeded the DC. |

## Error handling

- An unrecognized `rollMode` is rejected with a `400 VALIDATION_ERROR`
  (mapped from the dice subsystem's `DiceExpressionException`).
- A `seeded` roll with a missing or blank `seed` is rejected with a `400
  BAD_REQUEST`.
- An `abilityScore` outside the permitted range is rejected with a `400
  BAD_REQUEST`.
- A missing `encounterId` context is tolerated; the check is resolved and
  recorded without an encounter (revision recorded as `0`).
- All endpoints require an authenticated actor; unauthenticated callers receive
  `403 FORBIDDEN`.

## Auditability and revision before/after

Each resolved check is appended to the immutable turn history by
`GameplayAuditService.appendTurn` as a new `Turn` document embedding the
immutable `Action` and its `DiceResult`, together with the encounter aggregate's
`revisionBefore` and `revisionAfter`. Because a check does not mutate the
encounter, the revision before and after are equal; prior turn documents are
never updated, so the recorded outcome can never be retro-edited. See
`docs/GAMEPLAY_AUDIT.md` for the append-only turn and audit-log model.

## Contract

The client-facing types live in `contracts/gameplay.ts`, which mirrors the
backend `CheckRequest` and `ResolvedCheck` records.
