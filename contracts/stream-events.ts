/**
 * Shared contracts for typed stream events consumed by the Angular client.
 *
 * <p>These TypeScript types define the event shapes emitted by the backend
 * stream endpoints. All events carry a {@code turnId} and a monotonically
 * increasing {@code sequenceNumber} so the client can reconstruct ordering
 * and detect gaps. Payloads are player-visible only; GM-only secrets (secret
 * notes, gmNotes, secret context lines) are never included in stream events.</p>
 *
 * <p>The backend API runs on port 5172; the Angular client development server
 * runs on port 5322.</p>
 */

/** Base fields present on every stream event. */
export interface StreamEventBase {
  /** Identifier of the logical turn/stream the event belongs to. */
  turnId: string;
  /** Monotonic sequence number within the turn. */
  sequenceNumber: number;
  /** ISO-8601 timestamp of emission. */
  timestamp: string;
}

/** Acceptance of a proposed operation or membership acceptance. */
export interface AcceptanceEvent extends StreamEventBase {
  type: 'acceptance';
  /** Identifier of the accepted proposal or membership. */
  acceptedId: string;
  /** Actor who performed the acceptance. */
  acceptedBy: string;
  /** Optional human-readable reason, player-safe. */
  reason?: string;
}

/** Incremental narrative text delta. */
export interface NarrativeDeltaEvent extends StreamEventBase {
  type: 'narrative_delta';
  /** Player-visible narrative delta. GM-only secrets are stripped. */
  delta: string;
}

/** A tool/operation proposed by the AI or by the system. */
export interface ProposedToolEvent extends StreamEventBase {
  type: 'proposed_tool';
  /** Name of the tool/operation type. */
  toolName: string;
  /** Player-safe parameters for the proposal. */
  parameters: Record<string, unknown>;
  /** Justification shown to players; no GM secrets. */
  justification?: string;
}

/** Result of a tool/operation execution. */
export interface ToolResultEvent extends StreamEventBase {
  type: 'tool_result';
  /** Name of the tool/operation that was executed. */
  toolName: string;
  /** Player-visible result summary. */
  result: unknown;
  /** Whether the execution succeeded. */
  success: boolean;
}

/** A turn that has been committed to the encounter state. */
export interface CommittedTurnEvent extends StreamEventBase {
  type: 'committed_turn';
  /** Turn number within the encounter. */
  turnNumber: number;
  /** Actor identifier for the committed turn. */
  actorId: string;
  /** Player-visible summary of actions committed in this turn. */
  actions: Array<{
    actionType: string;
    description: string;
  }>;
}

/** Failure or error event emitted on the stream. */
export interface FailureEvent extends StreamEventBase {
  type: 'failure';
  /** Machine-readable error code. */
  code: string;
  /** Player-safe error message. */
  message: string;
  /** Optional details, player-safe. */
  details?: Record<string, unknown>;
}

/** Union of all stream event types. */
export type StreamEvent =
  | AcceptanceEvent
  | NarrativeDeltaEvent
  | ProposedToolEvent
  | ToolResultEvent
  | CommittedTurnEvent
  | FailureEvent;
