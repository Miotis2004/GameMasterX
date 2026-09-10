# Gameplay Attacks: To-Hit Resolution Against Armor Class

## Overview

The `gameplay` package resolves an **attack roll** as a deterministic,
backend-owned rule. An attack compares a to-hit roll against the target's
**armor class (AC)**, classifies the outcome as a critical hit, a hit or a
miss, and records the full audit trail. The attack is the combat companion of the
ability check, skill check and saving throw resolved by
{@link com.gamemasterx.server.gameplay.service.GameplayRulesService}.

An attack is computed entirely in backend code by consuming the shared **dice
subsystem** (`DiceRoller` + `DiceExpression`) and combining the die total with a
deterministic set of modifiers. The result is an immutable, auditable `Action`
(see `docs/GAMEPLAY_AUDIT.md`) with an `ActionType#ATTACK` category.

## Package layout

| Type | Responsibility |
| ---- | -------------- |
| `gameplay.model.ActionOutcome` | The outcome enum, now including `CRIT` for a critical hit. |
| `gameplay.service.GameplayRulesService#resolveAttack` | Single authority that resolves an attack into an auditable `ResolvedAttack`. |
| `gameplay.controller.AttackRequest` | Request payload for the attack endpoint. |
| `gameplay.controller.GameplayController` | REST surface on the backend API port (5172). |

## What an attack is

Every attack rolls a single `1d20` through the dice subsystem and adds the
deterministic attack modifier:

```
toHitTotal = 1d20 + ability modifier + (proficiency bonus, when proficient)
```

The **ability modifier** is derived server-side from the raw ability score using
the standard formula `floor((score - 10) / 2)`, mirroring the check resolution
and `Character.AbilityModifiers.fromScores`. Callers supply a raw score and
never a trusted modifier, so the modifier can never be tampered with on the wire.

### To-hit comparison against armor class

The to-hit total is compared against the target's **armor class** supplied by the
caller. The comparison is the to-hit verdict:

- `toHitTotal >= armorClass` &rarr; **hit**
- `toHitTotal < armorClass` &rarr; **miss**

The armor class is caller-supplied (it lives on the target, not the attacker) and
is validated to the same `[1, 30]` range as ability scores.

### Outcomes: crits, hits and misses

Outcomes are classified consistently and deterministically:

| Condition | Outcome |
| --------- | ------- |
| natural roll meets the profile's critical-hit threshold (a natural 20 under the default profile) | `CRIT` (always a hit) |
| otherwise, `toHitTotal >= armorClass` | `SUCCESS` (a hit) |
| otherwise (`toHitTotal < armorClass`) | `FAILURE` (a miss) |

A critical hit is always a hit; the additional critical effect (for example
doubled damage) is resolved by the separate damage step, never here.

### Critical hits and the rules profile

The critical-hit verdict is governed exclusively by the owning encounter's
selected, validated `RulesProfile` (the single source of truth for
critical-hit behaviour). The profile is loaded from the stored encounter and is
never taken from a value supplied on the attack request: the
`rulesProfile` field, when present, must match the encounter's stored profile or
the attack is rejected with a `400 BAD_REQUEST`. This stops critical-hit
behaviour from being overridden on the wire. The default profile
`SRD-5.2-2024` treats a natural 20 as a critical hit. The same roll always
produces the same critical verdict for a given profile, and critical hits are
only applied when the governing profile is a supported one (enforced by
`RulesProfileService.assertCriticalHitPermitted` at the point of resolution).

The critical-damage multiplier exposed on the resolved attack
(`ResolvedAttack.criticalDamageMultiplier`) is the backend-owned value from the
governing profile (`getCriticalDamageMultiplier`); it is a deterministic rule,
not language-model output.

## Determinism

Attack resolution is deterministic for the same inputs: the ability and
proficiency modifiers are pure functions of the inputs, and the `1d20` is rolled
through the same `DiceRoller` used by the dice API, so a seeded roll reproduces
the identical die value for a given seed and a random roll records the individual
die value on the audit record for later verification.

## Turn ownership and action availability

An attack is only valid within a live encounter on the acting participant's turn.
The controller enforces both invariants before the attack is resolved:

- **Action availability** &ndash; the owning encounter must be
  `ACTIVE`. A draft, paused or completed encounter has no actions available.
