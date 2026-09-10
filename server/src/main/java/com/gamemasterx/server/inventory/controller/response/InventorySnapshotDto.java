package com.gamemasterx.server.inventory.controller.response;

import com.gamemasterx.server.inventory.model.ConsumableResource;
import com.gamemasterx.server.inventory.model.Inventory;
import com.gamemasterx.server.inventory.model.InventoryItem;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * API-facing snapshot of an {@link Inventory} aggregate.
 *
 * <p>Distinct from the {@link Inventory} document entity: this is what the API
 * returns to callers, carrying the item quantities and consumable-resource
 * amounts after a change as well as the aggregate revision and the last
 * updated timestamp.</p>
 */
public record InventorySnapshotDto(
        String id,
        String campaignId,
        String name,
        int revision,
        Instant createdAt,
        Instant updatedAt,
        List<ItemSnapshot> items,
        List<ResourceSnapshot> resources) {

    /**
     * Projects a persisted {@link Inventory} into its API snapshot.
     *
     * @param inventory the inventory to project
     * @return the snapshot
     */
    public static InventorySnapshotDto from(Inventory inventory) {
        List<ItemSnapshot> items = inventory.getItems().stream()
                .map(item -> new ItemSnapshot(item.getId(), item.getItemId(), item.getName(), item.getQuantity()))
                .collect(Collectors.toList());
        List<ResourceSnapshot> resources = inventory.getResources().stream()
                .map(r -> new ResourceSnapshot(
                        r.getId(), r.getName(), r.getResourceType(), r.getCurrent(), r.getMax()))
                .collect(Collectors.toList());
        return new InventorySnapshotDto(
                inventory.getId(),
                inventory.getCampaignId(),
                inventory.getName(),
                inventory.getRevision(),
                inventory.getCreatedAt(),
                inventory.getUpdatedAt(),
                items,
                resources);
    }

    /**
     * Snapshot of a single inventory item's quantity.
     *
     * @param id     the logical item id
     * @param itemId the canonical item id
     * @param name   the item name
     * @param quantity the current quantity
     */
    public record ItemSnapshot(String id, String itemId, String name, int quantity) {
    }

    /**
     * Snapshot of a single consumable resource's current/max amounts.
     *
     * @param id          the logical resource id
     * @param name        the resource name
     * @param resourceType the resource kind
     * @param current     the current amount
     * @param max         the maximum amount
     */
    public record ResourceSnapshot(String id, String name, String resourceType, int current, int max) {
    }
}
