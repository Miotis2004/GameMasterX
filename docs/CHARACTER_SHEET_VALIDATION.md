# Character Sheet Validation

This document describes the deterministic validation applied to character sheets,
using the SRD 5.2 / 2024 terminology and rules. Validation is enforced
authoritatively on the backend in
`com.gamemasterx.server.character.CharacterValidator` and is invoked from
`com.gamemasterx.server.character.service.CharacterService` on every create and
update.

## Why deterministic

Every rule enforced here is fully deterministic: the same inputs always produce
the same verdict, and every failure reports the same field-specific message.
This makes the validation reproducible and independent of incidental ordering or
environment.

## Enforced rules

### Ability scores and derived modifiers

- Each of the six ability scores (`strength`, `dexterity`, `constitution`,
  `intelligence`, `wisdom`, `charisma`) must be an integer in the inclusive
  range `[1, 30]` (SRD 2024 ability score range).
- Ability modifiers must equal the deterministic derivation
  `floor((score - 10) / 2)` of the corresponding score. Modifiers are recomputed
  from the scores by the `Character` aggregate before they are stored, so a
  stored modifier that disagrees with its score fails validation.

### Level and proficiency bonus

- Character level must be an integer in the inclusive range `[1, 20]`, which is
  the SRD 2024 maximum character level.
- The proficiency bonus must equal the value dictated by the SRD 2024
  proficiency bonus table for the character's level:

  | Level  | Proficiency bonus |
  |--------|-------------------|
  | 1–4    | +2                |
  | 5–8    | +3                |
  | 9–12   | +4                |
  | 13–16  | +5                |
  | 17–20  | +6                |

### Hit points and armor class

- Max hit points must not be negative.
- Current hit points must not be negative and must not exceed max hit points.
- Temporary hit points must not be negative.
- Armor class must be an integer in the inclusive range `[1, 30]`.

### Resources and inventory quantities

- Every resource's `current` and `max` values must be non-negative integers.
- Every inventory item's `quantity` must be a non-negative integer.

## Validation boundary

- Only sections that are actually present (`non-null`) are validated, so partial
  updates that omit a section do not trigger spurious failures. When `abilityScores`
  is present, its modifiers are validated too.
- Bean Validation annotations on the request DTOs
  (`CharacterSheetCreateRequest`, `CharacterSheetUpdateRequest`) provide an early,
  field-level guard. The `CharacterValidator` is the authoritative backend
  boundary and also validates cross-field and derived relationships (such as the
  proficiency bonus vs. level) that annotation-level constraints cannot express.

## Error handling

Validation failures throw
`com.gamemasterx.server.character.CharacterSheetValidationException`, which is
mapped by `com.gamemasterx.server.exception.GlobalExceptionHandler` to a
`400 BAD_REQUEST` response. When a failure is tied to a specific field, the
offending field is included in the `fieldErrors` list.

The exception is intentionally **not** an `IllegalArgumentException`: the
character controller already maps `IllegalArgumentException` to `404 Not Found`
for the "character not found" case, and using a distinct type keeps validation
failures (400) separate from not-found failures (404).
