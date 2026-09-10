/**
 * Shared contract for the backend-owned gameplay checks: ability checks, skill
 * checks and saving throws.
 *
 * <p>These TypeScript types mirror the backend DTOs and records in the
 * {@code com.gamemasterx.server.gameplay} package. They describe the request
 * payloads accepted by (and the resolved-check payloads returned from) the
 * {@code /api/gameplay} endpoints. The backend API runs on port 5172.</p>
 */

/** The source of randomness selected for a check roll. */
export type RollMode = 'random' | 'seeded';

/** The three backend-owned check kinds. */
export type CheckType = 'ability-check' | 'skill-check' | 'saving-throw';

/** The resolved outcome of a check or attack. */
export type ActionOutcome = 'SUCCESS' | 'FAILURE' | 'PARTIAL' | 'UNKNOWN' | 'CRIT';

/** A single named, signed modifier applied to a check. */
export interface CheckModifier {
  name: string;
  value: number;
}

/**
 * A single resolved die record for a check. Mirrors the backend
 * {@code DiceResult}: {@code rolls} is the auditable record of the individual
 * die values that were drawn.
 */
export interface CheckDiceResult {
  id: string | null;
  label: string;
  diceExpression: string;
  dieSize: number;
  numberOfDice: number;
  rolls: number[];
  modifier: number;
  total: number;
  rolledAt: string | null;
}

/** Request payload accepted by the {@code /api/gameplay} check endpoints. */
export interface CheckRequest {
  /** Identifier of the actor performing the check. */
  actorId: string;
  /** Identifier of the campaign the check is performed in (optional). */
  campaignId?: string | null;
  /** Identifier of the encounter bounding the audited record (optional). */
  encounterId?: string | null;
  /** Identifier of the target participant, when applicable. */
  targetId?: string | null;
  /** Human-readable name of the target, when applicable. */
  targetName?: string | null;
  /** The ability the check is based on, for example {@code "Strength"}. */
  abilityName: string;
  /** The raw ability score; the modifier is derived server-side. */
  abilityScore: number;
  /** Whether the actor is proficient. Ignored by skill checks. */
  proficient?: boolean;
  /** The proficiency bonus to apply when proficient (0 = none). */
  proficiencyBonus?: number;
  /** The difficulty class the total must meet or exceed, or undefined. */
  dc?: number | null;
  /** {@code "random"} (default) or {@code "seeded"}. */
  rollMode?: RollMode;
  /** Seed for a {@code "seeded"} roll; required when {@code rollMode} is seeded. */
  seed?: string | null;
  /** Optional human-readable label for the resolved die result. */
  label?: string | null;
  /** Optional free-form note recorded on the audit action. */
  note?: string | null;
}

/**
 * The auditable outcome of a resolved check. Records the inputs (ability,
 * proficiency), the modifiers that were applied, the random values that were
 * drawn, the raw die value and the final outcome. Mirrors the backend
 * {@code ResolvedCheck}.
 */
export interface ResolvedCheck {
  /** The kind of check resolved. */
  checkType: CheckType;
  /** The ability the check was based on. */
  abilityName: string;
  /** Whether the actor was proficient. */
  proficient: boolean;
  /** The declared proficiency bonus. */
  proficiencyBonus: number;
  /** Whether the proficiency bonus was actually applied. */
  appliesProficiency: boolean;
  /** The ability modifier that was applied. */
  abilityModifier: number;
  /** The ordered, named modifiers that were applied. */
  modifiers: CheckModifier[];
  /** The resolved die record (auditable random values). */
  dieResult: CheckDiceResult;
  /** The raw {@code 1d20} value that was rolled. */
  roll: number;
  /** The sum of every applied modifier. */
  modifierTotal: number;
  /** The final outcome: {@code roll + modifierTotal}. */
  total: number;
  /** The difficulty class, or null. */
  dc: number | null;
  /** The resolved outcome. */
  outcome: ActionOutcome;
  /** Whether the total met or exceeded the DC. */
  success: boolean;
}

/**
 * Request payload accepted by the {@code /api/gameplay/attack} endpoint. Mirrors
 * the backend {@code AttackRequest}.
 */
