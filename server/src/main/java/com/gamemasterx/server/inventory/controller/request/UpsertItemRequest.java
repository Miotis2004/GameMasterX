package com.gamemasterx.server.inventory.controller.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for adding or replacing a single inventory item, as accepted by
 * the {@code POST /api/inventory/{campaignId}/items} endpoint.
 */
public record UpsertItemRequest(

        /**
         * The logical id of the item. Must be unique within the campaign.
         */
        @NotBlank
        String id,

        /**
         * The canonical item id, shared across campaigns for the same item.
         */
        String itemId,

        /**
         * The human-readable item name.
         */
        @NotBlank
        String name,

        /**
         * The starting quantity. Must be non-negative.
         */
        @Min(0)
        int quantity,

        /**
         * Optional upper bound on how many of this item may be held, or
         * {@code null} when unbounded.
         */
        Integer capacity) {
}
