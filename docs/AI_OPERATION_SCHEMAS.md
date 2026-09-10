# Versioned Schemas for AI-Proposed Operations

## Overview

AI-completed completions propose changes to encounter state. A **proposed
operation** is the typed, immutable boundary between an
`com.gamemasterx.server.ai.AiProvider` completion and the backend's validation
layer. Every proposal carries an explicit **schema version**, and the backend
validates it against the authoritative, versioned schema that version selects.

These schemas live exclusively in the backend and are never exposed to the
browser as editable or mutable inputs.

Package: `server/src/main/java/com/gamemasterx/server/ai/operation`

## Records, enums and what they capture

| Type | Responsibility |
| ---- | -------------- |
| `OperationType` | Closed set of the kinds of state change an operation may describe. Backend-authoritative; never extended by the browser. |
| `OperationSchemaVersion` | Recognised schema versions (`1`, `2`). Each has a `wire()` label and resolves a wire value via `fromWire`. |
| `ProposedOperation` | Immutable, typed record: `schemaVersion`, `operationType`, `targetKind`, `targetId`, `targetName`, `parameters`, `justification`, `priority`, `constraints`. All fields final; collections defensively copied. |
| `OperationSchema` | Interface: a versioned rule set (`version()`, `supportedOperationTypes()`, `validate(ProposedOperation)`). Stateless and thread-safe. |
| `v1/ProposedOperationSchemaV1` | Schema version 1 rules. |
| `v2/ProposedOperationSchemaV2` | Schema version 2 rules. |
| `OperationSchemaRegistry` | `@Component` holding one schema per version; the single authority for which schemas exist. |
| `ProposedOperationValidator` | `@Service` — the single backend entry point that validates a proposal against its declared version's schema. |
| `OperationSchemaException` | Validation failure (mapped to `400 BAD_REQUEST`). |
| `OperationSchemaController` | Read-only catalogue of supported versions; no endpoint can edit schemas. |

## Schema versions

Both versions coexist and are validated simultaneously:

- **Version 1** (`"1"`) — requires a non-blank `operationType` (from the V1
  supported set), `targetKind`, `targetId` and `justification`. The V2-only
  `priority` and `constraints` fields are forbidden.
- **Version 2** (`"2"`) — adds a bounded `priority` (`0..100`) and a list of
  `constraints`, and exposes the `NARRATE` operation type. Justification is
  required for every type except `NARRATE`.

When the contract evolves, a new version is introduced *alongside* the old one
rather than replacing it, so proposals captured under different versions can
each be validated against their own schema.

## Validation flow

A proposed operation must conform to its versioned schema **before** it is
considered:

```java
@Service
class SomeConsumer {
    private final ProposedOperationValidator validator;

    void handle(ProposedOperation proposal) {
        // Rejects unknown versions and any proposal that violates the
        // rules of the schema matching proposal.schemaVersion().
        validator.validate(proposal);
        // ... only now is the proposal acted on ...
    }
}
```

`ProposedOperationValidator.validate` resolves `proposal.schemaVersion()` to an
`OperationSchemaVersion` (an unknown or blank version raises
`OperationSchemaException`) and then runs the selected schema's rules. The
schema is the sole authority for what makes a proposal valid; a caller cannot
rewrite what a version means.

## Backend validation orchestration

The schema check is only the first of twelve backend-authoritative dimensions.
The orchestrating {@link com.gamemasterx.server.ai.operation.validate.OperationValidator}
runs a proposal through every dimension in a fixed, backend-owned dependency
order before the backend will execute it:

| Dimension | Enforced by |
| --------- | ----------- |
| `SCHEMA` | `SchemaChecker` |
| `ENTITY_EXISTS` | `EntityExistenceChecker` |
| `AUTHORIZATION` | `AuthorizationChecker` |
| `ACTOR_CONTROL` | `ActorControlChecker` |
| `LEGAL_ACTION` | `LegalActionChecker` |
| `TARGET` | `TargetChecker` |
| `RANGE` | `RangeChecker` |
| `RESOURCES` | `ResourceChecker` |
| `EXPECTED_REVISION` | `ExpectedRevisionChecker` |
| `IDEMPOTENCY` | `IdempotencyChecker` |
| `NUMERIC_AGREEMENT` | `NumericAgreementChecker` |
| `SECRET_DISCLOSURE` | `SecretDisclosureChecker` |

The checks run in ordinal order: a proposal is structurally valid before it is
checked for existence, existence before authorization, authorization before actor
control, and so on. The order is backend-declared; a caller cannot influence
which dimension is evaluated first.

`OperationValidator.validateStrict` raises
`OperationValidationException` on the first failing dimension (or when a
repeated idempotency key is supplied), so a rejected proposal never reaches
deterministic execution. The deterministic rules engine remains the sole
authority for the `LEGAL_ACTION` decision, and authorization is enforced by the
backend for every proposed operation through the `AUTHORIZATION` dimension.

## Backend execution wiring

The validation orchestration is only meaningful when a real request path actually
invokes it. `ProposedOperationExecutionService` is the single backend-authoritative
path that turns a `ProposedOperation` into a deterministic change of encounter
state:

1. The owning encounter is loaded once through
   `EncounterService.loadForOperation`, which also asserts the caller may access
   it.
2. A `ProposedOperationContext` is built around that loaded document (actor,
   campaign, encounter, expected revision and idempotency key) so every
   dimension observes the same authoritative state.
3. `OperationValidator.validateStrict` runs; on any failure the request is
   rejected with a `400 BAD_REQUEST` before anything is persisted.
4. Only a fully cleared proposal is dispatched to the backend-owned
   `EncounterService` mutation, which owns the hit-point, resource and movement
   rules and commits through the guarded, idempotent coordinator.

The dispatch from `OperationType` to a service method is entirely backend-owned;
the caller may name an operation type but cannot rewrite what each one does.
The AI never generates authoritative random results and never mutates MongoDB
directly: the only path that persists a proposed change is one that has first
been cleared by this orchestrator.

## Backend ownership

- Schema definitions are created and held only by
  `OperationSchemaRegistry` at application startup.
- The schemas are never serialised to the browser as editable inputs. The only
  API surface is `GET /api/ai/operation-schemas`, which returns a **read-only**
  catalogue of supported versions and their `operationType`s. It cannot create,
  edit, remove or reorder any schema.
- `OperationType` values are backend-declared; a proposal naming an unknown
  type is rejected during validation.