- **Turn ownership** &ndash; the acting participant is the participant whose turn
  it currently is (identified from the encounter's initiative order). The attack
  is resolved and recorded only when the request's `actorId` matches that
  participant.

Both invariants are enforced server-side; a violation is rejected with a
`400 BAD_REQUEST`.

## API contract

Attacks go through `/api/gameplay/attack` on the backend API port (5172).

### Request body (`AttackRequest`)

| Field | Type | Notes |
| ----- | ---- | ----- |
| `actorId` | string | Required. The actor (participant or NPC) performing the attack. |
| `campaignId` | string | Optional. Owning campaign. |
| `encounterId` | string | Required. Encounter bounding the audited record and governing critical hits. |
| `targetId` | string | Optional. The targeted participant. |
| `targetName` | string | Optional. The target's name. |
| `abilityName` | string | Required. The ability the attack is based on, e.g. `Strength`. |
| `abilityScore` | int | Required. Raw ability score; the modifier is derived server-side (`[1, 30]`). |
| `proficient` | boolean | Whether the actor is proficient in the attack. |
| `proficiencyBonus` | int | Proficiency bonus when proficient (`0` = none). |
| `armorClass` | int | Required. The target's armor class the to-hit total must meet or exceed (`[1, 30]`). |
| `rulesProfile` | string | Optional. Wire representation of the governing rules profile (defaults to `SRD-5.2-2024`). |
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
  "abilityName": "Strength",
  "abilityScore": 18,
  "proficient": true,
  "proficiencyBonus": 2,
  "armorClass": 15,
  "rollMode": "seeded",
  "seed": "combat-round-1"
}
```

### Response body (`ResolvedAttack`)

| Field | Type | Notes |
| ----- | ---- | ----- |
| `actorId` | string | Who performed the attack. |
| `abilityName` | string | The ability the attack was based on. |
| `proficient` | boolean | Whether the actor was proficient. |
| `proficiencyBonus` | int | The declared proficiency bonus. |
| `appliesProficiency` | boolean | Whether the proficiency bonus was actually applied. |
| `abilityModifier` | int | The ability modifier that was applied. |
| `modifiers` | `CheckModifier[]` | The ordered, named modifiers that were applied. |
| `dieResult` | `CheckDiceResult` | The auditable die record (records the drawn die value). |
| `roll` | int | The natural `1d20` value that was rolled. |
| `modifierTotal` | int | The sum of every applied modifier. |
| `toHitTotal` | int | The to-hit total: `roll + modifierTotal`. |
| `armorClass` | int | The target armor class the attack was resolved against. |
| `hit` | boolean | Whether `toHitTotal` met or exceeded the armor class. |
| `critical` | boolean | Whether the natural roll was a critical hit. |
| `outcome` | string | `CRIT` / `SUCCESS` (hit) / `FAILURE` (miss). |
| `profile` | string | The rules profile that governed critical-hit behaviour. |
| `action` | object | The immutable `Action` capturing the full audit trail. |

## Error handling

- An unrecognized `rollMode` is rejected with a `400 VALIDATION_ERROR`
  (mapped from the dice subsystem's `DiceExpressionException`).
- A `seeded` roll with a missing or blank `seed` is rejected with a `400
  BAD_REQUEST`.
- An `abilityScore` or `armorClass` outside the permitted range is rejected with
  a `400 BAD_REQUEST`.
- An attack on a missing encounter is rejected with a `400 BAD_REQUEST`.
- An attack while the encounter is not `ACTIVE`, or when it is not the actor's
  turn, is rejected with a `400 BAD_REQUEST`.
- An attack whose request-supplied `rulesProfile` disagrees with the encounter's
  stored profile is rejected with a `400 BAD_REQUEST`, so critical-hit
  behaviour cannot be overridden on the wire.
- All endpoints require an authenticated actor; unauthenticated callers receive
  `403 FORBIDDEN`.

## Auditability and revision before/after

Each resolved attack is appended to the immutable turn history by
`GameplayAuditService.appendTurnAndAudit` as a new `Turn` document embedding the
immutable `Action` and its `DiceResult`, together with the encounter aggregate's
`revisionBefore` and `revisionAfter`. Because an attack does not mutate the
encounter, the revision before and after are equal; prior turn documents are
never updated, so the recorded outcome can never be retro-edited.

When the attack resolves to a critical hit, it is additionally recorded as an
<em>accepted mutation</em> in the append-only audit log: a single `Audit` entry
with decision `ACCEPTED` captures the before/after state of the critical verdict
and the backend-owned critical-damage multiplier that the governing profile
prescribes. This makes critical hits auditable and proves the critical-hit
damage follows backend rules rather than any language-model output. See
`docs/GAMEPLAY_AUDIT.md` for the append-only turn and audit-log model.

## Contract

The client-facing types live in `contracts/gameplay.ts`, which mirrors the
backend `AttackRequest` and `ResolvedAttack` records.
