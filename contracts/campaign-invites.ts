/**
 * Shared contracts for campaign invites and local join codes.
 *
 * These types are consumed by both the client and server workspaces to keep the
 * invite/join-code API contract in a single place.
 */

/**
 * Role a joiner receives when redeeming an invite or join code. Mirrors the
 * server-side {@link MembershipRole} hierarchy, ordered from highest to lowest
 * authority: OWNER, GAME_MASTER, PLAYER, OBSERVER.
 */
export type InviteRole = 'OWNER' | 'GAME_MASTER' | 'PLAYER' | 'OBSERVER';

/**
 * How redeeming an invite affects the joiner's membership.
 *
 * - DIRECT: redemption creates an active membership (targeted invites).
 * - PENDING: redemption creates a pending membership awaiting approval
 *   (local join codes).
 */
export type InviteRedeemMode = 'DIRECT' | 'PENDING';

/**
 * Lifecycle status of an invite or join code.
 */
export type InviteStatus = 'PENDING' | 'USED' | 'REVOKED' | 'EXPIRED';

/**
 * API-facing representation of an issued invite or generated join code.
 */
export interface InviteResult {
  id: string;
  inviteCode: string;
  campaignId: string;
  issuedBy: string | null;
  role: InviteRole;
  redeemMode: InviteRedeemMode;
  status: InviteStatus;
  expiresAt: string | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * Status of a membership created by redeeming an invite or join code.
 */
export type MembershipStatus = 'ACTIVE' | 'PENDING' | 'REVOKED';

/**
 * API-facing representation of a membership.
 */
export interface MembershipResult {
  id: string;
  userId: string;
  campaignId: string;
  role: InviteRole;
  status: MembershipStatus;
}

/**
 * Request payload for issuing an invite or generating a local join code.
 */
export interface InviteCreation {
  /** The role granted to the joiner upon redemption. */
  role: InviteRole;
  /**
   * Optional lifetime in seconds. When omitted, invites never expire and join
   * codes use their default lifetime.
   */
  expiresAfterSeconds?: number;
}

/**
 * Request payload for redeeming an invite or join code.
 */
export interface RedeemInvite {
  /** Stable identifier of the user redeeming the code (optional). */
  userId?: string;
}
