package com.gamemasterx.server.adventure.model;

import com.gamemasterx.server.adventure.model.Adventure.Branch;
import com.gamemasterx.server.adventure.model.Adventure.Chapter;
import com.gamemasterx.server.adventure.model.Adventure.Creature;
import com.gamemasterx.server.adventure.model.Adventure.Location;
import com.gamemasterx.server.adventure.model.Adventure.Npc;
import com.gamemasterx.server.adventure.model.Adventure.Objective;
import com.gamemasterx.server.adventure.model.Adventure.Reward;
import com.gamemasterx.server.adventure.model.Adventure.RewardCurrency;
import com.gamemasterx.server.adventure.model.Adventure.RewardItem;
import com.gamemasterx.server.adventure.model.Adventure.SecretNote;
import com.gamemasterx.server.adventure.model.Adventure.Scene;
import com.gamemasterx.server.adventure.model.Adventure.Status;
import com.gamemasterx.server.adventure.model.Adventure.Tag;
import com.gamemasterx.server.adventure.model.Adventure.WorldFact;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * API-facing representation of an Adventure.
 *
 * <p>This DTO is intentionally distinct from the persisted {@link Adventure}
 * MongoDB document entity. It defines only the fields that are safe to expose
 * over the wire and carries no persistence metadata such as the
 * optimistic-concurrency {@code revision} counter.</p>
 *
 * <p>It is used both as the shape returned by the adventure endpoints and as the
 * projection of an {@link Adventure} document into the API contract.</p>
 */
public class AdventureDto {

    private String id;
    private int schemaVersion;
    private Instant createdAt;
    private Instant updatedAt;
    private String title;
    private String description;
    private String gameSystem;
    private Integer recommendedPlayerCount;
    private Status status;
    private List<Chapter> chapters;
    private List<Location> locations;
    private List<Npc> npcs;
    private List<Creature> creatures;
    private List<Objective> objectives;
    private List<Branch> branches;
    private List<Reward> rewards;
    private List<SecretNote> secretNotes;
    private List<WorldFact> worldFacts;
    private List<Tag> tags;

    public AdventureDto() {
    }

    /**
     * Builds the API representation from a persisted document entity.
     */
    public static AdventureDto from(Adventure adventure) {
        AdventureDto dto = new AdventureDto();
        dto.id = adventure.getId();
        dto.schemaVersion = adventure.getSchemaVersion();
        dto.createdAt = adventure.getCreatedAt();
        dto.updatedAt = adventure.getUpdatedAt();
        dto.title = adventure.getTitle();
        dto.description = adventure.getDescription();
        dto.gameSystem = adventure.getGameSystem();
        dto.recommendedPlayerCount = adventure.getRecommendedPlayerCount();
        dto.status = adventure.getStatus();
        dto.chapters = chapterList(adventure.getChapters());
        dto.locations = locationList(adventure.getLocations());
        dto.npcs = npcList(adventure.getNpcs());
        dto.creatures = creatureList(adventure.getCreatures());
        dto.objectives = objectiveList(adventure.getObjectives());
        dto.branches = branchList(adventure.getBranches());
        dto.rewards = rewardList(adventure.getRewards());
        dto.secretNotes = secretNoteList(adventure.getSecretNotes());
        dto.worldFacts = worldFactList(adventure.getWorldFacts());
        dto.tags = tagList(adventure.getTags());
        return dto;
    }

    private static List<Chapter> chapterList(List<Chapter> chapters) {
        return (chapters != null) ? chapters : new ArrayList<>();
    }

    private static List<Location> locationList(List<Location> locations) {
        return (locations != null) ? locations : new ArrayList<>();
    }

    private static List<Npc> npcList(List<Npc> npcs) {
        return (npcs != null) ? npcs : new ArrayList<>();
    }

    private static List<Creature> creatureList(List<Creature> creatures) {
        return (creatures != null) ? creatures : new ArrayList<>();
    }

    private static List<Objective> objectiveList(List<Objective> objectives) {
        return (objectives != null) ? objectives : new ArrayList<>();
    }

