package com.gamemasterx.server.campaign.model;

/**
 * Lifecycle states a Campaign can be in.
 */
public enum CampaignStatus {
    DRAFT,
    OPEN,
    ACTIVE,
    COMPLETED,
    CANCELLED,
    /**
     * Archived campaigns are retained for historical reference but are no
     * longer part of active play. Archiving is distinct from completing or
     * cancelling a campaign.
     */
    ARCHIVED
}
