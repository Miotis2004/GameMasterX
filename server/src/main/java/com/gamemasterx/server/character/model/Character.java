package com.gamemasterx.server.character.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Character aggregate root persisted as a MongoDB document.
 *
 * <p>This is the persistence-only representation of the Character aggregate. It
 * is deliberately distinct from {@link CharacterDto}: the document entity is
 * what Spring Data MongoDB reads from and writes to the {@code characters}
 * collection, while {@link CharacterDto} is the API-facing representation that
 * is returned to (and accepted from) callers. Keeping the two types separate
 * prevents leaking persistence concerns (such as the optimistic-concurrency
 * revision counter) into the API contract.</p>
 *
 * <p>The document carries a stable identifier, a schema version, a revision
 * counter and created/updated timestamps. The {@link #revision} field is also
 * annotated with {@link Version} so that Spring Data MongoDB applies optimistic
 * concurrency control to the aggregate.</p>
 *
 * <p>The character sheet is composed of embedded value objects: {@link
 * AbilityScores}/{@link AbilityModifiers}, {@link Proficiency}, {@link
 * HitPoints}, {@link ArmorClass}, a list of {@link Resource}s and {@link
 * InventoryItem}s. These are nested value objects that belong to the aggregate
 * and have no independent identity.</p>
 */
@Document(collection = "characters")
public class Character {

    /** Current schema version for the Character document shape. */
    public static final int SCHEMA_VERSION = 1;

    @Id
    private String id;

    /** Logical schema version for this document. */
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
    /**
     * Stable identifier of the user who owns this character. Every character is
     * owned by exactly one user and this reference is established and validated
     * on both create and edit.
     */
    private String ownerId;
    /**
     * Stable identifier of the campaign this character is associated with. A
     * character may only be associated with a campaign whose owner this is,
     * which is enforced on create and edit.
     */
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

    public Character() {
        this.schemaVersion = SCHEMA_VERSION;
        this.revision = 1;
        this.resources = new ArrayList<>();
        this.inventory = new ArrayList<>();
    }

    /**
     * Full constructor used when materialising a character from persistence.
     */
    public Character(String id, int schemaVersion, int revision, Instant createdAt, Instant updatedAt,
                     String name, String ownerId, String campaignId, String gameSystem, int level,
                     AbilityScores abilityScores, AbilityModifiers abilityModifiers,
                     Proficiency proficiency, HitPoints hitPoints, ArmorClass armorClass,
                     List<Resource> resources, List<InventoryItem> inventory) {
        this.id = id;
        this.schemaVersion = schemaVersion;
        this.revision = revision;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.name = name;
        this.ownerId = ownerId;
        this.campaignId = campaignId;
        this.gameSystem = gameSystem;
        this.level = level;
        this.abilityScores = abilityScores;
        this.abilityModifiers = abilityModifiers;
        this.proficiency = proficiency;
        this.hitPoints = hitPoints;
        this.armorClass = armorClass;
        this.resources = (resources != null) ? resources : new ArrayList<>();
        this.inventory = (inventory != null) ? inventory : new ArrayList<>();
    }

    /**
     * Recomputes every {@link AbilityModifier} from the current {@link
     * AbilityScores}. Called automatically before persisting so the stored
     * modifiers never drift from the ability scores.
     */
    public void recomputeAbilityModifiers() {
        if (this.abilityScores == null) {
            this.abilityModifiers = new AbilityModifiers();
            return;
        }
        this.abilityModifiers = this.abilityModifiers.fromScores(this.abilityScores);
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
        this.resources = (resources != null) ? resources : new ArrayList<>();
    }

    public List<InventoryItem> getInventory() {
        return inventory;
    }

    public void setInventory(List<InventoryItem> inventory) {
        this.inventory = (inventory != null) ? inventory : new ArrayList<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Character character = (Character) o;
        return Objects.equals(id, character.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * Six base ability scores for a character. Valid values are in the inclusive
     * range {@code [1, 30]}; values outside that range are rejected by {@link
     * #validate()}.
     */
    public static class AbilityScores {
        private int strength;
        private int dexterity;
        private int constitution;
        private int intelligence;
        private int wisdom;
        private int charisma;

        public AbilityScores() {
        }

        public AbilityScores(int strength, int dexterity, int constitution,
                             int intelligence, int wisdom, int charisma) {
            this.strength = strength;
            this.dexterity = dexterity;
            this.constitution = constitution;
            this.intelligence = intelligence;
            this.wisdom = wisdom;
            this.charisma = charisma;
        }

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

        public void validate() {
            for (int v : new int[] {strength, dexterity, constitution, intelligence, wisdom, charisma}) {
                if (v < 1 || v > 30) {
                    throw new IllegalArgumentException("Ability scores must be in the range 1-30");
                }
            }
        }
    }

    /**
     * Derived ability modifiers, one per ability. The stored values are kept in
     * sync with {@link AbilityScores} via {@link Character#recomputeAbilityModifiers()}.
     */
    public static class AbilityModifiers {
        private int strength;
        private int dexterity;
        private int constitution;
        private int intelligence;
        private int wisdom;
        private int charisma;

        public AbilityModifiers() {
        }

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

        /**
         * Derives each modifier from the corresponding score using the standard
         * {@code floor((score - 10) / 2)} formula.
         */
        public AbilityModifiers fromScores(AbilityScores scores) {
            AbilityModifiers mods = new AbilityModifiers();
            mods.setStrength(modify(scores.getStrength()));
            mods.setDexterity(modify(scores.getDexterity()));
            mods.setConstitution(modify(scores.getConstitution()));
            mods.setIntelligence(modify(scores.getIntelligence()));
            mods.setWisdom(modify(scores.getWisdom()));
            mods.setCharisma(modify(scores.getCharisma()));
            return mods;
        }

        private static int modify(int score) {
            return Math.floorDiv(score - 10, 2);
        }
    }

    /**
     * Proficiency information: the numeric proficiency bonus applied to
     * professed skills plus the list of professed items.
     */
    public static class Proficiency {
        private int level;
        private int proficiencyBonus;
        private List<String> proficiencies;

        public Proficiency() {
            this.proficiencies = new ArrayList<>();
        }

        public Proficiency(int level, int proficiencyBonus, List<String> proficiencies) {
            this.level = level;
            this.proficiencyBonus = proficiencyBonus;
            this.proficiencies = (proficiencies != null) ? proficiencies : new ArrayList<>();
        }

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
            this.proficiencies = (proficiencies != null) ? proficiencies : new ArrayList<>();
        }
    }

    /**
     * Hit point state for the character.
     */
    public static class HitPoints {
        private int max;
        private int current;
        private int temporary;

        public HitPoints() {
        }

        public HitPoints(int max, int current, int temporary) {
            this.max = max;
            this.current = current;
            this.temporary = temporary;
        }

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

        public int getEffectiveCurrent() {
            return current + temporary;
        }
    }

    /**
     * Armor class: the resulting value together with the type of the effect that
     * produced it (for example {@code "natural"}, {@code "armor"} or {@code "spell"}).
     */
    public static class ArmorClass {
        private int value;
        private String type;

        public ArmorClass() {
        }

        public ArmorClass(int value, String type) {
            this.value = value;
            this.type = type;
        }

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

    /**
     * A named, possibly limited, resource such as hit dice, spell slots,
     * inspiration or resources points.
     */
    public static class Resource {
        private String name;
        private int current;
        private int max;
        private String description;

        public Resource() {
        }

        public Resource(String name, int current, int max, String description) {
            this.name = name;
            this.current = current;
            this.max = max;
            this.description = description;
        }

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

    /**
     * A single inventory entry: a countable item with an optional weight.
     */
    public static class InventoryItem {
        private String name;
        private int quantity;
        private String description;
        private Double weight;

        public InventoryItem() {
        }

        public InventoryItem(String name, int quantity, String description, Double weight) {
            this.name = name;
            this.quantity = quantity;
            this.description = description;
            this.weight = weight;
        }

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
