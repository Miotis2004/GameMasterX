/**
 * Shared contracts for the Authored Adventure aggregate.
 *
 * These types are consumed by both the client and server workspaces to keep the
 * adventure-authoring API contract in a single place. They mirror the server-side
 * {@code com.gamemasterx.server.adventure.model} hierarchy.
 *
 * <p>An <em>Authored Adventure</em> is the authored-content aggregate that holds
 * everything a game master needs to run a session: chapters and their scenes, the
 * locations where scenes take place, the NPCs and creatures that populate them, the
 * objectives that drive play, the branches that model player choice, the rewards for
 * success, the secret notes kept from the players, the world facts that anchor the
 * setting, and the tags used to classify the adventure.</p>
 *
 * <p>Every aggregate carries a stable {@code id}, a {@code schemaVersion}, a
 * {@code revision} counter and {@code createdAt}/{@code updatedAt} timestamps so that
 * documents can be versioned, migrated and optimistically concurrently controlled.</p>
 */

/**
 * Lifecycle status of an authored adventure.
 */
export type AdventureStatus = 'DRAFT' | 'PLAYTEST' | 'PUBLISHED' | 'ARCHIVED';

/**
 * The role a non-player character plays within an adventure.
 */
export type NpcRole = 'PROTAGONIST' | 'QUESTGIVER' | 'ANTAGONIST' | 'ALLY' | 'MILLIONAIR' | 'RECURRING';

/**
 * Difficulty tier used by creatures. Follows the traditional 4-tier difficulty
 * banding scaled to the number of players.
 */
export type CreatureDifficulty = 'EASY' | 'MODERATE' | 'HARD' | 'EXTREME';

/**
 * The kind of objective an adventure scene or the adventure overall is trying to
 * accomplish.
 */
export type ObjectiveType = 'MAIN' | 'SIDE' | 'SECRET' | 'TIMED' | 'COLLECT';

/**
 * How a branch connects to the surrounding scenes.
 */
export type BranchKind = 'CHOICE' | 'CONDITIONAL' | 'RANDOM' | 'LINEAR';

/**
 * The granularity of a world fact so callers can surface it appropriately.
 */
export type WorldFactCategory = 'LORE' | 'GEOGRAPHY' | 'FACTIONS' | 'MECHANICS' | 'NPC' | 'TIMELINE';

/**
 * A self-contained, authored adventure. This is the API-facing projection of the
 * adventure aggregate root.
 */
export interface AdventureResult {
  /** Stable identifier of the adventure. */
  id: string;
  /** Logical schema version for the persisted adventure document. */
  schemaVersion: number;
  /** Monotonic revision counter for optimistic concurrency control. */
  revision: number;
  createdAt: string;
  updatedAt: string;
  /** Human-readable title of the adventure. */
  title: string;
  /** Short promotional or summary description. */
  description: string;
  /** The game system this adventure is authored for. */
  gameSystem: string;
  /** Recommended number of players, if a recommendation exists. */
  recommendedPlayerCount: number | null;
  status: AdventureStatus;
  chapters: ChapterResult[];
  locations: LocationResult[];
  npcs: NpcResult[];
  creatures: CreatureResult[];
  objectives: ObjectiveResult[];
  branches: BranchResult[];
  rewards: RewardResult[];
  secretNotes: SecretNoteResult[];
  worldFacts: WorldFactResult[];
  tags: TagResult[];
}

/**
 * A chapter groups a linear sequence of scenes and provides the top-level
 * navigation structure of an adventure.
 */
export interface ChapterResult {
  id: string;
  title: string;
  description: string;
  /** Zero-based order of the chapter within the adventure. */
  order: number;
  scenes: SceneResult[];
}

/**
 * A scene is a single playable unit of content that takes place at a location and
 * typically involves NPCs, creatures, an objective and one or more branches.
 */