    private static List<Branch> branchList(List<Branch> branches) {
        return (branches != null) ? branches : new ArrayList<>();
    }

    private static List<Reward> rewardList(List<Reward> rewards) {
        return (rewards != null) ? rewards : new ArrayList<>();
    }

    private static List<SecretNote> secretNoteList(List<SecretNote> secretNotes) {
        return (secretNotes != null) ? secretNotes : new ArrayList<>();
    }

    private static List<WorldFact> worldFactList(List<WorldFact> worldFacts) {
        return (worldFacts != null) ? worldFacts : new ArrayList<>();
    }

    private static List<Tag> tagList(List<Tag> tags) {
        return (tags != null) ? tags : new ArrayList<>();
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public Integer getRecommendedPlayerCount() {
        return recommendedPlayerCount;
    }

    public void setRecommendedPlayerCount(Integer recommendedPlayerCount) {
        this.recommendedPlayerCount = recommendedPlayerCount;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public List<Chapter> getChapters() {
        return chapters;
    }

    public void setChapters(List<Chapter> chapters) {
        this.chapters = (chapters != null) ? chapters : new ArrayList<>();
    }

    public List<Location> getLocations() {
        return locations;
    }

    public void setLocations(List<Location> locations) {
        this.locations = (locations != null) ? locations : new ArrayList<>();
    }

    public List<Npc> getNpcs() {
        return npcs;
    }

    public void setNpcs(List<Npc> npcs) {
        this.npcs = (npcs != null) ? npcs : new ArrayList<>();
    }

    public List<Creature> getCreatures() {
        return creatures;
    }

    public void setCreatures(List<Creature> creatures) {
        this.creatures = (creatures != null) ? creatures : new ArrayList<>();
    }

    public List<Objective> getObjectives() {
        return objectives;
    }

    public void setObjectives(List<Objective> objectives) {
        this.objectives = (objectives != null) ? objectives : new ArrayList<>();
    }

    public List<Branch> getBranches() {
        return branches;
    }

    public void setBranches(List<Branch> branches) {
        this.branches = (branches != null) ? branches : new ArrayList<>();
    }

    public List<Reward> getRewards() {
        return rewards;
    }

    public void setRewards(List<Reward> rewards) {
        this.rewards = (rewards != null) ? rewards : new ArrayList<>();
    }

    public List<SecretNote> getSecretNotes() {
        return secretNotes;
    }

    public void setSecretNotes(List<SecretNote> secretNotes) {
        this.secretNotes = (secretNotes != null) ? secretNotes : new ArrayList<>();
    }

    public List<WorldFact> getWorldFacts() {
        return worldFacts;
    }

    public void setWorldFacts(List<WorldFact> worldFacts) {
        this.worldFacts = (worldFacts != null) ? worldFacts : new ArrayList<>();
    }

    public List<Tag> getTags() {
        return tags;
    }

    public void setTags(List<Tag> tags) {
        this.tags = (tags != null) ? tags : new ArrayList<>();
    }

    /**
     * Copies the mutable, API-facing content from a creation/update request shape
     * (which shares the same nested value-object types) into this DTO.
     */
    public void fromContent(Adventure source) {
        this.title = source.getTitle();
        this.description = source.getDescription();
        this.gameSystem = source.getGameSystem();
        this.recommendedPlayerCount = source.getRecommendedPlayerCount();
        this.status = source.getStatus();
        this.chapters = chapterList(source.getChapters());
        this.locations = locationList(source.getLocations());
        this.npcs = npcList(source.getNpcs());
        this.creatures = creatureList(source.getCreatures());
        this.objectives = objectiveList(source.getObjectives());
        this.branches = branchList(source.getBranches());
        this.rewards = rewardList(source.getRewards());
        this.secretNotes = secretNoteList(source.getSecretNotes());
        this.worldFacts = worldFactList(source.getWorldFacts());
        this.tags = tagList(source.getTags());
    }
}
