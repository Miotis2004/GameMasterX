import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Encounter,
  EncounterStatus,
  Participant,
  HitPoints,
  Condition,
  Resource,
} from '@contracts/encounter';
import {
  CheckRequest,
  AttackRequest,
  ResolvedCheck,
  ResolvedAttack,
  CheckType,
  RollMode,
} from '@contracts/gameplay';
import { DiceRollRequest, DiceRollResponse } from '@contracts/dice';

/**
 * The auditable {@link Action} recorded on a turn, as returned by
 * {@code GET /api/encounters/{id}/turns}. Mirrors the backend
 * {@code com.gamemasterx.server.gameplay.model.Action}.
 */
export interface GameAction {
  id: string | null;
  type: string;
  actorId: string | null;
  targetId: string | null;
  targetName: string | null;
  skillOrAbility: string | null;
  modifiers: { name: string; value: number }[];
  diceResults: TurnDiceResult[];
  modifierTotal: number;
  total: number;
  outcome: string;
  note: string | null;
  performedAt: string | null;
}

/**
 * The auditable die record embedded in a {@link GameAction} and a
 * {@link Turn}. Mirrors the backend {@code com.gamemasterx.server.gameplay.model.DiceResult}.
 */
export interface TurnDiceResult {
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

/**
 * The immutable turn document returned by
 * {@code GET /api/encounters/{id}/turns}. Mirrors the backend
 * {@code com.gamemasterx.server.gameplay.model.Turn}.
 */
export interface Turn {
  id: string | null;
  campaignId: string | null;
  encounterId: string | null;
  round: number;
  turnIndex: number;
  actingParticipantId: string | null;
  actions: GameAction[];
  diceResults: TurnDiceResult[];
  revisionBefore: number;
  revisionAfter: number;
  startedAt: string | null;
  endedAt: string | null;
}

/**
 * The immutable audit entry returned by
 * {@code GET /api/encounters/{id}/audit}. Mirrors the backend
 * {@code com.gamemasterx.server.gameplay.model.Audit}.
 */
export interface AuditEntry {
  id: string | null;
  auditSequence: number;
  campaignId: string | null;
  encounterId: string | null;
  turnId: string | null;
  actionId: string | null;
  mutationId: string | null;
  subjectType: string | null;
  subjectId: string | null;
  before: string | null;
  after: string | null;
  decision: string;
  actor: string | null;
  reason: string | null;
  revisionBefore: number;
  revisionAfter: number;
  recordedAt: string | null;
  correlationId: string | null;
}

/** The caller's campaign role, matching the backend membership hierarchy. */
export type PlayRole = 'OWNER' | 'GAME_MASTER' | 'PLAYER' | 'OBSERVER';

/**
 * Role hierarchy levels (highest authority first). A role is authorised for an
 * action when its level is at or below the action's required level, matching the
 * server-side {@code MembershipRole.isAtLeast} semantics.
 */
const ROLE_LEVEL = new Map<PlayRole, number>([
  ['OWNER', 0],
  ['GAME_MASTER', 1],
  ['PLAYER', 2],
  ['OBSERVER', 3],
]);

/**
 * Active encounter service.
 *
 * <p>This is the client-facing surface for the live encounter view. It wraps the
 * backend turn APIs: the encounter lifecycle endpoints ({@link startEncounter},
 * {@link pauseEncounter}, {@link resumeEncounter}, {@link completeEncounter},
 * {@link advanceTurn} and initiative generation), the participant mutation
 * endpoints ({@link applyDamage}, {@link applyHealing}, {@link addCondition} and
 * friends), the immutable turn/audit history, and the backend-owned gameplay and
 * dice endpoints. All calls run on the backend API port (5172).</p>
 *
 * <p><strong>Idempotency.</strong> Every mutating action accepts an
 * {@code idempotencyKey}. The client generates a key per submission so a retried
 * request is answered from the backend-owned idempotency store instead of
 * double-rolling its dice or double-recording its turn. The key is a client-side
 * convenience; the backend remains the authoritative enforcement boundary and
 * will still reject an out-of-turn or unavailable action with
 * {@code 400 Bad Request}.</p>
 *
 * <p><strong>Visibility is not enforcement.</strong> The controls the play view
 * shows or hides based on the caller's campaign role are a presentation
 * convenience only. Hiding a control never provides security: every mutating
 * call below is authorised server-side by {@code MembershipService} and returns
 * {@code 403 Forbidden} when the caller lacks the required role. The backend is
 * the single enforcement boundary.</p>
 */
@Injectable({ providedIn: 'root' })
export class EncounterPlayService {
  private readonly http = inject(HttpClient);

  private readonly apiBase = 'http://localhost:5172/api';

  /**
   * The role hierarchy levels exposed for UI visibility checks. Kept public so a
   * view can resolve whether a caller may perform an action; it is never used as
   * an access control by itself.
   */
  static readonly roleLevel = ROLE_LEVEL;

