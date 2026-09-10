/**
 * Shared contract for the Encounter aggregate.
 *
 * <p>These TypeScript types mirror the backend DTOs in
 * {@code com.gamemasterx.server.encounter.model}. They describe the API-facing
 * representation returned by (and accepted from) the {@code /api/encounters}
 * endpoints. The persistence-only {@code revision} counter and {@code
 * schemaVersion} are tracked here for information.</p>
 */

/** Lifecycle states of an encounter. */
export type EncounterStatus = 'DRAFT' | 'ACTIVE' | 'PAUSED' | 'COMPLETED';

/** Identifies who controls a participant during an encounter. */
export type ActorControl = 'SELF' | 'GM' | 'AUTOMATED';

/**
 * The selected rules profile that governs the supported rules subset for an
 * encounter and, through it, the encounter's critical-hit behaviour.
 *
 * <p>The wire value is the rules-subset identifier of the profile (for example
 * {@code "SRD-5.2-2024"}), not the enum constant name.</p>
 */
export type RulesProfile = 'SRD-5.2-2024' | 'SRD-5.1-2014';

/** A grid position, expressed as integer coordinates. */
export interface Position {
  x: number;
  y: number;
}

/** Hit point state for a participant. */
export interface HitPoints {
  max: number;
  current: number;
  temporary: number;
}

/** A condition affecting a participant. */
export interface Condition {
  name: string;
  description: string | null;
  appliesAt: string | null;
  roundsRemaining: number | null;
}

/** A named, possibly limited, resource tracked during an encounter. */
export interface Resource {
  name: string;
  current: number;
  max: number;
  description: string | null;
}

/** API-facing representation of a single participant. */
export interface Participant {
  id: string;
  name: string | null;
  actorControl: ActorControl;
  initiative: number | null;
  position: Position | null;
  hitPoints: HitPoints | null;
  conditions: Condition[];
  resources: Resource[];
}

/** API-facing representation of an encounter (mirrors EncounterDto). */
export interface Encounter {
  id: string;
  schemaVersion: number;
  createdAt: string;
  updatedAt: string;
  campaignId: string;
  name: string | null;
  status: EncounterStatus;
  rulesProfile: RulesProfile;
  participants: Participant[];
  initiativeOrder: string[];
  round: number;
  turn: number;
  revision: number;
}

/** Creation payload accepted by POST /api/encounters. */
export interface EncounterCreateRequest {
  campaignId: string;
  name: string | null;
  /**
   * Wire representation of the selected rules profile. Optional; when omitted
   * the service applies the default profile (SRD-5.2-2024).
   */
  rulesProfile: RulesProfile | null;
}

/** A participant payload accepted when adding a participant. */
export interface ParticipantCreateRequest {
  id: string;
  name: string | null;
  actorControl: ActorControl;
  initiative: number | null;
  position: Position | null;
  hitPoints: HitPoints | null;
  conditions: Condition[];
  resources: Resource[];
}
