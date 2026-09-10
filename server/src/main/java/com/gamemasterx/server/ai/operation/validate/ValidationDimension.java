package com.gamemasterx.server.ai.operation.validate;

/**
 * The closed set of validation dimensions a {@link ProposedOperation} is
 * checked against before the backend will execute it.
 *
 * <p>Each dimension corresponds to one of the authoritative backend concerns
 * that a proposed operation must satisfy. The list is exhaustive and
 * backend-owned: a proposal is only ever acted on once it has passed every
 * applicable dimension, and the dimension that fails is reported so the
 * rejection is unambiguous.</p>
 */
public enum ValidationDimension {

    /**
     * The proposal conforms to the versioned {@link
     * com.gamemasterx.server.ai.operation.OperationSchema} it declares
     * (structure, field presence, permitted operation types).
     */
    SCHEMA,

    /**
     * The subject of the proposal actually exists (the encounter and its
     * participants / campaigns referenced by the proposal).
     */
    ENTITY_EXISTS,

    /**
     * The authenticated actor holds the role the backend requires for the
     * proposed operation in the relevant campaign.
     */
    AUTHORIZATION,

    /**
     * The actor is permitted to control the participant the operation targets
     * (respecting each participant's {@link
     * com.gamemasterx.server.encounter.model.ActorControl}).
     */
    ACTOR_CONTROL,

    /**
     * The operation is legal in the current encounter state and under the
     * encounter's governing rules subset. The deterministic rules engine is
     * the sole authority for this decision.
     */
    LEGAL_ACTION,

    /**
     * The target kind and identifier are well-formed for the operation type.
     */
    TARGET,

    /**
     * A movement or reach bound is respected (validated by the backend-owned
     * {@link com.gamemasterx.server.gameplay.service.MovementRangeService}).
     */
    RANGE,

    /**
     * A resource change keeps every resource within its legal bounds.
     */
    RESOURCES,

    /**
     * The caller's expected revision matches the stored revision (optimistic
     * concurrency), so a conflicting write is rejected before execution.
     */
    EXPECTED_REVISION,

    /**
     * A supplied idempotency key is well-formed and a repeated key cannot
     * re-apply its effect.
     */
    IDEMPOTENCY,

    /**
     * The numeric values carried on the proposal agree with each other and
     * with the deterministic resolution performed by the backend.
     */
    NUMERIC_AGREEMENT,

    /**
     * The proposal does not request disclosure of restricted or secret
     * information to an actor who is not entitled to receive it.
     */
    SECRET_DISCLOSURE
}
