/**
 * Shared contract for the auditable, seeded-deterministic dice API.
 *
 * <p>These TypeScript types mirror the backend DTOs in the
 * {@code com.gamemasterx.server.dice} package. They describe the request and
 * response payloads exchanged with the {@code POST /api/dice/roll} endpoint. The
 * backend API runs on port 5172.</p>
 */

/** The source of randomness selected for a roll. */
export type RollMode = 'random' | 'seeded';

/** Advantage/disadvantage applied to a batch of dice. */
export type DiceAdvantage = 'none' | 'advantage' | 'disadvantage';

/**
 * A single resolved die group.
 *
 * <p>{@code rolls} is the authoritative, auditable record of the individual die
 * values that were drawn. It contains exactly one kept value per die; for
 * advantage/disadvantage batches the kept value (higher or lower of two draws)
 * is recorded. This is what makes a roll reproducible and verifiable.</p>
 */
export interface DiceResult {
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

/** Request payload accepted by POST /api/dice/roll. */
export interface DiceRollRequest {
  /** The expression to resolve, e.g. "2d6+3" or "1d20dis". */
  expression: string;
  /** "random" (default) or "seeded". */
  mode?: RollMode;
  /** Required when mode is "seeded"; derives the deterministic generator. */
  seed?: string | null;
  /** Optional human-readable label applied to each result. */
  label?: string | null;
}

/** Response payload returned by POST /api/dice/roll. */
export interface DiceRollResponse {
  /** The expression that was resolved (as supplied). */
  expression: string;
  /** The applied RollMode, as a wire string. */
  mode: RollMode;
  /** The seed that produced this roll, or null for random mode. */
  seed: string | null;
  /** The auditable resolved results, one per expression group. */
  results: DiceResult[];
  /** The aggregate total: the sum of every result's total. */
  total: number;
}
