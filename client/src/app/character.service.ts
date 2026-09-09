import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormBuilder, AbstractControl, ValidationErrors, ValidatorFn, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable, of } from 'rxjs';
import { map } from 'rxjs/operators';

/**
 * Shared contract for the Character sheet.
 *
 * <p>These TypeScript types mirror the backend DTOs in
 * {@code com.gamemasterx.server.character.model}. They describe the API-facing
 * representation returned by (and accepted from) the {@code /api/characters}
 * endpoints. The persistence-only {@code schemaVersion} metadata is tracked here
 * for information only.</p>
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

/**
 * The limited character projection returned by the campaign dashboard. Only the
 * fields needed to render a character in a campaign-wide list are present.
 */
export interface CampaignCharacterSummary {
  id: string;
  name: string;
  ownerId: string | null;
  campaignId: string | null;
}

// --- Validation constants mirroring the backend CharacterSheet DTOs. ---

/** Maximum length of a character name (server {@code @Size(max = 100)}). */
export const CHARACTER_NAME_MAX = 100;
/** Maximum length of a campaign identifier on the create request. */
export const CAMPAIGN_ID_MAX = 100;
/** Maximum length of a game system string. */
export const GAME_SYSTEM_MAX = 100;
/** Lowest legal ability score (SRD 2024). */
export const MIN_ABILITY_SCORE = 1;
/** Highest legal ability score (SRD 2024). */
export const MAX_ABILITY_SCORE = 30;
/** Lowest legal character level. */
export const MIN_LEVEL = 1;
/** Highest legal character level (SRD 2024). */
export const MAX_LEVEL = 20;
/** Lowest legal armor class value (SRD 2024). */
export const MIN_ARMOR_CLASS = 1;
/** Highest legal armor class value (SRD 2024). */
export const MAX_ARMOR_CLASS = 30;

/** The six ability scores in canonical order. */
export const ABILITY_NAMES = [
  'strength',
  'dexterity',
  'constitution',
  'intelligence',
  'wisdom',
  'charisma',
] as const;

export type AbilityName = (typeof ABILITY_NAMES)[number];

/**
 * Computes the deterministic SRD 2024 ability modifier for a score:
 * {@code floor((score - 10) / 2)}. Mirrors the derivation enforced by the
 * backend {@link CharacterValidator}.
 */
export function abilityModifier(score: number): number {
  return Math.floor((score - 10) / 2);
}

/**
 * Returns the SRD 2024 proficiency bonus for a level using the standard tiered
 * table. Mirrors {@link CharacterValidator#expectedProficiencyBonus}.
 */
export function expectedProficiencyBonus(level: number): number {
  if (level >= 1 && level <= 4) return 2;
  if (level >= 5 && level <= 8) return 3;
  if (level >= 9 && level <= 12) return 4;
  if (level >= 13 && level <= 16) return 5;
  return 6; // 17-20
}

/**
 * A validator that ensures every ability score is an integer in
 * {@code [1, 30]} and that any supplied modifier equals the deterministic
 * derivation {@code floor((score - 10) / 2)} of its score. Used by the create
 * form (scores only) and the edit form (scores plus derived modifiers).
 *
 * <p>The create form sends only {@code abilityScores}, so {@code includeModifiers}
 * controls whether the modifier derivation is checked.</p>
 */
const abilityScoresValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const group = control as FormGroup;
  const scores = group.get('abilityScores')?.value as Partial<AbilityScores> | null;
  if (!scores) {
    return null;
  }
  const errors: Record<string, unknown> = {};
  for (const name of ABILITY_NAMES) {
    const score = scores[name];
    if (typeof score !== 'number' || Number.isNaN(score)) {
      errors[`ability_${name}_required`] = true;
      continue;
    }
    if (!Number.isInteger(score) || score < MIN_ABILITY_SCORE || score > MAX_ABILITY_SCORE) {
      errors[`ability_${name}_range`] = { min: MIN_ABILITY_SCORE, max: MAX_ABILITY_SCORE };
    }
  }
  return Object.keys(errors).length > 0 ? errors : null;
};

/**
 * Re-exports ReactiveFormsModule for use in component imports.
 */
export const CharacterReactiveFormsModule = ReactiveFormsModule;

/**
 * Client service for the character listing, creation, detail, and update
 * endpoints.
 *
 * <p>Owned characters are read through {@link listOwnedCharacters}
 * ({@code GET /api/characters}); campaign-accessible characters are read through
 * {@link listCampaignCharacters} by consulting each campaign's dashboard
 * ({@code GET /api/campaigns/:id/dashboard}). The list component merges the two
 * sources so that a caller sees every character it owns plus every character it
 * can access through a campaign it belongs to.</p>
 */
@Injectable({ providedIn: 'root' })
export class CharacterService {
  private readonly http = inject(HttpClient);
  private readonly formBuilder = inject(FormBuilder);
  private readonly apiBase = 'http://localhost:5172/api';

  /** Lists every character owned by the authenticated caller. */
  listOwnedCharacters(): Observable<CharacterSheet[]> {
    return this.http.get<CharacterSheet[]>(`${this.apiBase}/characters`);
  }

