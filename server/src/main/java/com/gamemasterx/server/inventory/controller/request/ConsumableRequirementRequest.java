package com.gamemasterx.server.inventory.controller.request;

import com.gamemasterx.server.inventory.model.ConsumableResource;
import jakarta.validation.constraints.NotNull;

/**
 * Request body describing a single consumption requirement, as accepted by the
 * {@code POST /api/inventory/{campaignId}/resources} endpoint.
 */
public record ConsumableRequirementRequest(

        /**
         * The requirement kind. One of {@code MIN_CURRENT}, {@code
         * MAX_SINGLE_USE}, {@code MIN_LEVEL} or {@code CLASS_REQUIRED}.
         */
        @NotNull
        ConsumableResource.Requirement.RequirementType type,

        /**
         * The numeric threshold for the requirement. Required for every kind
         * except {@code CLASS_REQUIRED}.
         */
        int value,

        /**
         * The textual value for a {@code CLASS_REQUIRED} requirement (the class
         * name an actor must belong to), or {@code null} otherwise.
         */
        String stringValue) {

    /**
     * Converts this request into a persisted {@link ConsumableResource.Requirement}.
     *
     * @return the requirement
     */
    public ConsumableResource.Requirement toRequirement() {
        if (type == ConsumableResource.Requirement.RequirementType.CLASS_REQUIRED) {
            return ConsumableResource.Requirement.classRequired(
                    (stringValue != null && !stringValue.isBlank()) ? stringValue : "");
        }
        return ConsumableResource.Requirement.numeric(type, value);
    }
}
