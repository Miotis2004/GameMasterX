package com.gamemasterx.server.inventory.controller.request;

import jakarta.validation.constraints.Min;

/**
 * Request body for consuming a single consumable resource, as accepted by the
 * {@code POST /api/inventory/resources/{resourceId}/consume} endpoint.
 *
 * <p>The consumption is applied server-side by the backend-owned rules only
 * when the resource has enough available and every consumption requirement is
 * met; otherwise it is rejected with a clear diagnostic and nothing is
 * applied.</p>
 */
public record ConsumeResourceRequest(

        /**
         * The amount to consume. Must be positive.
         */
        @Min(1)
        int amount,

        /**
         * The consuming actor's character level, used to satisfy
         * minimum-level requirements. Optional.
         */
        Integer actorLevel,

        /**
         * The consuming actor's class name, used to satisfy
         * class-required requirements. Optional.
         */
        String actorClass,

        /** Optional free-form note recorded with the consumption. */
        String note) {
}
