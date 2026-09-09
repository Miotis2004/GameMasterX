package com.gamemasterx.server.campaign.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

/**
 * Campaign aggregate root persisted as a MongoDB document.
 *
 * <p>This is the persistence-only representation of the Campaign aggregate. It
 * is deliberately distinct from {@link CampaignDto}: the document entity is what
 * Spring Data MongoDB reads from and writes to the {@code campaigns}
 * collection, while {@link CampaignDto} is the API-facing representation that is
 * returned to (and accepted from) callers. Keeping the two types separate
 * prevents leaking persistence concerns (such as the optimistic-concurrency
 * revision counter) into the API contract.</p>
 *
 * <p>The document carries a stable identifier, a schema version, a revision
 * counter and created/updated timestamps. The {@link #revision} field is also
 * annotated with {@link Version} so that Spring Data MongoDB applies optimistic
 * concurrency control to the aggregate.</p>
 */
@Document(collection = "campaigns")
public class Campaign {

    @Id
    private String id;

    /**
     * Logical schema version for this document. Bumped when the persisted
     * shape of the aggregate changes in a backwards-incompatible way.
     */
    private int schemaVersion;

    /**
     * Monotonic revision counter used for optimistic concurrency control.
     * Managed automatically by Spring Data MongoDB because of {@link Version}.
     */
    @Version
    private int revision;

    private Instant createdAt;
    private Instant updatedAt;

    private String name;
    private String description;
    private String gameSystem;
    private CampaignStatus status;
    private int maxPlayers;
    /**
     * Stable identifier of the authored adventure this campaign has selected.
     * A campaign may reference at most one adventure at a time; a {@code null}
     * or blank value means no adventure has been selected yet. Selecting an
     * adventure is a {@code GAME_MASTER} operation and is surfaced by the
     * campaign dashboard.
     */
    private String adventureId;

    public Campaign() {
    }

    public Campaign(String id, int schemaVersion, int revision, Instant createdAt, Instant updatedAt,
                    String name, String description, String gameSystem, CampaignStatus status, int maxPlayers) {
        this.id = id;
        this.schemaVersion = schemaVersion;
        this.revision = revision;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.name = name;
        this.description = description;
        this.gameSystem = gameSystem;
        this.status = status;
        this.maxPlayers = maxPlayers;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getGameSystem() {
        return gameSystem;
    }

    public void setGameSystem(String gameSystem) {
        this.gameSystem = gameSystem;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public void setStatus(CampaignStatus status) {
        this.status = status;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public String getAdventureId() {
        return adventureId;
    }

    public void setAdventureId(String adventureId) {
        this.adventureId = adventureId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Campaign campaign = (Campaign) o;
        return Objects.equals(id, campaign.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
