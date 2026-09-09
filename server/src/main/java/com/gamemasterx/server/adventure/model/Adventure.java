package com.gamemasterx.server.adventure.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Authored Adventure aggregate root persisted as a MongoDB document.
 *
 * <p>This is the persistence-only representation of the Authored Adventure
 * aggregate. It is deliberately distinct from {@link AdventureDto}: the document
 * entity is what Spring Data MongoDB reads from and writes to the
 * {@code adventures} collection, while {@link AdventureDto} is the API-facing
 * representation that is returned to (and accepted from) callers. Keeping the two
 * types separate prevents leaking persistence concerns (such as the
 * optimistic-concurrency revision counter) into the API contract.</p>
 *
 * <p>The Adventure is the authored-content aggregate that holds everything a game
 * master needs to run a session: chapters and their scenes, the locations where
 * scenes take place, the NPCs and creatures that populate them, the objectives
 * that drive play, the branches that model player choice, the rewards for
 * success, the secret notes kept from the players, the world facts that anchor
 * the setting, and the tags used to classify the adventure.</p>
 *
 * <p>The document carries a stable identifier, a schema version, a revision
 * counter and created/updated timestamps. The {@link #revision} field is also
 * annotated with {@link Version} so that Spring Data MongoDB applies optimistic
 * concurrency control to the aggregate.</p>
 *
 * <p>The adventure is composed of embedded value objects: {@link Chapter}s with
 * their {@link Scene}s, plus the flat collections of {@link Location}s,
 * {@link Npc}s, {@link Creature}s, {@link Objective}s, {@link Branch}s,
 * {@link Reward}s, {@link SecretNote}s, {@link WorldFact}s and {@link Tag}s. These
 * are nested value objects that belong to the aggregate and have no independent
 * identity.</p>
 */
@Document(collection = "adventures")
public class Adventure {

    /** Current schema version for the Adventure document shape. */
    public static final int SCHEMA_VERSION = 1;

    /**
     * Lifecycle status of an authored adventure.
     */
    public enum Status {
        DRAFT,
        PLAYTEST,
        PUBLISHED,
        ARCHIVED
    }

    /**
     * The role a non-player character plays within an adventure.
     */
    public enum NpcRole {
        PROTAGONIST,
        QUESTGIVER,
        ANTAGONIST,
        ALLY,
        MILLIONAIR,
        RECURRING
    }

    /**
     * Difficulty tier used by creatures, scaled to the number of players.
     */
    public enum CreatureDifficulty {
        EASY,
        MODERATE,
        HARD,
        EXTREME
    }

    /**
     * The kind of objective an adventure or scene is trying to accomplish.
     */
    public enum ObjectiveType {
        MAIN,
        SIDE,
        SECRET,
        TIMED,
        COLLECT
    }

    /**
     * How a branch connects to the surrounding scenes.
     */
    public enum BranchKind {
        CHOICE,
        CONDITIONAL,
        RANDOM,
        LINEAR
    }

    /**
     * The granularity of a world fact so callers can surface it appropriately.
     */
    public enum WorldFactCategory {
        LORE,
        GEOGRAPHY,
        FACTIONS,
        MECHANICS,
        NPC,
        TIMELINE
    }

    /**
     * Whether a location is indoors or outdoors.
     */
    public enum Environment {
        INDOORS,
        OUTDOORS,
        UNKNOWN
    }

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

    public Adventure() {
        this.schemaVersion = SCHEMA_VERSION;
        this.revision = 1;
        this.status = Status.DRAFT;
        this.chapters = new ArrayList<>();
        this.locations = new ArrayList<>();
        this.npcs = new ArrayList<>();
        this.creatures = new ArrayList<>();
        this.objectives = new ArrayList<>();
        this.branches = new ArrayList<>();
        this.rewards = new ArrayList<>();
        this.secretNotes = new ArrayList<>();
        this.worldFacts = new ArrayList<>();
        this.tags = new ArrayList<>();
    }

    /**
     * Full constructor used when materialising an adventure from persistence.
     */
    public Adventure(String id, int schemaVersion, int revision, Instant createdAt, Instant updatedAt,
                     String title, String description, String gameSystem, Integer recommendedPlayerCount,
                     Status status, List<Chapter> chapters, List<Location> locations, List<Npc> npcs,
                     List<Creature> creatures, List<Objective> objectives, List<Branch> branches,
                     List<Reward> rewards, List<SecretNote> secretNotes, List<WorldFact> worldFacts,
                     List<Tag> tags) {
        this.id = id;
        this.schemaVersion = schemaVersion;
        this.revision = revision;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.title = title;
        this.description = description;
        this.gameSystem = gameSystem;
        this.recommendedPlayerCount = recommendedPlayerCount;
        this.status = (status != null) ? status : Status.DRAFT;
        this.chapters = (chapters != null) ? chapters : new ArrayList<>();
        this.locations = (locations != null) ? locations : new ArrayList<>();
        this.npcs = (npcs != null) ? npcs : new ArrayList<>();
        this.creatures = (creatures != null) ? creatures : new ArrayList<>();
        this.objectives = (objectives != null) ? objectives : new ArrayList<>();
        this.branches = (branches != null) ? branches : new ArrayList<>();
        this.rewards = (rewards != null) ? rewards : new ArrayList<>();
        this.secretNotes = (secretNotes != null) ? secretNotes : new ArrayList<>();
        this.worldFacts = (worldFacts != null) ? worldFacts : new ArrayList<>();
        this.tags = (tags != null) ? tags : new ArrayList<>();
    }

    /**
     * Marks the aggregate as modified by refreshing the {@link #updatedAt}
     * timestamp and advancing the optimistic-concurrency {@link #revision}.
     */
    public void touch(Instant now) {
        this.updatedAt = now;
        this.revision++;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Adventure adventure = (Adventure) o;
        return Objects.equals(id, adventure.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * A chapter groups a linear sequence of scenes and provides the top-level
     * navigation structure of an adventure.
     */
    public static class Chapter {
        private String id;
        private String title;
        private String description;
        private int order;
        private List<Scene> scenes;

        public Chapter() {
            this.scenes = new ArrayList<>();
        }

        public Chapter(String id, String title, String description, int order, List<Scene> scenes) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.order = order;
            this.scenes = (scenes != null) ? scenes : new ArrayList<>();
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
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

        public int getOrder() {
            return order;
        }

        public void setOrder(int order) {
            this.order = order;
        }

        public List<Scene> getScenes() {
            return scenes;
        }

        public void setScenes(List<Scene> scenes) {
            this.scenes = (scenes != null) ? scenes : new ArrayList<>();
        }
    }

    /**
     * A scene is a single playable unit of content that takes place at a location
     * and typically involves NPCs, creatures, an objective and one or more
     * branches. Cross-references between a scene and the adventure's shared NPC,
     * creature, objective and branch collections are stored as stable IDs.
     */
    public static class Scene {
        private String id;
        private String title;
        private String description;
        private int order;
        private String locationId;
        private List<String> npcIds;
        private List<String> creatureIds;
        private String objectiveId;
        private List<String> branchIds;

        public Scene() {
            this.npcIds = new ArrayList<>();
            this.creatureIds = new ArrayList<>();
            this.branchIds = new ArrayList<>();
        }

        public Scene(String id, String title, String description, int order, String locationId,
                     List<String> npcIds, List<String> creatureIds, String objectiveId, List<String> branchIds) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.order = order;
            this.locationId = locationId;
            this.npcIds = (npcIds != null) ? npcIds : new ArrayList<>();
            this.creatureIds = (creatureIds != null) ? creatureIds : new ArrayList<>();
            this.objectiveId = objectiveId;
            this.branchIds = (branchIds != null) ? branchIds : new ArrayList<>();
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
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

        public int getOrder() {
            return order;
        }

        public void setOrder(int order) {
            this.order = order;
        }

        public String getLocationId() {
            return locationId;
        }

        public void setLocationId(String locationId) {
            this.locationId = locationId;
        }

        public List<String> getNpcs() {
            return npcIds;
        }

        public void setNpcs(List<String> npcs) {
            this.npcIds = (npcs != null) ? npcs : new ArrayList<>();
        }

        public List<String> getCreatures() {
            return creatureIds;
        }

        public void setCreatures(List<String> creatures) {
            this.creatureIds = (creatures != null) ? creatures : new ArrayList<>();
        }

        public String getObjectiveId() {
            return objectiveId;
        }

        public void setObjectiveId(String objectiveId) {
            this.objectiveId = objectiveId;
        }

        public List<String> getBranches() {
            return branchIds;
        }

        public void setBranches(List<String> branchIds) {
            this.branchIds = (branchIds != null) ? branchIds : new ArrayList<>();
        }
    }

    /**
     * A location is a place within the adventure world where scenes occur.
     */
    public static class Location {
        private String id;
        private String name;
        private String description;
        private String region;
        private Environment environment;

        public Location() {
        }

        public Location(String id, String name, String description, String region, Environment environment) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.region = region;
            this.environment = (environment != null) ? environment : Environment.UNKNOWN;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
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

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public Environment getEnvironment() {
            return environment;
        }

        public void setEnvironment(Environment environment) {
            this.environment = environment;
        }
    }

    /**
     * A non-player character authored into the adventure.
     */
    public static class Npc {
        private String id;
        private String name;
        private String description;
        private NpcRole role;
        private boolean playerAware;
        private String gmNotes;

        public Npc() {
        }

        public Npc(String id, String name, String description, NpcRole role, boolean playerAware, String gmNotes) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.role = (role != null) ? role : NpcRole.MILLIONAIR;
            this.playerAware = playerAware;
            this.gmNotes = gmNotes;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
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

        public NpcRole getRole() {
            return role;
        }

        public void setRole(NpcRole role) {
            this.role = role;
        }

        public boolean isPlayerAware() {
            return playerAware;
        }

        public void setPlayerAware(boolean playerAware) {
            this.playerAware = playerAware;
        }

        public String getGmNotes() {
            return gmNotes;
        }

        public void setGmNotes(String gmNotes) {
            this.gmNotes = gmNotes;
        }
    }

    /**
     * A creature encounter authored into the adventure.
     */
    public static class Creature {
        private String id;
        private String name;
        private String description;
        private CreatureDifficulty difficulty;
        private int rating;
        private String role;

        public Creature() {
        }

        public Creature(String id, String name, String description, CreatureDifficulty difficulty,
                        int rating, String role) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.difficulty = (difficulty != null) ? difficulty : CreatureDifficulty.EASY;
            this.rating = rating;
            this.role = role;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
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

        public CreatureDifficulty getDifficulty() {
            return difficulty;
        }

        public void setDifficulty(CreatureDifficulty difficulty) {
            this.difficulty = difficulty;
        }

        public int getRating() {
            return rating;
        }

        public void setRating(int rating) {
            this.rating = rating;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }

    /**
     * An objective represents a goal the players are working towards.
     */
    public static class Objective {
        private String id;
        private String title;
        private String description;
        private ObjectiveType type;
        private boolean completed;
        private Integer deadlineRounds;

        public Objective() {
        }

        public Objective(String id, String title, String description, ObjectiveType type, boolean completed,
                         Integer deadlineRounds) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.type = (type != null) ? type : ObjectiveType.MAIN;
            this.completed = completed;
            this.deadlineRounds = deadlineRounds;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
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

        public ObjectiveType getType() {
            return type;
        }

        public void setType(ObjectiveType type) {
            this.type = type;
        }

        public boolean isCompleted() {
            return completed;
        }

        public void setCompleted(boolean completed) {
            this.completed = completed;
        }

        public Integer getDeadlineRounds() {
            return deadlineRounds;
        }

        public void setDeadlineRounds(Integer deadlineRounds) {
            this.deadlineRounds = deadlineRounds;
        }
    }

    /**
     * A branch models a point of divergence in the adventure driven by player
     * choice or conditions.
     */
    public static class Branch {
        private String id;
        private String title;
        private String description;
        private BranchKind kind;
        private String consequenceSceneId;
        private List<String> fromSceneIds;

        public Branch() {
            this.fromSceneIds = new ArrayList<>();
        }

        public Branch(String id, String title, String description, BranchKind kind,
                      String consequenceSceneId, List<String> fromSceneIds) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.kind = (kind != null) ? kind : BranchKind.CHOICE;
            this.consequenceSceneId = consequenceSceneId;
            this.fromSceneIds = (fromSceneIds != null) ? fromSceneIds : new ArrayList<>();
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
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

        public BranchKind getKind() {
            return kind;
        }

        public void setKind(BranchKind kind) {
            this.kind = kind;
        }

        public String getConsequenceSceneId() {
            return consequenceSceneId;
        }

        public void setConsequenceSceneId(String consequenceSceneId) {
            this.consequenceSceneId = consequenceSceneId;
        }

        public List<String> getFromSceneIds() {
            return fromSceneIds;
        }

        public void setFromSceneIds(List<String> fromSceneIds) {
            this.fromSceneIds = (fromSceneIds != null) ? fromSceneIds : new ArrayList<>();
        }
    }

    /**
     * A reward granted to players on completion of an objective or scene.
     */
    public static class Reward {
        private String id;
        private String title;
        private int experiencePoints;
        private List<RewardItem> items;
        private List<RewardCurrency> currency;

        public Reward() {
            this.items = new ArrayList<>();
            this.currency = new ArrayList<>();
        }

        public Reward(String id, String title, int experiencePoints, List<RewardItem> items,
                      List<RewardCurrency> currency) {
            this.id = id;
            this.title = title;
            this.experiencePoints = experiencePoints;
            this.items = (items != null) ? items : new ArrayList<>();
            this.currency = (currency != null) ? currency : new ArrayList<>();
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public int getExperiencePoints() {
            return experiencePoints;
        }

        public void setExperiencePoints(int experiencePoints) {
            this.experiencePoints = experiencePoints;
        }

        public List<RewardItem> getItems() {
            return items;
        }

        public void setItems(List<RewardItem> items) {
            this.items = (items != null) ? items : new ArrayList<>();
        }

        public List<RewardCurrency> getCurrency() {
            return currency;
        }

        public void setCurrency(List<RewardCurrency> currency) {
            this.currency = (currency != null) ? currency : new ArrayList<>();
        }
    }

    /**
     * A single named item reward.
     */
    public static class RewardItem {
        private String name;
        private int quantity;
        private String description;

        public RewardItem() {
        }

        public RewardItem(String name, int quantity, String description) {
            this.name = name;
            this.quantity = quantity;
            this.description = description;
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
    }

    /**
     * A single currency reward of a given denomination.
     */
    public static class RewardCurrency {
        private String denomination;
        private int amount;

        public RewardCurrency() {
        }

        public RewardCurrency(String denomination, int amount) {
            this.denomination = denomination;
            this.amount = amount;
        }

        public String getDenomination() {
            return denomination;
        }

        public void setDenomination(String denomination) {
            this.denomination = denomination;
        }

        public int getAmount() {
            return amount;
        }

        public void setAmount(int amount) {
            this.amount = amount;
        }
    }

    /**
     * A secret note is GM-only information that should not be revealed to players
     * unless certain conditions are met.
     */
    public static class SecretNote {
        private String id;
        private String identifier;
        private String text;
        private boolean revealed;
        private String revealCondition;

        public SecretNote() {
        }

        public SecretNote(String id, String identifier, String text, boolean revealed, String revealCondition) {
            this.id = id;
            this.identifier = identifier;
            this.text = text;
            this.revealed = revealed;
            this.revealCondition = revealCondition;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getIdentifier() {
            return identifier;
        }

        public void setIdentifier(String identifier) {
            this.identifier = identifier;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public boolean isRevealed() {
            return revealed;
        }

        public void setRevealed(boolean revealed) {
            this.revealed = revealed;
        }

        public String getRevealCondition() {
            return revealCondition;
        }

        public void setRevealCondition(String revealCondition) {
            this.revealCondition = revealCondition;
        }
    }

    /**
     * A world fact is a piece of authored lore that anchors the setting.
     */
    public static class WorldFact {
        private String id;
        private String statement;
        private WorldFactCategory category;
        private boolean playerKnown;

        public WorldFact() {
        }

        public WorldFact(String id, String statement, WorldFactCategory category, boolean playerKnown) {
            this.id = id;
            this.statement = statement;
            this.category = (category != null) ? category : WorldFactCategory.LORE;
            this.playerKnown = playerKnown;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getStatement() {
            return statement;
        }

        public void setStatement(String statement) {
            this.statement = statement;
        }

        public WorldFactCategory getCategory() {
            return category;
        }

        public void setCategory(WorldFactCategory category) {
            this.category = category;
        }

        public boolean isPlayerKnown() {
            return playerKnown;
        }

        public void setPlayerKnown(boolean playerKnown) {
            this.playerKnown = playerKnown;
        }
    }

    /**
     * A tag classifies the adventure or its content by name and optional category.
     */
    public static class Tag {
        private String id;
        private String name;
        private String category;

        public Tag() {
        }

        public Tag(String id, String name, String category) {
            this.id = id;
            this.name = name;
            this.category = category;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }
    }
}
