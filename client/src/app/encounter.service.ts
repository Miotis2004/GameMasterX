import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import {
  FormBuilder,
  FormControl,
  FormGroup,
  FormArray,
  AbstractControl,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { Observable } from 'rxjs';
import {
  Encounter,
  EncounterCreateRequest,
  ParticipantCreateRequest,
  ActorControl,
  RulesProfile,
  HitPoints,
} from '@contracts/encounter';

/**
 * Shared contract for the Encounter aggregate.
 *
 * <p>These types are the single source of truth for the API shape. They live in
 * the repository-wide {@code contracts/encounter.ts} file (shared with the
 * backend) and are re-exported here so client code can depend on the shared
 * contract rather than a local copy.</p>
 */
export type {
  Encounter,
  ActorControl,
  RulesProfile,
  HitPoints,
  ParticipantCreateRequest,
  EncounterCreateRequest as EncounterContractCreateRequest,
};

/**
 * Client-facing representation of an encounter, as returned by the API.
 *
 * Mirrors the server-side {@code com.gamemasterx.server.encounter.model.EncounterDto}.
 */
export type EncounterResult = Encounter;

/** Creation payload accepted by {@link POST /api/encounters}. */
export type EncounterSetup = EncounterCreateRequest;

/** A participant payload accepted when adding a participant. */
export type ParticipantSetup = ParticipantCreateRequest;

/**
 * Validation constraints for a new encounter, kept on the client so the reactive
 * form can validate before the request is sent. The server enforces the same
 * constraints through Bean Validation.
 */
export const ENCOUNTER_NAME_MAX = 100;
export const ENCOUNTER_MIN_PLAYERS = 1;

/** Rules profile options offered on the encounter setup view. */
export const RULES_PROFILES: RulesProfile[] = ['SRD-5.2-2024', 'SRD-5.1-2014'];

/** Default rules profile applied by the service when none is selected. */
export const DEFAULT_RULES_PROFILE: RulesProfile = 'SRD-5.2-2024';

/**
 * Client service for the encounter listing, creation and participant endpoints.
 *
 * <p>Setup reads a campaign's encounters via
 * {@link listCampaignEncounters}; the encounter setup view writes an encounter
 * via {@link createEncounter} and adds player/non-player participants via
 * {@link addParticipant}. Both use the backend API on port 5172.</p>
 */
@Injectable({ providedIn: 'root' })
export class EncounterService {
  private readonly http = inject(HttpClient);
  private readonly formBuilder = inject(FormBuilder);

  private readonly apiBase = 'http://localhost:5172/api';

  /** Lists the encounters of a campaign (or every accessible encounter). */
  listCampaignEncounters(campaignId?: string): Observable<EncounterResult[]> {
    let params = new HttpParams();
    if (campaignId) {
      params = params.set('campaignId', campaignId);
    }
    return this.http.get<EncounterResult[]>(`${this.apiBase}/encounters`, { params });
  }

  /** Reads a single encounter by id. */
  fetchEncounter(id: string): Observable<EncounterResult> {
    return this.http.get<EncounterResult>(`${this.apiBase}/encounters/${id}`);
  }

  /** Creates a new draft encounter. */
  createEncounter(setup: EncounterSetup): Observable<EncounterResult> {
    return this.http.post<EncounterResult>(`${this.apiBase}/encounters`, setup);
  }

  /** Adds or replaces a participant in a draft encounter. */
  addParticipant(encounterId: string, participant: ParticipantSetup): Observable<EncounterResult> {
    return this.http.post<EncounterResult>(
      `${this.apiBase}/encounters/${encounterId}/participants`,
      participant,
    );
  }

  /** Removes a participant from a draft encounter. */
  removeParticipant(encounterId: string, participantId: string): Observable<EncounterResult> {
    return this.http.delete<EncounterResult>(
      `${this.apiBase}/encounters/${encounterId}/participants/${participantId}`,
    );
  }

  /**
   * The reactive form model for creating an encounter. Field validators mirror
   * the server-side Bean Validation constraints on
   * {@code com.gamemasterx.server.encounter.model.EncounterCreateRequest}.
   */
  createForm(): FormGroup {
    return this.formBuilder.group({
      name: ['', [Validators.required, Validators.maxLength(ENCOUNTER_NAME_MAX)]],
      campaignId: ['', [Validators.required, Validators.maxLength(100)]],
      rulesProfile: [DEFAULT_RULES_PROFILE, Validators.required],
    });
  }

  /**
   * The reactive form model for a single participant. Players (
   * {@link ActorControl#SELF}) are owned by a character; NPCs and monsters
   * ({@link ActorControl#GM}) are controlled by the game master. The hit-point
   * fields are optional so that token-only participants can be added without
   * combat stats.
   */
  createParticipantForm(actorControl: ActorControl): FormGroup {
    return this.formBuilder.group({
      id: ['', Validators.required],
      name: ['', [Validators.required, Validators.maxLength(100)]],
      actorControl: [actorControl, Validators.required],
      // Initiative is established server-side at roll time; the client never
      // supplies it, so it is intentionally left unset here.
      initiative: [null as number | null],
      // Hit-point fields are flattened on the participant group so the template
      // can bind them directly. They are optional (blank = token-only).
      maxHitPoints: [null as number | null, [Validators.required, Validators.min(0)]],
      currentHitPoints: [null as number | null, [Validators.min(0)]],
      movementSpeed: [10, [Validators.min(0)]],
    },
    { validators: currentHitPointsAtMostMax() });
  }

  /** The form array that holds every participant entry being set up. */
  participantEntries(): FormArray<FormGroup> {
    return this.formBuilder.array<FormGroup>([]);
  }

  /**
   * Appends a new, blank participant entry with the given actor control. A
   * stable id is generated client-side; the backend accepts it directly and the
   * same id is used to reference the participant for later edits and removals.
   */
  appendParticipant(entries: FormArray<FormGroup>, actorControl: ActorControl): void {
    const group = this.createParticipantForm(actorControl);
    group.get('actorControl')?.setValue(actorControl);
    group.get('id')?.setValue(generateParticipantId());
    entries.push(group);
  }

  /** Removes the participant entry at the given index. */
  removeParticipantEntry(entries: FormArray<FormGroup>, index: number): void {
    entries.removeAt(index);
  }

  /** Maps a participant form group to the wire payload accepted by the API. */
  toParticipantRequest(group: FormGroup): ParticipantSetup {
    const payload: ParticipantSetup = {
      id: group.get('id')?.value ?? '',
      name: group.get('name')?.value ?? null,
      actorControl: (group.get('actorControl')?.value as ActorControl) ?? 'GM',
      initiative: null,
      position: null,
      movementSpeed: movementSpeedValue(group),
      hitPoints: null,
      conditions: [],
      resources: [],
    };
    const max = group.get('maxHitPoints')?.value ?? null;
    const current = group.get('currentHitPoints')?.value ?? null;
    if (max !== null && max !== undefined && max > 0) {
      payload.hitPoints = {
        max: Math.max(0, Math.floor(max)),
        current: current !== null && current !== undefined ? Math.max(0, Math.floor(current)) : 0,
        temporary: 0,
      } satisfies HitPoints;
    }
    return payload;
  }
}

/** Ensures current hit points never exceed the configured maximum. */
export function currentHitPointsAtMostMax(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const group = control as FormGroup<Record<string, FormControl<number | string | null>>>;
    const max = group.get('maxHitPoints')?.value ?? null;
    const current = group.get('currentHitPoints')?.value ?? null;
    if (max === null || current === null) {
      return null;
    }
    return current > max ? { currentExceedsMax: { max } } : null;
  };
}

/**
 * Reads the movement speed from a participant form group, defaulting to zero
 * when unset or negative. The backend stores it as an integer count of grid
 * squares.
 */
function movementSpeedValue(group: FormGroup): number {
  const value = group.get('movementSpeed')?.value ?? 0;
  const parsed = Math.floor(Number(value));
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 0;
}

/**
 * Generates a stable, client-side identifier for a participant. Uses the
 * platform UUID API when available and falls back to a random suffix otherwise,
 * so it works across the browsers the setup view supports.
 */
export function generateParticipantId(): string {
  const base =
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `participant-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
  return base;
}