export interface AttackRequest {
  /** Identifier of the actor (participant or NPC) performing the attack. */
  actorId: string;
  /** Identifier of the campaign the attack is performed in (optional). */
  campaignId?: string | null;
  /**
   * Identifier of the encounter that bounds the audited record and whose rules
   * profile governs critical hits. Required: an attack is only valid within an
   * active encounter on the actor's turn.
   */
  encounterId: string;
  /** Identifier of the target participant, when applicable. */
  targetId?: string | null;
  /** Human-readable name of the target, when applicable. */
  targetName?: string | null;
  /** The ability the attack is based on, for example {@code "Strength"}. */
  abilityName: string;
  /** The raw ability score; the modifier is derived server-side. */
  abilityScore: number;
  /** Whether the actor is proficient in the attack. */
  proficient?: boolean;
  /** The proficiency bonus to apply when proficient (0 = none). */
  proficiencyBonus?: number;
  /** The target's armor class the to-hit total must meet or exceed. */
  armorClass: number;
  /**
   * The attacker's reach, in grid squares, when the owning encounter tracks
   * grid positions for the attacker and target. Defaults to 1 (melee reach).
   * Only validated when both the attacker and the target have a stored grid
   * position; an out-of-range target is rejected.
   */
  reach?: number | null;
  /**
   * The wire representation of the rules profile governing critical hits (for
   * example {@code "SRD-5.2-2024"}). Defaults to the default profile when
   * omitted.
   */
  rulesProfile?: string | null;
  /** {@code "random"} (default) or {@code "seeded"}. */
  rollMode?: RollMode;
  /** Seed for a {@code "seeded"} roll; required when {@code rollMode} is seeded. */
  seed?: string | null;
  /** Optional human-readable label for the resolved die result. */
  label?: string | null;
  /** Optional free-form note recorded on the audit action. */
  note?: string | null;
}

/**
 * The auditable outcome of a resolved attack. Records the inputs (ability,
 * proficiency), the modifiers that were applied, the random values that were
 * drawn, the natural roll, the to-hit total, the target armor class, the hit and
 * critical verdicts, the resolved outcome, and the governing rules profile.
 * Mirrors the backend {@code ResolvedAttack}.
 */
export interface ResolvedAttack {
  /** Identifier of the actor who performed the attack. */
  actorId: string;
  /** The ability the attack was based on. */
  abilityName: string;
  /** Whether the actor was proficient. */
  proficient: boolean;
  /** The declared proficiency bonus. */
  proficiencyBonus: number;
  /** Whether the proficiency bonus was actually applied. */
  appliesProficiency: boolean;
  /** The ability modifier that was applied. */
  abilityModifier: number;
  /** The ordered, named modifiers that were applied. */
  modifiers: CheckModifier[];
  /** The resolved die record (auditable random values). */
  dieResult: CheckDiceResult;
  /** The natural {@code 1d20} value that was rolled. */
  roll: number;
  /** The sum of every applied modifier. */
  modifierTotal: number;
  /** The to-hit total: {@code roll + modifierTotal}. */
  toHitTotal: number;
  /** The target armor class the attack was resolved against. */
  armorClass: number;
  /** Whether the to-hit total met or exceeded the armor class. */
  hit: boolean;
  /** Whether the natural roll was a critical hit. */
  critical: boolean;
  /** The resolved outcome ({@code CRIT} on a crit, otherwise hit/miss). */
  outcome: ActionOutcome;
  /** The rules profile that governed critical-hit behaviour. */
  profile: string;
  /**
   * The damage multiplier prescribed by the governing rules profile for a
   * critical hit (for example {@code 2.0}). Backend-owned and deterministic,
   * never language-model output.
   */
  criticalDamageMultiplier: number;
  /** The immutable action record capturing the full audit trail. */
  action: {
    type: string;
    actorId: string;
    targetId: string | null;
    targetName: string | null;
    skillOrAbility: string | null;
    modifierTotal: number;
    total: number;
    outcome: ActionOutcome;
  };
}
