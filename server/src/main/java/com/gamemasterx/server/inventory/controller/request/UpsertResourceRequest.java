package com.gamemasterx.server.inventory.controller.request;

import com.gamemasterx.server.inventory.model.ConsumableResource;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Request body for adding or replacing a single consumable resource, as
 * accepted by the {@code POST /api/inventory/{campaignId}/resources} endpoint.
 */
public record UpsertResourceRequest(

        /**
         * The logical id of the resource. Must be unique within the campaign.
         */
        @NotBlank
        String id,

        /**
         * The human-readable resource name.
         */
        @NotBlank
        String name,

        /**
         * The resource kind, for example {@code "spellSlot"} or {@code "charge"}.
         */
        String resourceType,

        /**
         * The starting current amount. Must be within {@code [0, max]}.
         */
        @Min(0)
        int current,

        /**
         * The maximum amount. Must be non-negative.
         */
        @Min(0)
        int max,

        /**
         * Optional ordered list of consumption requirements.
         */
        List<ConsumableRequirementRequest> requirements) {

    /**
     * Converts this request into a persisted {@link ConsumableResource}.
     *
     * @param id the caller-supplied logical id (overrides the request's own id)
     * @return the resource
     */
    public ConsumableResource toResource() {
        List<ConsumableResource.Requirement> converted = (requirements() != null)
                ? requirements().stream().map(req -> req.toRequirement()).toList()
                : null;
        return new ConsumableResource(id, name, resourceType, current, max, converted);
    }
}
