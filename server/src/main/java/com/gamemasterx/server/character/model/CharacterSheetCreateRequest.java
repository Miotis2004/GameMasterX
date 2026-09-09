package com.gamemasterx.server.character.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * Request payload for creating a Character sheet.
 *
 * <p>Unlike {@link CharacterSheetUpdateRequest}, every field is required here so
 * that a freshly created character is fully specified. Bean Validation
 * annotations are enforced before the request reaches {@link
 * com.gamemasterx.server.character.service.CharacterService}.</p>
 *
 * <p>This DTO is the server-side validation boundary and is distinct from both
 * the persisted {@link Character} document entity and the API-facing {@link
 * CharacterDto}.</p>
 */
public class CharacterSheetCreateRequest {

    @NotBlank(message = "Character name must not be blank")
    @Size(max = 100, message = "Character name must not exceed 100 characters")
    private String name;

    /**
     * Campaign this character is associated with. Required on create: a character
     * may only exist inside a campaign the owner belongs to, which is validated in
     * {@link com.gamemasterx.server.character.service.CharacterService}.
     */
    @NotBlank(message = "Campaign identifier must not be blank")
    @Size(max = 100, message = "Campaign identifier must not exceed 100 characters")
    private String campaignId;

    @Size(max = 100, message = "Game system must not exceed 100 characters")
    private String gameSystem;

    @Min(value = 1, message = "Level must be at least 1")
    @Max(value = 30, message = "Level must not exceed 30")
    private int level;

    private AbilityScores abilityScores;
    private Proficiency proficiency;
    private HitPoints hitPoints;
    private ArmorClass armorClass;
    private List<Resource> resources;
    private List<InventoryItem> inventory;

    public CharacterSheetCreateRequest() {
    }

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
        this.resources = (resources != null) ? resources : new ArrayList<>();
    }

    public List<InventoryItem> getInventory() {
        return inventory;
    }

    public void setInventory(List<InventoryItem> inventory) {
        this.inventory = (inventory != null) ? inventory : new ArrayList<>();
    }

    // --- Nested value objects mirroring the aggregate. ---

    public static class AbilityScores {
        @Min(value = 1, message = "Strength must be between 1 and 30")
        @Max(value = 30, message = "Strength must be between 1 and 30")
        private int strength;
        @Min(value = 1, message = "Dexterity must be between 1 and 30")
        @Max(value = 30, message = "Dexterity must be between 1 and 30")
        private int dexterity;
        @Min(value = 1, message = "Constitution must be between 1 and 30")
        @Max(value = 30, message = "Constitution must be between 1 and 30")
        private int constitution;
        @Min(value = 1, message = "Intelligence must be between 1 and 30")
        @Max(value = 30, message = "Intelligence must be between 1 and 30")
        private int intelligence;
        @Min(value = 1, message = "Wisdom must be between 1 and 30")
        @Max(value = 30, message = "Wisdom must be between 1 and 30")
        private int wisdom;
        @Min(value = 1, message = "Charisma must be between 1 and 30")
        @Max(value = 30, message = "Charisma must be between 1 and 30")
        private int charisma;

        public int getStrength() {
            return strength;
        }

        public void setStrength(int strength) {
            this.strength = strength;
        }

        public int getDexterity() {
            return dexterity;
        }

        public void setDexterity(int dexterity) {
            this.dexterity = dexterity;
        }

        public int getConstitution() {
            return constitution;
        }

        public void setConstitution(int constitution) {
            this.constitution = constitution;
        }

        public int getIntelligence() {
            return intelligence;
        }

        public void setIntelligence(int intelligence) {
            this.intelligence = intelligence;
        }

        public int getWisdom() {
            return wisdom;
        }

        public void setWisdom(int wisdom) {
            this.wisdom = wisdom;
        }

        public int getCharisma() {
            return charisma;
        }

        public void setCharisma(int charisma) {
            this.charisma = charisma;
        }
    }

    public static class Proficiency {
        @Min(value = 1, message = "Proficiency level must be at least 1")
        private int level;
        @Min(value = 0, message = "Proficiency bonus must not be negative")
        private int proficiencyBonus;
        private List<String> proficiencies;

        public int getLevel() {
            return level;
        }

        public void setLevel(int level) {
            this.level = level;
        }

        public int getProficiencyBonus() {
            return proficiencyBonus;
        }

        public void setProficiencyBonus(int proficiencyBonus) {
            this.proficiencyBonus = proficiencyBonus;
        }

        public List<String> getProficiencies() {
            return proficiencies;
        }

        public void setProficiencies(List<String> proficiencies) {
            this.proficiencies = proficiencies;
        }
    }

    public static class HitPoints {
        @Min(value = 0, message = "Max hit points must not be negative")
        private int max;
        @Min(value = 0, message = "Current hit points must not be negative")
        private int current;
        @Min(value = 0, message = "Temporary hit points must not be negative")
        private int temporary;

        public int getMax() {
            return max;
        }

        public void setMax(int max) {
            this.max = max;
        }

        public int getCurrent() {
            return current;
        }

        public void setCurrent(int current) {
            this.current = current;
        }

        public int getTemporary() {
            return temporary;
        }

        public void setTemporary(int temporary) {
            this.temporary = temporary;
        }
    }

    public static class ArmorClass {
        @Min(value = 0, message = "Armor class must not be negative")
        private int value;
        private String type;

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    public static class Resource {
        private String name;
        @Min(value = 0, message = "Resource current must not be negative")
        private int current;
        @Min(value = 0, message = "Resource max must not be negative")
        private int max;
        private String description;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCurrent() {
            return current;
        }

        public void setCurrent(int current) {
            this.current = current;
        }

        public int getMax() {
            return max;
        }

        public void setMax(int max) {
            this.max = max;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    public static class InventoryItem {
        @NotBlank(message = "Inventory item name must not be blank")
        private String name;
        @Min(value = 0, message = "Quantity must not be negative")
        private int quantity;
        private String description;
        private Double weight;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Double getWeight() {
            return weight;
        }

        public void setWeight(Double weight) {
            this.weight = weight;
        }
    }
}