export interface SceneResult {
  id: string;
  title: string;
  description: string;
  /** Zero-based order of the scene within its chapter. */
  order: number;
  /** Optional reference to the location where the scene takes place. */
  locationId: string | null;
  npcIds: string[];
  creatureIds: string[];
  objectiveId: string | null;
  branchIds: string[];
}

/**
 * A location is a place within the adventure world where scenes occur.
 */
export interface LocationResult {
  id: string;
  name: string;
  description: string;
  /** Free-form geographic or dimensional region the location belongs to. */
  region: string | null;
  /** Whether the location is indoors or outdoors. */
  environment: 'INDOORS' | 'OUTDOORS' | 'UNKNOWN';
}

/**
 * A non-player character authored into the adventure.
 */
export interface NpcResult {
  id: string;
  name: string;
  description: string;
  role: NpcRole;
  /** Whether this NPC is known to the players (as opposed to a hidden actor). */
  playerAware: boolean;
  /** Free-form notes, motivations and secrets for the game master. */
  gmNotes: string;
}

/**
 * A creature encounter authored into the adventure.
 */
export interface CreatureResult {
  id: string;
  name: string;
  description: string;
  difficulty: CreatureDifficulty;
  /** Challenge/difficulty rating number (for example a CR or tier value). */
  rating: number;
  /** The combat or social role the creature plays. */
  role: string;
}

/**
 * An objective represents a goal the players are working towards.
 */
export interface ObjectiveResult {
  id: string;
  title: string;
  description: string;
  type: ObjectiveType;
  /** Whether the objective has already been completed. */
  completed: boolean;
  /** Optional deadline in rounds/turns for timed objectives. */
  deadlineRounds: number | null;
}

/**
 * A branch models a point of divergence in the adventure driven by player choice
 * or conditions.
 */
export interface BranchResult {
  id: string;
  title: string;
  description: string;
  kind: BranchKind;
  /** The scene that produces this branch (the "consequence" target). */
  consequenceSceneId: string | null;
  /** The scenes this branch leads away from. */
  fromSceneIds: string[];
}

/**
 * A reward granted to players on completion of an objective or scene.
 */
export interface RewardResult {
  id: string;
  title: string;
  experiencePoints: number;
  items: RewardItemResult[];
  currency: RewardCurrencyResult[];
}

/**
 * A single named item reward.
 */
export interface RewardItemResult {
  name: string;
  quantity: number;
  description: string;
}

/**
 * A single currency reward of a given denomination.
 */
export interface RewardCurrencyResult {
  denomination: string;
  amount: number;
}

/**
 * A secret note is GM-only information that should not be revealed to players
 * unless certain conditions are met.
 */
export interface SecretNoteResult {
  id: string;
  /** Stable identifier used to reference the secret from conditions. */
  identifier: string;
  text: string;
  /** Whether the secret has been revealed. */
  revealed: boolean;
  /** Optional condition identifier that gates the reveal. */
  revealCondition: string | null;
}

/**
 * A world fact is a piece of authored lore that anchors the setting.
 */
export interface WorldFactResult {
  id: string;
  statement: string;
  category: WorldFactCategory;
  /** Whether the fact is known to players from the start. */
  playerKnown: boolean;
}

/**
 * A tag classifies the adventure or its content by name and optional category.
 */
export interface TagResult {
  id: string;
  name: string;
  category: string | null;
}

/**
 * Request payload for authoring a new adventure.
 */
export interface AdventureCreation {
  title: string;
  description?: string;
  gameSystem: string;
  recommendedPlayerCount?: number;
  chapters?: ChapterResult[];
  locations?: LocationResult[];
  npcs?: NpcResult[];
  creatures?: CreatureResult[];
  objectives?: ObjectiveResult[];
  branches?: BranchResult[];
  rewards?: RewardResult[];
  secretNotes?: SecretNoteResult[];
  worldFacts?: WorldFactResult[];
  tags?: TagResult[];
}
