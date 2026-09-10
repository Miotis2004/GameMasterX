# Auditable Dice Rolling & Seeded Deterministic Rolling

The `dice` package resolves polyhedral dice expressions into auditable,
structured results, and supports two selectable sources of randomness:
unpredictable random rolls and reproducible seeded rolls.

## Overview

GameMasterX already models dice at two layers:

- **`dice/DiceExpression` / `dice/DiceGroup`** &ndash; a parsed, immutable
  representation of a dice expression such as `2d6+3` or `1d20dis`. Parsing is
  a pure, deterministic transformation.
- **`gameplay/model/DiceResult`** &ndash; an immutable record capturing exactly
  what an audit requires: the inputs (`label`, `diceExpression`, `dieSize`,
  `numberOfDice`), the **random values that were drawn** (`rolls`), an optional
  `modifier`, and the resulting `total`.

The **`DiceRoller`** ties the two together. It is the single place where die
values are drawn, and it produces one `DiceResult` per expression group. Because
every `DiceResult` records the individual `rolls` that were drawn, every roll is
both **auditable** and, in seeded mode, **reproducible**.

## Package layout

| Type | Responsibility |
| ---- | -------------- |
| `dice/DiceRoller` | Resolves a `DiceExpression` into auditable `DiceResult`s for a chosen `RollMode`. |
| `dice/RollMode` | Selects between unpredictable and seeded-deterministic randomness. |
| `dice/DiceRollRequest` | Request body for a roll: expression, mode, seed, optional label. |
| `dice/DiceRollResponse` | Response body: echoed expression/mode/seed, resolved results, aggregate total. |
| `controller/DiceController` | REST endpoint `POST /api/dice/roll` on backend port 5172. |

## Roll modes

`RollMode` is the selectable, documented source of randomness. Exactly one mode
applies per roll request:

| Mode | Wire value | Source | Reproducible | Seed |
| ---- | ---------- | ------ | ------------ | ---- |
| `RANDOM` | `random` (default) | `java.security.SecureRandom` (via a freshly-seeded `java.util.Random`) | No | None |
| `SEEDED` | `seeded` | `java.util.Random` seeded from a stable FNV-1a 64-bit hash of the seed | Yes | Required |

### Auditable random generation (`RANDOM`)

Random rolls are drawn from a cryptographically strong generator. They are
unpredictable and not reproducible, but **the individual random values that were
used are recorded on each `DiceResult`** (`rolls`), so the exact randomness that
produced an outcome can be audited later. No seed is used or returned.

### Seeded deterministic rolling (`SEEDED`)

Seeded rolls derive a deterministic `java.util.Random` from a caller-supplied
`seed`. The seed string is hashed with the **FNV-1a 64-bit** algorithm, a pure
integer computation that is deterministic and identical across platforms. As a
result, **rolling the same expression with the same seed always produces an
identical sequence of die values and the same totals**, so outcomes can be
verified and replayed. The `seed` is echoed back in the response so the exact
generator state can be reproduced elsewhere.

## Advantage and disadvantage

Each die in a group with advantage is rolled twice and the higher value is kept;
each die with disadvantage is rolled twice and the lower value is kept. The kept
value is recorded on the group's `DiceResult` (so `rolls` holds exactly one value
per die and `total = sum(rolls) + modifier`), matching the `DiceResult`
contract.

## API contract

Rolls go through `POST /api/dice/roll` on the backend API port (5172). The
shared TypeScript types live in `contracts/dice.ts`.

Request body (`DiceRollRequest`):

| Field | Type | Notes |
| ----- | ---- | ----- |
| `expression` | string | The expression to resolve, e.g. `2d6+3` or `1d20dis`. Parsed on receipt; malformed input is rejected with a `400 VALIDATION_ERROR`. |
| `mode` | string | `"random"` (default) or `"seeded"`. Case-insensitive. |
| `seed` | string | Required when `mode` is `"seeded"`; ignored otherwise. Blank/missing for a seeded roll is rejected with a `400 VALIDATION_ERROR`. |
| `label` | string | Optional human-readable label applied to each result; defaults to the group text. |

Example request:

```json
{
  "expression": "2d6+3",
  "mode": "seeded",
  "seed": "combat-round-1",
  "label": "Attack roll"
}
```

Response body (`DiceRollResponse`):

| Field | Type | Notes |
| ----- | ---- | ----- |
| `expression` | string | The expression resolved (as supplied). |
| `mode` | string | The applied mode as a wire string (`random` / `seeded`). |
| `seed` | string | The seed that produced this roll, or `null` for random mode. |
| `results` | `DiceResult[]` | One auditable result per expression group, in order. |
| `total` | int | The aggregate total: the sum of every `DiceResult#total()`. |

Example response:

```json
{
  "expression": "2d6+3",
  "mode": "seeded",
  "seed": "combat-round-1",
  "results": [
    {
      "id": null,
      "label": "Attack roll",
      "diceExpression": "2d6+3",
      "dieSize": 6,
      "numberOfDice": 2,
      "rolls": [4, 2],
      "modifier": 3,
      "total": 9,
      "rolledAt": "2026-09-09T12:00:00Z"
    }
  ],
  "total": 9
}
```

## Error handling

- A malformed `expression` is rejected with `400 VALIDATION_ERROR`, tagged with
  the offending field (via `DiceExpressionException`).
- A `seeded` mode with a missing or blank `seed` is rejected with `400
  VALIDATION_ERROR`.
- An unrecognized `mode` is rejected with `400 VALIDATION_ERROR`.
- All endpoints require an authenticated actor, consistent with the rest of the
  API; unauthenticated callers receive `403 FORBIDDEN`.

## Reproducing a seeded roll

Because seeded rolls are deterministic, a roll can be replayed by sending the
same `expression` and `seed` again:

```
POST /api/dice/roll
{ "expression": "2d6+3", "mode": "seeded", "seed": "combat-round-1" }
```

The returned `results` and `total` are guaranteed to be identical to the
original roll, and each `DiceResult.rolls()` records the exact die values that
were drawn.
