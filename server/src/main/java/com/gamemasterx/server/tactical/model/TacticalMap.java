package com.gamemasterx.server.tactical.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Tactical map aggregate persisted as a MongoDB document.
 *
 * <p>This entity carries a stable identifier, a schema version, a revision counter
 * for optimistic concurrency, and created/updated timestamps. The grid definition
 * and tokens are embedded value objects.</p>
 */
@Document(collection = "tactical_maps")
public class TacticalMap {

    public static final int SCHEMA_VERSION = 1;

    @Id
    private String id;

    private int schemaVersion;

    @Version
    private int revision;

    private Instant createdAt;
    private Instant updatedAt;

    private String campaignId;
    private String name;

    private GridDefinition gridDefinition;

    private List<Token> tokens;

    public TacticalMap() {
        this.schemaVersion = SCHEMA_VERSION;
        this.tokens = new ArrayList<>();
    }

    public TacticalMap(String id, int schemaVersion, int revision, Instant createdAt, Instant updatedAt,
                       String campaignId, String name, GridDefinition gridDefinition, List<Token> tokens) {
        this.id = id;
        this.schemaVersion = schemaVersion;
        this.revision = revision;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.campaignId = campaignId;
        this.name = name;
        this.gridDefinition = gridDefinition;
        this.tokens = (tokens != null) ? tokens : new ArrayList<>();
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

    public GridDefinition getGridDefinition() {
        return gridDefinition;
    }

    public void setGridDefinition(GridDefinition gridDefinition) {
        this.gridDefinition = gridDefinition;
    }

    public List<Token> getTokens() {
        return tokens;
    }

    public void setTokens(List<Token> tokens) {
        this.tokens = (tokens != null) ? tokens : new ArrayList<>();
    }
}
