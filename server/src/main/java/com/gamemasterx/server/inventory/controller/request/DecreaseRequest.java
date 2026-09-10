package com.gamemasterx.server.inventory.controller.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request body for a batch decrement of inventory quantities, as accepted by
 * the {@code POST /api/inventory/decrease} endpoint.
 *
 * <p>Each line decrements the quantity of one item. The batch is applied
 * atomically: if any line would drive an item below {@code 0} the whole batch
 * is rejected, so no partial commit occurs.</p>
 */
public record DecreaseRequest(

        /**
         * The items to decrement. Must be non-empty.
         */
        @NotEmpty
        List<Line> changes,

        /** Optional free-form note recorded with the batch. */
        String note) {

    /**
     * A single requested decrement.
     *
     * @param itemId the logical item id to decrement; must not be blank
     * @param amount the amount to decrement; must be non-negative
     */
    public record Line(

            /**
             * The logical item id to decrement.
             */
            @NotBlank
            String itemId,

            /**
             * The amount to decrement. Must be non-negative.
             */
            @Min(0)
            int amount) {
    }
}
