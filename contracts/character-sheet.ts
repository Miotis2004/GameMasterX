/**
 * Shared contract for the Character sheet.
 *
 * <p>These TypeScript types mirror the backend backend DTOs in
 * {@code com.gamemasterx.server.character.model}. They describe the API-facing
 * representation returned by (and accepted from) the {@code /api/characters}
 * endpoints. The persistence-only fields (the optimistic-concurrency
 * {@code revision} counter and the raw {@code schemaVersion}) are tracked here
 * for information but {@code revision} is intentionally not exposed by the
 * backend DTO.</p>
 */

export interface AbilityScores {
  strength: number;
  dexterity: number;
  constitution: number;
  intelligence: number;
  wisdom: number;
  charisma: number;
}

export interface AbilityModifiers {
  strength: number;
  dexterity: number;
  constitution: number;
  intelligence: number;
  wisdom: number;
  charisma: number;
}

export interface Proficiency {
  level: number;
  proficiencyBonus: number;
  proficiencies: string[];
}

export interface HitPoints {
  max: number;
  current: number;
  temporary: number;
}

export interface ArmorClass {
  value: number;
  type: string;
}

export interface CharacterResource {
  name: string;
  current: number;
  max: number;
  description: string;
}

export interface InventoryItem {
  name: string;
  quantity: number;
  description: string;
  weight: number | null;
}

/** API-facing representation of a character sheet (mirrors CharacterDto). */
export interface CharacterSheet {
  id: string;
  schemaVersion: number;
  createdAt: string;
  updatedAt: string;
  name: string;
  ownerId: string;
  campaignId: string | null;
  gameSystem: string | null;
  level: number;
  abilityScores: AbilityScores;
  abilityModifiers: AbilityModifiers;
  proficiency: Proficiency;
  hitPoints: HitPoints;
  armorClass: ArmorClass;
  resources: CharacterResource[];
  inventory: InventoryItem[];
}

/** Creation payload accepted by POST /api/characters. */
export interface CharacterSheetCreateRequest {
  name: string;
  campaignId: string;
  gameSystem?: string;
  level: number;
  abilityScores: AbilityScores;
  proficiency?: Proficiency;
  hitPoints?: HitPoints;
  armorClass?: ArmorClass;
  resources?: CharacterResource[];
  inventory?: InventoryItem[];
}

/** Partial update payload accepted by PUT /api/characters/:id. */
export interface CharacterSheetUpdateRequest {
  name?: string;
  campaignId?: string;
  gameSystem?: string;
  level?: number;
  abilityScores?: AbilityScores;
  proficiency?: Proficiency;
  hitPoints?: HitPoints;
  armorClass?: ArmorClass;
  resources?: CharacterResource[];
  inventory?: InventoryItem[];
}
