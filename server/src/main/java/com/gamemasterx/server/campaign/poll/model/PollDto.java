package com.gamemasterx.server.campaign.poll.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API-facing representation of a Poll.
 *
 * <p>The DTO projects the persisted document into a shape suitable for API
 * consumers. Vote counts are aggregated per option; individual voter mapping
 * is not exposed.</p>
 */
public class PollDto {

    private String id;
    private int schemaVersion;
    private Instant createdAt;
    private Instant updatedAt;
    private String campaignId;
    private String question;
    private List<String> options;
    private Map<String, Integer> voteCounts;
    private PollStatus status;
    private String createdBy;

    public PollDto() {
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

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }

    public Map<String, Integer> getVoteCounts() {
        return voteCounts;
    }

    public void setVoteCounts(Map<String, Integer> voteCounts) {
        this.voteCounts = voteCounts;
    }

    public PollStatus getStatus() {
        return status;
    }

    public void setStatus(PollStatus status) {
        this.status = status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * Projects a persisted Poll document into an API DTO.
     */
    public static PollDto from(Poll poll) {
        PollDto dto = new PollDto();
        dto.setId(poll.getId());
        dto.setSchemaVersion(poll.getSchemaVersion());
        dto.setCreatedAt(poll.getCreatedAt());
        dto.setUpdatedAt(poll.getUpdatedAt());
        dto.setCampaignId(poll.getCampaignId());
        dto.setQuestion(poll.getQuestion());
        dto.setOptions(poll.getOptions());
        dto.setStatus(poll.getStatus());
        dto.setCreatedBy(poll.getCreatedBy());

        Map<String, Integer> counts = new HashMap<>();
        if (poll.getOptions() != null && poll.getVotes() != null) {
            for (int i = 0; i < poll.getOptions().size(); i++) {
                counts.put(poll.getOptions().get(i), 0);
            }
            for (Integer optionIndex : poll.getVotes().values()) {
                if (optionIndex != null && optionIndex >= 0 && optionIndex < poll.getOptions().size()) {
                    String option = poll.getOptions().get(optionIndex);
                    counts.put(option, counts.getOrDefault(option, 0) + 1);
                }
            }
        }
        dto.setVoteCounts(counts);
        return dto;
    }
}
