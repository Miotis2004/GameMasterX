package com.gamemasterx.server.campaign.membership.model;

import com.gamemasterx.server.campaign.model.Campaign;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

/**
 * Campaign membership aggregate root persisted as a MongoDB document.
 *
 * <p>A membership links a stable user identifier to a stable campaign
 * identifier together with the {@link MembershipRole} that user holds in that
 * campaign. It is the unit of authorization for the Campaign aggregate: every
 * authorization decision is derived from the roles a user holds across the
 * campaigns they belong to.</p>
 *
 * <p>This is the persistence-only representation and is deliberately distinct
 * from {@link MembershipDto}: the document entity is what Spring Data MongoDB
 * reads from and writes to the {@code campaign_memberships} collection, while
 * {@link MembershipDto} is the API-facing representation. Keeping the two types
 * separate prevents leaking persistence concerns (such as the
 * optimistic-concurrency revision counter) into the API contract.</p>
 *
 * <p>The document carries a stable identifier, a schema version, a revision
 * counter and created/updated timestamps. The {@link #revision} field is also
 * annotated with {@link Version} so that Spring Data MongoDB applies optimistic
 * concurrency control to the aggregate. Exactly one membership may exist for a
 * given ({@code campaignId}, {@code userId}) pair, enforced by the unique
 * compound index below.</p>
 */
@Document(collection = "campaign_memberships")
@CompoundIndex(def = "campaignId_userId", unique = true, name = "idx_campaign_user")
public class Membership {

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

    /**
     * Stable identifier of the user who is a member. This is the user's durable
     * identifier (not an ephemeral session value) so that membership survives
     * re-login and profile changes.
     */
    @Indexed
    private String userId;

    /**
     * Stable identifier of the campaign the user belongs to.
     */
    @Indexed
    private String campaignId;

    private MembershipRole role;

    /**
     * Lifecycle status of this membership. Defaults to {@link
     * MembershipStatus#ACTIVE}. Redeeming a join code may create a {@link
     * MembershipStatus#PENDING} membership that requires approval before the
     * joiner is fully admitted.
     */
    private MembershipStatus status;

    public Membership() {
        this.status = MembershipStatus.ACTIVE;
    }

    public Membership(String id, int schemaVersion, int revision, Instant createdAt, Instant updatedAt,
                      String userId, String campaignId, MembershipRole role, MembershipStatus status) {
        this.id = id;
        this.schemaVersion = schemaVersion;
        this.revision = revision;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.userId = userId;
        this.campaignId = campaignId;
        this.role = role;
        this.status = status;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public void setStatus(MembershipStatus status) {
        this.status = status;
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(String campaignId) {
        this.campaignId = campaignId;
    }

    public MembershipRole getRole() {
        return role;
    }

    public void setRole(MembershipRole role) {
        this.role = role;
    }

    /**
     * Returns the highest-authority role this membership grants, since a
     * membership holds exactly one role. Convenience accessor mirroring the
     * {@link MembershipRole#getLevel()} hierarchy.
     *
     * @return the role's authority level
     */
    public int getAuthorityLevel() {
        return role == null ? -1 : role.getLevel();
    }

    /**
     * @param requiredRole the minimum role required to perform an action
     * @return {@code true} if this membership's role authorizes the action
     * according to the role hierarchy
     */
    public boolean isAuthorizedFor(MembershipRole requiredRole) {
        return role != null && role.isAtLeast(requiredRole);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Membership that = (Membership) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