  /**
   * Query-form authorization check mirroring the server-side role hierarchy:
   * {@code true} when {@code role} is at or above the {@code requiredRole}. This
   * only drives what the UI shows; the backend enforces the same rule.
   */
  static isAtLeast(role: PlayRole | null, requiredRole: PlayRole): boolean {
    if (role == null) {
      return false;
    }
    const level = ROLE_LEVEL.get(role);
    const required = ROLE_LEVEL.get(requiredRole);
    return level !== undefined && required !== undefined && level <= required;
  }

  // ----- Encounters -----

  /** Reads an encounter by id. */
  fetchEncounter(id: string): Observable<Encounter> {
    return this.http.get<Encounter>(`${this.apiBase}/encounters/${id}`);
  }

  /** Lists a campaign's encounters. */
  listCampaignEncounters(campaignId: string): Observable<Encounter[]> {
    const params = new HttpParams().set('campaignId', campaignId);
    return this.http.get<Encounter[]>(`${this.apiBase}/encounters`, { params });
  }

  /**
   * Starts a draft encounter (DRAFT &rarr; ACTIVE), rolling initiative for any
   * participant without a pre-set score.
   */
  startEncounter(
    id: string,
    rollMode: RollMode | undefined = 'random',
    seed: string | undefined,
  ): Observable<Encounter> {
    return this.http.post<Encounter>(`${this.apiBase}/encounters/${id}/start`, null, {
      params: this.rollParams(rollMode, seed),
    });
  }

  /**
   * Re-rolls combat initiative for every participant in a DRAFT encounter.
   */
  generateInitiative(
    id: string,
    rollMode: RollMode | undefined = 'random',
    seed: string | undefined,
  ): Observable<Encounter> {
    return this.http.post<Encounter>(`${this.apiBase}/encounters/${id}/initiative`, null, {
      params: this.rollParams(rollMode, seed),
    });
  }

  /** Pauses an active encounter (ACTIVE &rarr; PAUSED). */
  pauseEncounter(id: string): Observable<Encounter> {
    return this.http.post<Encounter>(`${this.apiBase}/encounters/${id}/pause`, null);
  }

  /** Resumes a paused encounter (PAUSED &rarr; ACTIVE). */
  resumeEncounter(id: string): Observable<Encounter> {
    return this.http.post<Encounter>(`${this.apiBase}/encounters/${id}/resume`, null);
  }

  /** Completes an encounter (ACTIVE/PAUSED &rarr; COMPLETED). */
  completeEncounter(id: string): Observable<Encounter> {
    return this.http.post<Encounter>(`${this.apiBase}/encounters/${id}/complete`, null);
  }

  /** Advances the encounter to the next turn. */
  advanceTurn(id: string): Observable<Encounter> {
    return this.http.post<Encounter>(`${this.apiBase}/encounters/${id}/turns/advance`, null);
  }

  // ----- Participant mutation (all require the encounter to be ACTIVE) -----

  /** Applies damage to a participant, absorbing temporary hit points first. */
  applyDamage(
    id: string,
    participantId: string,
    amount: number,
    note: string | undefined,
    expectedRevision: number | undefined,
    idempotencyKey: string | undefined,
  ): Observable<Encounter> {
    return this.http.post<Encounter>(
      `${this.apiBase}/encounters/${id}/participants/${participantId}/damage`,
      { amount, note },
      { params: this.actionParams(expectedRevision, idempotencyKey) },
    );
  }

  /** Heals a participant, restoring current hit points up to the maximum. */
  applyHealing(
    id: string,
    participantId: string,
    amount: number,
    note: string | undefined,
    expectedRevision: number | undefined,
    idempotencyKey: string | undefined,
  ): Observable<Encounter> {
    return this.http.post<Encounter>(
      `${this.apiBase}/encounters/${id}/participants/${participantId}/healing`,
      { amount, note },
      { params: this.actionParams(expectedRevision, idempotencyKey) },
    );
  }

  /** Grants temporary hit points to a participant. */
  applyTemporaryHitPoints(
    id: string,
    participantId: string,
    amount: number,
    note: string | undefined,
    expectedRevision: number | undefined,
    idempotencyKey: string | undefined,
  ): Observable<Encounter> {
    return this.http.post<Encounter>(
      `${this.apiBase}/encounters/${id}/participants/${participantId}/temporary-hit-points`,
      { amount, note },
      { params: this.actionParams(expectedRevision, idempotencyKey) },
    );
  }

  /** Adds (or updates) a condition on a participant. */
  addCondition(
    id: string,
    participantId: string,
    name: string,
    description: string | null,
    roundsRemaining: number | null,
  ): Observable<Encounter> {
    return this.http.post<Encounter>(
      `${this.apiBase}/encounters/${id}/participants/${participantId}/conditions`,
      { name, description, roundsRemaining },
    );
  }

  /** Removes a condition from a participant. */
  removeCondition(
    id: string,
    participantId: string,
    conditionName: string,
  ): Observable<Encounter> {
    return this.http.delete<Encounter>(
      `${this.apiBase}/encounters/${id}/participants/${participantId}/conditions/${conditionName}`,
    );
  }

  /** Moves a participant to a grid square, validated server-side. */
  moveParticipant(
    id: string,
    participantId: string,
    x: number,
    y: number,
    note: string | undefined,
  ): Observable<Encounter> {
    return this.http.post<Encounter>(
      `${this.apiBase}/encounters/${id}/participants/${participantId}/move`,
      { x, y, note },
    );
  }

