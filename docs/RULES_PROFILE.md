# Rules Profile and Critical-Hit Behaviour

## Overview

Every encounter follows exactly one **rules profile**. The profile selects the
**supported rules subset** the encounter is resolved against and, through that
subset, governs every rule-derived behaviour that flows from it. The single most
visible rule-derived behaviour is **critical-hit behaviour**: which attack rolls
count as critical hits and by what factor their damage is multiplied.

The profile is modelled as a closed, deterministic enumeration
(`com.gamemasterx.server.encounter.model.RulesProfile`). There is exactly one
source of truth for both the rules subset an encounter supports and the
critical-hit threshold and damage multiplier that subset prescribes, so the
gameplay layer never branches on a profile by name.

## Package layout

| Type | Responsibility |
| ---- | -------------- |
| `encounter.model.RulesProfile` | The selected profile. Names the supported rules subset and carries its critical-hit threshold and damage multiplier. |
| `encounter.service.RulesProfileService` | Single authority for the rules subset and critical-hit behaviour; validates the profile before an encounter proceeds. |
| `encounter.RulesProfileValidationException` | Raised when the profile is missing or unsupported before the encounter can proceed. |

## Supported rules subset

The enum enumerates the rules subsets the platform can follow. Each value carries
a `supported` flag that distinguishes profiles a campaign may actively select
from legacy profiles that are retained for reading existing documents but are no
longer selectable:

| Profile | Rules subset | Supported | Critical threshold | Damage multiplier |
| ------- | ------------ | --------- | ------------------ | ----------------- |
| `SRD_5_2024` | `SRD-5.2-2024` | yes | 20 | 2.0 |
| `SRD_5_2014` | `SRD-5.1-2014` | no (legacy) | 20 | 2.0 |

Only supported profiles may govern an encounter that proceeds.

## Critical-hit behaviour

The selected profile governs critical hits deterministically:

- An attack roll `>=` the profile's critical-hit threshold is a critical hit.
- Critical damage is dealt at the profile's critical-damage multiplier.

`RulesProfileService.resolveCriticalHit(profile, roll)` returns a
`CriticalHitOutcome` that records the roll, whether it is a critical hit, the
threshold used, the multiplier, and the governing profile. The same inputs always
produce the same verdict.

## Lifecycle

1. **Selection.** The creation payload (`EncounterCreateRequest.rulesProfile`)
   is optional. When it is blank or omitted the service applies the default
   profile (`SRD-5.2-2024`). The value is resolved with
   `RulesProfileService.resolve`, accepting either the enum constant name or the
   rules-subset identifier (case-insensitive); an unknown value is rejected at
   deserialization.
2. **Persistence.** The resolved profile is stored on the `Encounter` document
   (defaulting to `SRD-5.2-2024` so a profile is always present) and exposed on
   the `EncounterDto` and the shared contract `contracts/encounter.ts`.
3. **Validation before proceed.** `startEncounter` runs
   `RulesProfileService.validateEncounterCanProceed`. It rejects the start with a
   `400 Bad Request` (mapped from `RulesProfileValidationException`) when the
   encounter has no profile or the selected profile names a subset that is no
   longer supported. This is the server-side gate that runs before an encounter
   proceeds.

## Contract

The client-facing types live in `contracts/encounter.ts`:

- `RulesProfile = 'SRD-5.2-2024' | 'SRD-5.1-2014'` — the wire value is the
  rules-subset identifier, not the enum constant name.
- `Encounter.rulesProfile` and `EncounterCreateRequest.rulesProfile` (nullable).
