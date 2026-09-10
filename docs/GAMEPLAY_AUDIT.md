# Gameplay Audit Records & Immutable Turn/Audit History

## Overview

The `gameplay` package defines the typed, immutable records that capture what
happened during a session — individual dice rolls, gameplay actions, proposed
state mutations, whole turns, and the append-only audit decisions that record
which mutations were accepted or rejected.

These records live under
`server/src/main/java/com/gamemasterx/server/gameplay` and follow the same
persistence conventions as the Campaign, Membership, Character and Encounter
aggregates: Spring Data MongoDB documents backed by `MongoRepository`
interfaces and an application service that owns all writes.

## Package layout

| Type | Responsibility |
| ---- | -------------- |
| `model/DiceResult` | Immutable record of a single resolved die roll: expression, individual rolls, modifier, total. |
| `model/Modifier` | Immutable record of a single named, signed modifier applied to an action. |
| `model/ActionType` / `model/ActionOutcome` | Closed enums classifying an action and its resolved outcome. |
| `model/Action` | Immutable record of a gameplay action: inputs, modifiers, resolved dice, final outcome. |
| `model/Mutation` | Immutable record of a proposed state change: subject, before, after. |
| `model/MutationDecision` | Enum (`ACCEPTED` / `REJECTED`) recording the audit decision on a mutation. |
| `model/Turn` | Immutable record of a turn: acting participant, round/turn index, embedded actions and dice, revision before/after. |
| `model/Audit` | Immutable record of a single append-only audit-log entry: decision, before/after, revision before/after. |
| `repository/TurnRepository` | Spring Data MongoDB repository for turn documents. |
| `repository/AuditRepository` | Spring Data MongoDB repository for the append-only audit log. |
| `service/GameplayAuditService` | Application service that appends turn and audit history immutably. |

## Records and what they capture

Every record is a Java `record`, so all of its fields are final and the value
is immutable once constructed. This guarantees at the language level that a
record already stored or handed to a caller cannot be mutated.

- **`DiceResult`** captures the *inputs* (`label`, `diceExpression`, `dieSize`,
  `numberOfDice`), the *random values* (`rolls`) that were drawn, an optional
  `modifier`, and the *final outcome* (`total`).
- **`Action`** captures the *inputs* (`type`, `actorId`, `targetId`,
  `targetName`, `skillOrAbility`), the ordered *modifiers* (`modifiers`), the
  *random values* (`diceResults`), and the *final outcome* (`modifierTotal`,
  `total`, `outcome`).
- **`Mutation`** captures the *inputs* (`subjectType`, `subjectId`,
  `description`) together with the structured `before` and `after` state of the
  subject.
- **`Turn`** embeds every `Action` and `DiceResult` taken during the turn and
  records the encounter aggregate's `revisionBefore` and `revisionAfter`.
- **`Audit`** records the *accepted/rejected* decision on a mutation, the
  `before`/`after` state, the `mutationId`, and the `revisionBefore` /
  `revisionAfter`. The helper methods `acceptedMutations(...)` and
  `rejectedMutations(...)` let a caller reconstruct the full set of accepted and
  rejected mutations from a batch of audit entries.

## Immutability and append-oriented storage

Turn and audit history is stored immutably via two mechanisms working together:

1. **Value immutability** — the records themselves are Java records; their
   fields are `final` and the lists they hold are defensively copied and
   returned unmodifiable. Nothing can mutate a record after it is built.

2. **Append-only persistence** — `Turn` documents live in the `turns`
   collection and `Audit` documents live in the `audit_log` collection. The
   `GameplayAuditService` is the single place these are written, and it only
   ever *inserts* new documents:
   - A new `Turn` document is appended for each turn; it is never updated.
   - A new `Audit` document is appended for each audited event; existing audit
     documents are never updated or deleted.
   - Each audit entry is assigned the next monotonically increasing
     `auditSequence` inside the owning transaction, so the log reads in a
     stable, globally increasing order regardless of wall-clock timing.

Because prior turn and audit documents are never modified in place, the
historical record of accepted and rejected mutations — and the revision before
and after each change — can never be retro-edited.

## Using the service

```java
@Autowired
GameplayAuditService auditService;

// Append a turn together with the accepted/rejected decisions on its mutations.
Turn savedTurn = auditService.appendTurnAndAudit(
        turn,                 // id assigned when missing
        mutations,             // mutations considered during the turn
        resolutions,           // ACCEPTED / REJECTED for each, same order
        reasons,               // reason for each rejected mutation (nullable)
        revisionBefore,        // encounter revision before the turn
        revisionAfter,         // encounter revision after the turn
        actor,                 // acting actor (game master)
        correlationId);

// Read the immutable history back.
List<Turn> turns = auditService.turnsForEncounter(encounterId);
List<Audit> log  = auditService.auditLogForEncounter(encounterId);
List<Audit> accepted = Audit.acceptedMutations(log);
List<Audit> rejected = Audit.rejectedMutations(log);
```

## Endpoints

No dedicated REST surface is required by this milestone: the records and the
append-only service are the reusable backend primitives that the encounter and
gameplay endpoints consume to record turn and audit history. When those
endpoints expose history, they will surface `turnsForEncounter` and
`auditLogForEncounter` on the backend API port (5172).
