package com.gamemasterx.server.ai.operation;

/**
 * The closed set of kinds of state change an {@link ProposedOperation} may
 * describe.
 *
 * <p>These are backend-authoritative categories. The browser never defines or
 * extends this set; it can only reference an already-known operation type when
 * it submits a proposal. Every recognised value is declared here and only these
 * values can ever be validated against a {@link OperationSchema}, so a proposal
 * that names an unknown type is rejected before it is considered.</p>
 */
public enum OperationType {
    /** Deal damage to a target. */
    APPLY_DAMAGE,
    /** Restore hit points to a target. */
    APPLY_HEALING,
    /** Attach a condition to a target. */
    APPLY_CONDITION,
    /** Detach a condition from a target. */
    REMOVE_CONDITION,
    /** Move a participant to a new position. */
    MOVE_ACTOR,
    /** Change the quantity of a resource. */
    CHANGE_RESOURCE,
    /** Add narrative context around an action. */
    NARRATE,
    /** Flip a boolean flag on a target (for example toggling a light on/off). */
    SET_STATE
}
