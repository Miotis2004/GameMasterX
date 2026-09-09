/**
 * Shared contract for the Campaign Dashboard aggregate.
 *
 * These types are consumed by both the client and server workspaces to keep the
 * campaign dashboard API contract in a single place. They mirror the server-side
 * {@code com.gamemasterx.server.campaign.model.CampaignDashboardDto} projection.
 *
 * <p>The dashboard is a single aggregation endpoint that brings together, for a
 * single campaign, the three views a game master needs to open a session: the
 * campaign's <em>members and their roles</em>, the campaign's <em>characters</em>,
 * and the campaign's <em>selected adventure</em>.</p>
 */

/**
 * The role a user holds within a campaign. Roles follow a linear hierarchy from
 * highest to lowest authority: OWNER > GAME_MASTER > PLAYER > OBSERVER.
 */
export type MembershipRole = 'OWNER' | 'GAME_MASTER' | 'PLAYER' | 'OBSERVER';

/**
 * Lifecycle status of a membership in a campaign.
 */
export type MembershipStatus = 'ACTIVE' | 'PENDING' | 'REVOKED';

/**
 * A user's membership in a campaign, including the role and status.
 */
export interface MembershipEntry {
  /** Stable identifier of the membership. */
  id: string;
  /** Logical schema version for the persisted membership document. */
  schemaVersion: number;
  createdAt: string;
  updatedAt: string;
  /** Stable identifier of the member user. */
  userId: string;
  /** Stable identifier of the campaign the member belongs to. */
  campaignId: string;
  /** The role the member holds in the campaign. */
  role: MembershipRole;
  /** The lifecycle status of the membership. */
  status: MembershipStatus;
}

/**
 * The aggregated view returned by the campaign dashboard endpoint.
 */
export interface CampaignDashboardResult {
  /** The campaign the dashboard describes. */
  campaign: CampaignResult;
  /** Stable identifier of the authenticated caller who requested the dashboard. */
  actorId: string | null;
  /** The role the caller holds in the campaign. */
  actorRole: MembershipRole;
  /** The campaign's members with their roles and status. */
  members: MembershipEntry[];
  /** The characters associated with the campaign. */
  characters: CharacterSheet[];
  /** The adventure the campaign has selected, or null when none is selected. */
  selectedAdventure: AdventureResult | null;
}

/**
 * The API-facing representation of a Character sheet as returned by the dashboard.
 * Mirrors the character-sheet contract; included here so the dashboard can be
 * consumed without a second import.
 */
export interface CharacterSheet {
  id: string;
  name: string;
  ownerId: string | null;
  campaignId: string | null;
}

/**
 * The API-facing representation of a Campaign.
 */
export interface CampaignResult {
  id: string;
  schemaVersion: number;
  createdAt: string;
  updatedAt: string;
  name: string;
  description: string | null;
  gameSystem: string | null;
  status: string;
  maxPlayers: number;
  /** Stable identifier of the adventure this campaign has selected, if any. */
  adventureId: string | null;
}