  /** Resolves a death saving throw for a participant at 0 hit points. */
  resolveDeathSave(
    id: string,
    participantId: string,
    savingThrowModifier: number,
    failures: number,
    successes: number,
    rollMode: RollMode | undefined,
    seed: string | undefined,
    note: string | undefined,
  ): Observable<Encounter> {
    return this.http.post<Encounter>(
      `${this.apiBase}/encounters/${id}/participants/${participantId}/death-save`,
      { savingThrowModifier, failures, successes, rollMode, seed, note },
    );
  }

  // ----- Turn history and audit -----

  /** Returns the immutable turn history for an encounter, in round/turn order. */
  fetchTurns(encounterId: string): Observable<Turn[]> {
    return this.http.get<Turn[]>(`${this.apiBase}/encounters/${encounterId}/turns`);
  }

  /** Returns the immutable audit log for an encounter, in append order. */
  fetchAudit(encounterId: string): Observable<AuditEntry[]> {
    return this.http.get<AuditEntry[]>(`${this.apiBase}/encounters/${encounterId}/audit`);
  }

  // ----- Gameplay (backend-owned checks and attacks) -----

  /** Resolves and records an ability check. */
  abilityCheck(
    request: CheckRequest,
    expectedRevision: number | undefined,
    idempotencyKey: string | undefined,
  ): Observable<ResolvedCheck> {
    return this.http.post<ResolvedCheck>(
      `${this.apiBase}/gameplay/ability-check`,
      request,
      { params: this.actionParams(expectedRevision, idempotencyKey) },
    );
  }

  /** Resolves and records a skill check. */
  skillCheck(
    request: CheckRequest,
    expectedRevision: number | undefined,
    idempotencyKey: string | undefined,
  ): Observable<ResolvedCheck> {
    return this.http.post<ResolvedCheck>(
      `${this.apiBase}/gameplay/skill-check`,
      request,
      { params: this.actionParams(expectedRevision, idempotencyKey) },
    );
  }

  /** Resolves and records a saving throw. */
  savingThrow(
    request: CheckRequest,
    expectedRevision: number | undefined,
    idempotencyKey: string | undefined,
  ): Observable<ResolvedCheck> {
    return this.http.post<ResolvedCheck>(
      `${this.apiBase}/gameplay/saving-throw`,
      request,
      { params: this.actionParams(expectedRevision, idempotencyKey) },
    );
  }

  /** Resolves and records an attack roll. */
  attack(
    request: AttackRequest,
    expectedRevision: number | undefined,
    idempotencyKey: string | undefined,
  ): Observable<ResolvedAttack> {
    return this.http.post<ResolvedAttack>(
      `${this.apiBase}/gameplay/attack`,
      request,
      { params: this.actionParams(expectedRevision, idempotencyKey) },
    );
  }

  // ----- Dice -----

  /** Rolls a dice expression through the auditable dice subsystem. */
  rollDice(request: DiceRollRequest): Observable<DiceRollResponse> {
    return this.http.post<DiceRollResponse>(`${this.apiBase}/dice/roll`, request);
  }

  // ----- Helpers -----

  /**
   * Generates a client-side idempotency key for an action submission. The key
   * identifies a single logical action so that a retried submission is answered
   * from the backend-owned idempotency store rather than being rolled or
   * recorded twice.
   *
   * @param operation a short operation label, e.g. {@code "damage"} or {@code "attack"}
   * @param subjectId the participant (or target) the action concerns
   * @returns a stable, unique key for this submission
   */
  generateIdempotencyKey(operation: string, subjectId?: string): string {
    const suffix =
      typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
    return subjectId ? `${operation}-${subjectId}-${suffix}` : `${operation}-${suffix}`;
  }

  /**
   * Clamps current hit points into the valid range for the given maximum, so the
   * UI never submits an out-of-range value.
   */
  clampHitPoints(current: number | null | undefined, max: number | null | undefined): number {
    const value = Math.max(0, Math.floor(Number(current ?? 0)));
    if (max !== null && max !== undefined && value > max) {
      return max;
    }
    return value;
  }

  /** Whether the given participant is at 0 hit points (a death save candidate). */
  isAtZeroHitPoints(participant: Participant): boolean {
    return !!participant.hitPoints && participant.hitPoints.current <= 0;
  }

  private rollParams(rollMode: RollMode | undefined, seed: string | undefined): HttpParams {
    let params = new HttpParams();
    if (rollMode != null) {
      params = params.set('rollMode', rollMode);
    }
    if (seed != null) {
      params = params.set('seed', seed);
    }
    return params;
  }

  private actionParams(
    expectedRevision: number | undefined,
    idempotencyKey: string | undefined,
  ): HttpParams {
    let params = new HttpParams();
    if (expectedRevision != null) {
      params = params.set('expectedRevision', String(expectedRevision));
    }
    if (idempotencyKey != null && idempotencyKey !== '') {
      params = params.set('idempotencyKey', idempotencyKey);
    }
    return params;
  }
}
