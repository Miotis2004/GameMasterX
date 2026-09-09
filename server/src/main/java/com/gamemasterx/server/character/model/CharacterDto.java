package com.gamemasterx.server.character.model;

import com.gamemasterx.server.character.model.Character.AbilityModifiers;
import com.gamemasterx.server.character.model.Character.AbilityScores;
import com.gamemasterx.server.character.model.Character.ArmorClass;
import com.gamemasterx.server.character.model.Character.HitPoints;
import com.gamemasterx.server.character.model.Character.InventoryItem;
import com.gamemasterx.server.character.model.Character.Proficiency;
import com.gamemasterx.server.character.model.Character.Resource;

import java.time.Instant;
import java.util.List;

/**
 * API-facing representation of a Character sheet.
 *
 * <p>This DTO is intentionally distinct from the persisted {@link Character}
 * MongoDB document entity. It defines only the fields that are safe to expose
 * over the wire and carries the optimistic-concurrency {@code revision} counter
 * out of the API surface. It is used both as the shape returned by the character
 * endpoints and as the projection of a {@link Character} document into the API
 * contract.</p>
 *
 * <p>Unlike most DTOs it does carry the {@code schemaVersion}, {@code createdAt}
 * and {@code updatedAt} metadata because these are part of the character-sheet
 * contract exposed to clients.</p>
 */
public class CharacterDto {

    private String id;
    private int schemaVersion;
    private Instant createdAt;
    private Instant updatedAt;
    private String name;
    private String ownerId;
    private String campaignId;
    private String gameSystem;
    private int level;
    private AbilityScores abilityScores;
    private AbilityModifiers abilityModifiers;
    private Proficiency proficiency;
    private HitPoints hitPoints;
    private ArmorClass armorClass;
    private List<Resource> resources;
    private List<InventoryItem> inventory;

    public CharacterDto() {
    }

    /**
     * Builds the API representation from a persisted document entity.
     */
    public static CharacterDto from(Character character) {
        CharacterDto dto = new CharacterDto();
        dto.id = character.getId();
        dto.schemaVersion = character.getSchemaVersion();
        dto.createdAt = character.getCreatedAt();
        dto.updatedAt = character.getUpdatedAt();
        dto.name = character.getName();
        dto.ownerId = character.getOwnerId();
        dto.campaignId = character.getCampaignId();
        dto.gameSystem = character.getGameSystem();
        dto.level = character.getLevel();
        dto.abilityScores = character.getAbilityScores();
        dto.abilityModifiers = character.getAbilityModifiers();
        dto.proficiency = character.getProficiency();
        dto.hitPoints = character.getHitPoints();
        dto.armorClass = character.getArmorClass();
        dto.resources = character.getResources();
        dto.inventory = character.getInventory();
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(String campaignId) {
        this.campaignId = campaignId;
    }

    public String getGameSystem() {
        return gameSystem;
    }

    public void setGameSystem(String gameSystem) {
        this.gameSystem = gameSystem;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public AbilityScores getAbilityScores() {
        return abilityScores;
    }

    public void setAbilityScores(AbilityScores abilityScores) {
        this.abilityScores = abilityScores;
    }

    public AbilityModifiers getAbilityModifiers() {
        return abilityModifiers;
    }

    public void setAbilityModifiers(AbilityModifiers abilityModifiers) {
        this.abilityModifiers = abilityModifiers;
    }

    public Proficiency getProficiency() {
        return proficiency;
    }

    public void setProficiency(Proficiency proficiency) {
        this.proficiency = proficiency;
    }

    public HitPoints getHitPoints() {
        return hitPoints;
    }

    public void setHitPoints(HitPoints hitPoints) {
        this.hitPoints = hitPoints;
    }

    public ArmorClass getArmorClass() {
        return armorClass;
    }

    public void setArmorClass(ArmorClass armorClass) {
        this.armorClass = armorClass;
    }

    public List<Resource> getResources() {
        return resources;
    }

    public void setResources(List<Resource> resources) {
        this.resources = resources;
    }

    public List<InventoryItem> getInventory() {
        return inventory;
    }

    public void setInventory(List<InventoryItem> inventory) {
        this.inventory = inventory;
    }
}
