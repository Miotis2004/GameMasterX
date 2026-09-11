package com.gamemasterx.server.tactical.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * API-facing representation of a {@link TacticalMap}.
 */
public class TacticalMapDto {

    private String id;
    private int schemaVersion;
    private Instant createdAt;
    private Instant updatedAt;
    private String campaignId;
    private String name;
    private GridDefinitionDto gridDefinition;
    private List<TokenDto> tokens;
    private int revision;

    public TacticalMapDto() {
    }

    public static TacticalMapDto from(TacticalMap map) {
        TacticalMapDto dto = new TacticalMapDto();
        dto.id = map.getId();
        dto.schemaVersion = map.getSchemaVersion();
        dto.createdAt = map.getCreatedAt();
        dto.updatedAt = map.getUpdatedAt();
        dto.campaignId = map.getCampaignId();
        dto.name = map.getName();
        dto.revision = map.getRevision();
        if (map.getGridDefinition() != null) {
            dto.gridDefinition = GridDefinitionDto.from(map.getGridDefinition());
        }
        List<TokenDto> tokenDtos = new ArrayList<>();
        for (Token t : map.getTokens()) {
            tokenDtos.add(TokenDto.from(t));
        }
        dto.tokens = tokenDtos;
        return dto;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public GridDefinitionDto getGridDefinition() {
        return gridDefinition;
    }

    public void setGridDefinition(GridDefinitionDto gridDefinition) {
        this.gridDefinition = gridDefinition;
    }

    public List<TokenDto> getTokens() {
        return tokens;
    }

    public void setTokens(List<TokenDto> tokens) {
        this.tokens = tokens;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }
}
