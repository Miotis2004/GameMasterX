package com.gamemasterx.server.inventory.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Inventory aggregate root persisted as a MongoDB document.
 *
 * <p>The aggregate owns every {@link InventoryItem} and {@link
 * ConsumableResource} belonging to a single campaign. Keeping all items and
 * resources for a campaign inside one document means a batch of quantity and
 * consumable-resource changes can be applied and persisted atomically: either
 * the whole batch is committed or none of it is.</p>
 *
 * <p>The document carries a stable identifier, a schema version and a revision
 * counter. The {@link #revision} field is annotated with {@link Version} so
 * Spring Data MongoDB applies optimistic concurrency control, and it is also
 * recorded (before and after) on every {@link
 * com.gamemasterx.server.inventory.service.InventoryAuditService} entry so each
 * auditable change captures the revision before and after.</p>
 *
 * <p>The document is the persistence-only representation; the API-facing
 * representation is {@link com.gamemasterx.server.inventory.controller.response.InventorySnapshotDto}.</p>
 */
@Document(collection = "inventory")
public class Inventory {

    /** Current schema version for the Inventory document shape. */
    public static final int SCHEMA_VERSION = 1;

    @Id
    private String id;

    /** Logical schema version for this document. */
    private int schemaVersion;

    /**
     * Monotonic revision counter used for optimistic concurrency control and
     * recorded on every auditable change.
     */
    @Version
    private int revision;

    private Instant createdAt;
    private Instant updatedAt;

    /** Stable identifier of the campaign this inventory belongs to. */
    private String campaignId;

    /** Human-readable name of the inventory. */
    private String name;

    /** Inventory items. Never {@code null}. */
    private List<InventoryItem> items;

    /** Consumable resources. Never {@code null}. */
    private List<ConsumableResource> resources;

    public Inventory() {
        this.schemaVersion = SCHEMA_VERSION;
        this.items = new ArrayList<>();
        this.resources = new ArrayList<>();
    }

    /**
     * Full constructor used when materialising an inventory from persistence.
     */
    public Inventory(String id, int schemaVersion, int revision, Instant createdAt, Instant updatedAt,
                     String campaignId, String name, List<InventoryItem> items,
                     List<ConsumableResource> resources) {
        this.id = id;
        this.schemaVersion = (schemaVersion != 0) ? schemaVersion : SCHEMA_VERSION;
        this.revision = revision;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.campaignId = campaignId;
        this.name = (name != null) ? name : "Campaign inventory";
        this.items = (items != null) ? items : new ArrayList<>();
        this.resources = (resources != null) ? resources : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(String campaignId) {
        this.campaignId = campaignId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<InventoryItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void setItems(List<InventoryItem> items) {
        this.items = (items != null) ? items : new ArrayList<>();
    }

    public List<ConsumableResource> getResources() {
        return Collections.unmodifiableList(resources);
    }

    public void setResources(List<ConsumableResource> resources) {
        this.resources = (resources != null) ? resources : new ArrayList<>();
    }

    /**
     * Finds the inventory item with the given logical id.
     *
     * @param itemId the logical item id
     * @return an {@link Optional} carrying the item when present
     */
    public Optional<InventoryItem> findItem(String itemId) {
        for (InventoryItem item : items) {
            if (item.getId().equals(itemId)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    /**
     * Finds the consumable resource with the given logical id.
     *
     * @param resourceId the logical resource id
     * @return an {@link Optional} carrying the resource when present
     */
    public Optional<ConsumableResource> findResource(String resourceId) {
        for (ConsumableResource resource : resources) {
            if (resource.getId().equals(resourceId)) {
                return Optional.of(resource);
            }
        }
        return Optional.empty();
    }

    /**
     * Adds or replaces an inventory item by its logical id.
     *
     * @param item the item to add or replace
     */
    public void upsertItem(InventoryItem item) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId().equals(item.getId())) {
                items.set(i, item);
                return;
            }
        }
        items.add(item);
    }

    /**
     * Adds or replaces a consumable resource by its logical id.
     *
     * @param resource the resource to add or replace
     */
    public void upsertResource(ConsumableResource resource) {
        for (int i = 0; i < resources.size(); i++) {
            if (resources.get(i).getId().equals(resource.getId())) {
                resources.set(i, resource);
                return;
            }
        }
        resources.add(resource);
    }
}
