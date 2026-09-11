/**
 * Shared contract for campaign polls.
 *
 * These types mirror the server-side {@code com.gamemasterx.server.campaign.poll.model}
 * classes and are used by both client and server.
 */

export type PollStatus = 'OPEN' | 'CLOSED';

export interface PollCreateRequest {
  question: string;
  options: string[];
}

export interface PollVoteRequest {
  optionIndex: number;
}

export interface PollResult {
  id: string;
  schemaVersion: number;
  createdAt: string;
  updatedAt: string;
  campaignId: string;
  question: string;
  options: string[];
  voteCounts: Record<string, number>;
  status: PollStatus;
  createdBy: string;
}

export interface PollListResult {
  polls: PollResult[];
}
