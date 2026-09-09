package com.gamemasterx.server.character.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request payload for editing an existing Character sheet.
 *
 * <p>Every field is optional here so that callers can perform a partial update.
 * Bean Validation ignores {@code null} values, so an omitted field is left
 * unchanged; a provided field is still validated where a constraint is present.
 * Value-object fields (ability scores, hit points, and so on) are replaced as a
 * whole when supplied.</p>
 *
 * <p>Unlike {@link CharacterSheetCreateRequest}, no field is required and the
 * nested value objects use {@code null}-tolerant constraints.</p>
 */
public class CharacterSheetUpdateRequest {

    @Size(max = 100, message = "Character name must not exceed 100 characters")
    private String name;

    /**
     * Campaign this character is associated with. Optional on update; when
     * supplied it is validated (non-blank) and re-verified against the owner's
     * membership in that campaign by {@link
     * com.gamemasterx.server.character.service.CharacterService}.
     */
    @NotBlank(message = "Campaign identifier must not be blank")
    @Size(max = 100, message = "Campaign identifier must not exceed 100 characters")
    private String campaignId;

    @Size(max = 100, message = "Game system must not exceed 100 characters")
    private String gameSystem;

    @Min(value = 1, message = "Level must be at least 1")
    @Max(value = 30, message = "Level must not exceed 30")
    private Integer level;

    private CharacterSheetCreateRequest.AbilityScores abilityScores;
    private CharacterSheetCreateRequest.Proficiency proficiency;
    private CharacterSheetCreateRequest.HitPoints hitPoints;
    private CharacterSheetCreateRequest.ArmorClass armorClass;
    private List<CharacterSheetCreateRequest.Resource> resources;
    private List<CharacterSheetCreateRequest.InventoryItem> inventory;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public CharacterSheetCreateRequest.AbilityScores getAbilityScores() {
        return abilityScores;
    }

    public void setAbilityScores(CharacterSheetCreateRequest.AbilityScores abilityScores) {
        this.abilityScores = abilityScores;
    }

    public CharacterSheetCreateRequest.Proficiency getProficiency() {
        return proficiency;
    }

    public void setProficiency(CharacterSheetCreateRequest.Proficiency proficiency) {
        this.proficiency = proficiency;
    }

    public CharacterSheetCreateRequest.HitPoints getHitPoints() {
        return hitPoints;
    }

    public void setHitPoints(CharacterSheetCreateRequest.HitPoints hitPoints) {
        this.hitPoints = hitPoints;
    }

    public CharacterSheetCreateRequest.ArmorClass getArmorClass() {
        return armorClass;
    }

    public void setArmorClass(CharacterSheetCreateRequest.ArmorClass armorClass) {
        this.armorClass = armorClass;
    }

    public List<CharacterSheetCreateRequest.Resource> getResources() {
        return resources;
    }

    public void setResources(List<CharacterSheetCreateRequest.Resource> resources) {
        this.resources = resources;
    }

    public List<CharacterSheetCreateRequest.InventoryItem> getInventory() {
        return inventory;
    }

    public void setInventory(List<CharacterSheetCreateRequest.InventoryItem> inventory) {
        this.inventory = inventory;
    }
}
