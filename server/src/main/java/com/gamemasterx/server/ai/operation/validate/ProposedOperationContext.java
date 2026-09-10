package com.gamemasterx.server.ai.operation.validate;

import com.gamemasterx.server.campaign.membership.model.MembershipRole;
import com.gamemasterx.server.encounter.model.Encounter;

/**
 * The runtime context a {@link com.gamemasterx.server.ai.operation.ProposedOperation}
 * is validated against.
 *
 * <p>Context carries everything the backend-authoritative checks need that the
 * proposal itself does not carry: who is acting, what they are acting on, and
 * the optimistic-concurrency / idempotency tokens. The {@link #encounter} is
 * loaded once by the caller and threaded through the checks so the same
 * authoritative document is observed by every dimension.</p>
 *
 * <p>The context is an immutable record; {@link #withEncounter} returns a copy
 * with a freshly loaded encounter so the orchestrator can thread a loaded
 * aggregate through the dependent checks without mutating the original.</p>
 */
public record ProposedOperationContext(

        /** The authenticated caller, established by the authentication filter. */
        String actor,

        /** The campaign the operation targets, or {@code null} when not applicable. */
        String campaignId,

        /** The encounter the operation targets, or {@code null} when not applicable. */
        String encounterId,

        /** The revision the caller expects to observe, or {@code null} to skip the check. */
        Long expectedRevision,

        /** The caller-supplied idempotency key, or {@code null} when the operation is not retry-guarded. */
        String idempotencyKey,

        /** The pre-loaded owning encounter, or {@code null} when not yet loaded. */
        Encounter encounter,

        /** The actor's role in the campaign, or {@code null} when unknown. */
        MembershipRole actorRole) {

    /**
     * @param encounter the encounter to associate with the returned context
     * @return a copy of this context carrying the supplied encounter
     */
    public ProposedOperationContext withEncounter(Encounter encounter) {
        return new ProposedOperationContext(
                actor, campaignId, encounterId, expectedRevision, idempotencyKey,
                encounter, actorRole);
    }

    /**
     * Convenience accessor for the pre-loaded owning encounter.
     *
     * @return the encounter previously associated with this context, or
     *         {@code null} when it has not been loaded
     */
    public Encounter getEncounter() {
        return encounter;
    }
}
