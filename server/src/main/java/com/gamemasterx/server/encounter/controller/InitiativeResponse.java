package com.gamemasterx.server.encounter.controller;

import com.gamemasterx.server.encounter.service.InitiativeService.InitiativeResult;

import java.util.List;

/**
 * Response body for an initiative-generation request, as documented in the
 * encounter initiative contract.
 *
 * <p>The response makes the initiative step fully auditable: {@link
 * #initiativeOrder()} is the deterministic, descending combat order that was
 * established from the resolved initiative scores, and {@link #results()} holds
 * one auditable {@link InitiativeResult} per participant. Each result records the
 * {@link InitiativeResult#rollMode()}, the {@link InitiativeResult#seed()} that
 * produced the roll (when seeded) and the full {@link InitiativeResult#dieResult()}
 * that was resolved through the dice subsystem, so the exact initiative order can
 * be replayed and verified.</p>
 */
public record InitiativeResponse(

        /** The participant identifiers ordered by descending initiative. */
        List<String> initiativeOrder,

        /** One auditable initiative roll per participant, in participant order. */
        List<InitiativeResult> results,

        /** The number of participants whose initiative was resolved. */
        int participantCount) {

    /**
     * Builds the response from the established initiative order and the resolved
     * per-participant results.
     *
     * @param initiativeOrder the deterministic combat order
     * @param results         the auditable per-participant initiative results
     * @return the immutable {@link InitiativeResponse}
     */
    public static InitiativeResponse of(List<String> initiativeOrder, List<InitiativeResult> results) {
        List<String> order = (initiativeOrder != null) ? List.copyOf(initiativeOrder) : List.of();
        List<InitiativeResult> resolved = (results != null) ? List.copyOf(results) : List.of();
        return new InitiativeResponse(order, resolved, resolved.size());
    }
}
