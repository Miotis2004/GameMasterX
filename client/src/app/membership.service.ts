import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * The role a user holds within a campaign. Roles follow a linear hierarchy from
 * highest to lowest authority: OWNER > GAME_MASTER > PLAYER > OBSERVER.
 *
 * Mirrors the server-side
 * {@code com.gamemasterx.server.campaign.membership.model.MembershipRole}
 * hierarchy and the shared {@code campaigns-dashboard} contract.
 */
export type MembershipRole = 'OWNER' | 'GAME_MASTER' | 'PLAYER' | 'OBSERVER';

/**
 * Lifecycle status of a membership in a campaign.
 *
 * Mirrors the server-side
 * {@code com.gamemasterx.server.campaign.membership.model.MembershipStatus}.
 */
export type MembershipStatus = 'ACTIVE' | 'PENDING' | 'REVOKED';

/**
 * API-facing representation of a user's membership in a campaign, including the
 * role held and the lifecycle status.
 *
 * Mirrors the server-side
 * {@code com.gamemasterx.server.campaign.membership.model.MembershipDto}.
 */
export interface MembershipEntry {
  id: string;
  schemaVersion: number;
  createdAt: string;
  updatedAt: string;
  userId: string;
  campaignId: string;
  role: MembershipRole;
  status: MembershipStatus;
}

/**
 * The aggregated campaign dashboard view returned by the dashboard endpoint.
 * Only the members and actor fields used by the membership-management UI are
 * modelled here; they mirror the shared {@code campaigns-dashboard} contract.
 */
export interface CampaignDashboardResult {
  campaign: {
    id: string;
    schemaVersion: number;
    createdAt: string;
    updatedAt: string;
    name: string;
    description: string | null;
    gameSystem: string | null;
    status: string;
    maxPlayers: number;
    adventureId: string | null;
  };
  /** Stable identifier of the authenticated caller who requested the dashboard. */
  actorId: string | null;
  /** The role the caller holds in the campaign. */
  actorRole: MembershipRole;
  members: MembershipEntry[];
}

/**
 * Roles that may change another member's role or otherwise manage membership.
 * A member may always readjust their own membership.
 */
export const MANAGING_ROLES: MembershipRole[] = ['OWNER', 'GAME_MASTER'];

/**
 * Client service for campaign membership management.
 *
 * <p>Provides the operations the membership-management interface exposes:
 * listing a campaign's members and dashboard context, accepting a pending
 * membership, revoking one's own membership, and changing roles. Each mutating
 * call returns the updated membership so the UI can reflect the change
 * immediately.</p>
 *
 * <p>All calls are made to the backend API on port 5172.</p>
 *
 * <p><strong>Client visibility is not authorization.</strong> These calls are the
 * enforcement boundary. The frontend may hide controls based on the caller's
 * role as a convenience, but every mutating call below is still authorised
 * server-side and returns {@code 403 Forbidden} when the caller lacks the
 * required role. Frontend code must never treat a hidden control as a security
 * control; it must rely on these backend responses.</p>
 */
@Injectable({ providedIn: 'root' })
export class MembershipService {
  private readonly http = inject(HttpClient);
  private readonly apiBase = 'http://localhost:5172/api';

  /** Returns the campaign dashboard: the caller's role and the members list. */
  getCampaignDashboard(campaignId: string): Observable<CampaignDashboardResult> {
    return this.http.get<CampaignDashboardResult>(
      `${this.apiBase}/campaigns/${campaignId}/dashboard`
    );
  }

  /** Lists the members of a campaign with their roles and status. */
  listMembers(campaignId: string): Observable<MembershipEntry[]> {
    return this.http.get<MembershipEntry[]>(
      `${this.apiBase}/campaigns/${campaignId}/members`
    );
  }

  /**
   * Accepts a pending membership, transitioning it to active. A member may only
   * accept their own pending membership.
   */
  acceptMembership(campaignId: string, userId: string): Observable<MembershipEntry> {
    return this.http.post<MembershipEntry>(
      `${this.apiBase}/campaigns/${campaignId}/members/${userId}/accept`,
      {}
    );
  }

  /**
   * Revokes the caller's own membership, transitioning it to revoked. A member
   * may only revoke their own membership.
   */
  revokeMembership(campaignId: string, userId: string): Observable<MembershipEntry> {
    return this.http.post<MembershipEntry>(
      `${this.apiBase}/campaigns/${campaignId}/members/${userId}/revoke`,
      {}
    );
  }

  /**
   * Changes the role a user holds in a campaign. The caller must be authorised
   * to change roles: changing another member's role requires at least the
   * {@code GAME_MASTER} role, and a member may only adjust their own role.
   */
  updateRole(
    campaignId: string,
    userId: string,
    role: MembershipRole
  ): Observable<MembershipEntry> {
    const params = new HttpParams().set('role', role);
    return this.http.put<MembershipEntry>(
      `${this.apiBase}/campaigns/${campaignId}/members/${userId}/role`,
      {},
      { params }
    );
  }

  /**
   * Query form of the authorization check: returns {@code true} when the given
   * user may perform an action requiring {@code requiredRole} in the campaign.
   */
  isAuthorized(
    campaignId: string,
    userId: string,
    requiredRole: MembershipRole
  ): Observable<boolean> {
    const params = new HttpParams().set('requiredRole', requiredRole);
    return this.http.get<boolean>(
      `${this.apiBase}/campaigns/${campaignId}/members/${userId}/authorize`,
      { params }
    );
  }
}