  /** Reads a single character sheet by id. */
  getCharacter(id: string): Observable<CharacterSheet> {
    return this.http.get<CharacterSheet>(`${this.apiBase}/characters/${id}`);
  }

  /** Creates a new character sheet. The caller is recorded as the owner. */
  createCharacter(creation: CharacterSheetCreateRequest): Observable<CharacterSheet> {
    return this.http.post<CharacterSheet>(`${this.apiBase}/characters`, creation);
  }

  /** Updates an existing character sheet with a partial payload. */
  updateCharacter(id: string, update: CharacterSheetUpdateRequest): Observable<CharacterSheet> {
    return this.http.put<CharacterSheet>(`${this.apiBase}/characters/${id}`, update);
  }

  /**
   * Reads the characters associated with a single campaign that the caller can
   * access, via the campaign dashboard.
   */
  listCampaignCharacters(campaignId: string): Observable<CampaignCharacterSummary[]> {
    return this.http
      .get<{ characters: CampaignCharacterSummary[] }>(`${this.apiBase}/campaigns/${campaignId}/dashboard`)
      .pipe(map((response) => response.characters ?? []));
  }

  /**
   * The reactive form model for creating a character. Field validators mirror
   * the server-side Bean Validation and {@link CharacterValidator} constraints.
   *
   * @param campaignId the campaign the character will be associated with;
   *   required on create because the backend rejects a blank campaign id.
   */
  createForm(campaignId: string): { form: FormGroup; abilityScores: FormGroup } {
    const abilityScores = this.formBuilder.group({
      strength: [5, [Validators.required, Validators.min(MIN_ABILITY_SCORE), Validators.max(MAX_ABILITY_SCORE)]],
      dexterity: [5, [Validators.required, Validators.min(MIN_ABILITY_SCORE), Validators.max(MAX_ABILITY_SCORE)]],
      constitution: [5, [Validators.required, Validators.min(MIN_ABILITY_SCORE), Validators.max(MAX_ABILITY_SCORE)]],
      intelligence: [5, [Validators.required, Validators.min(MIN_ABILITY_SCORE), Validators.max(MAX_ABILITY_SCORE)]],
      wisdom: [5, [Validators.required, Validators.min(MIN_ABILITY_SCORE), Validators.max(MAX_ABILITY_SCORE)]],
      charisma: [5, [Validators.required, Validators.min(MIN_ABILITY_SCORE), Validators.max(MAX_ABILITY_SCORE)]],
    });

    const form = this.formBuilder.group(
      {
        name: ['', [Validators.required, Validators.maxLength(CHARACTER_NAME_MAX)]],
        campaignId: [campaignId, [Validators.required, Validators.maxLength(CAMPAIGN_ID_MAX)]],
        gameSystem: ['', Validators.maxLength(GAME_SYSTEM_MAX)],
        level: [1, [Validators.required, Validators.min(MIN_LEVEL), Validators.max(MAX_LEVEL)]],
        abilityScores,
      },
      { validators: abilityScoresValidator }
    );

    return { form, abilityScores };
  }

  /**
   * The reactive form model for editing an existing character. The name is
   * required; every other field is optional on the server but range-validates
   * the supplied values where a constraint exists, and the ability score range
   * and modifier derivation are enforced.
   */
  editForm(character: CharacterSheet): { form: FormGroup; abilityScores: FormGroup } {
    const abilityScores = this.formBuilder.group({
      strength: [character.abilityScores.strength, [Validators.min(MIN_ABILITY_SCORE), Validators.max(MAX_ABILITY_SCORE)]],
      dexterity: [character.abilityScores.dexterity, [Validators.min(MIN_ABILITY_SCORE), Validators.max(MAX_ABILITY_SCORE)]],
      constitution: [character.abilityScores.constitution, [Validators.min(MIN_ABILITY_SCORE), Validators.max(MAX_ABILITY_SCORE)]],
      intelligence: [character.abilityScores.intelligence, [Validators.min(MIN_ABILITY_SCORE), Validators.max(MAX_ABILITY_SCORE)]],
      wisdom: [character.abilityScores.wisdom, [Validators.min(MIN_ABILITY_SCORE), Validators.max(MAX_ABILITY_SCORE)]],
      charisma: [character.abilityScores.charisma, [Validators.min(MIN_ABILITY_SCORE), Validators.max(MAX_ABILITY_SCORE)]],
    });

    const form = this.formBuilder.group(
      {
        name: [character.name, [Validators.required, Validators.maxLength(CHARACTER_NAME_MAX)]],
        campaignId: [character.campaignId ?? '', Validators.maxLength(CAMPAIGN_ID_MAX)],
        gameSystem: [character.gameSystem ?? '', Validators.maxLength(GAME_SYSTEM_MAX)],
        level: [character.level, [Validators.min(MIN_LEVEL), Validators.max(MAX_LEVEL)]],
        abilityScores,
      },
      { validators: abilityScoresValidator }
    );

    return { form, abilityScores };
  }
}
