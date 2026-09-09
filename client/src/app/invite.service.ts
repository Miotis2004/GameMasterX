import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Role a joiner receives when redeeming an invite or join code. Mirrors the
 * server-side {@code MembershipRole} hierarchy.
 */
export type InviteRole = 'OBSERVER' | 'PLAYER' | 'GAME_MASTER' | 'OWNER';

/**
 * API-facing representation of an issued invite or generated join code.
 */
export interface InviteResult {
  id: string;
  inviteCode: string;
  campaignId: string;
  issuedBy: string | null;
  role: InviteRole;
  redeemMode: 'DIRECT' | 'PENDING';
  status: 'PENDING' | 'USED' | 'REVOKED' | 'EXPIRED';
  expiresAt: string | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * API-facing representation of a membership created by redeeming an invite or
 * join code.
 */
export interface MembershipResult {
  id: string;
  userId: string;
  campaignId: string;
  role: InviteRole;
  status: 'ACTIVE' | 'PENDING' | 'REVOKED';
}

/**
 * Request payload for issuing an invite or generating a local join code.
 */
export interface InviteCreation {
  role: InviteRole;
  expiresAfterSeconds?: number;
}

/**
 * Client service for campaign invites and local join codes.
 *
 * <p>Provides methods for a campaign owner or game master to issue invites and
 * generate local join codes scoped to a specific campaign and role, and for a
 * user to redeem them, creating a pending or active membership.</p>
 */
@Injectable({ providedIn: 'root' })
export class InviteService {
  private readonly apiBase = 'http://localhost:5172/api';

  constructor(private http: HttpClient) {}

  /** Issues a targeted invite for a campaign. */
  issueInvite(campaignId: string, creation: InviteCreation): Observable<InviteResult> {
    return this.http.post<InviteResult>(`${this.apiBase}/invites/${campaignId}/invites`, creation);
  }

  /** Generates a local join code for a campaign. */
  generateJoinCode(campaignId: string, creation: InviteCreation): Observable<InviteResult> {
    return this.http.post<InviteResult>(`${this.apiBase}/invites/${campaignId}/join-codes`, creation);
  }

  /** Lists all invites issued for a campaign. */
  listInvites(campaignId: string): Observable<InviteResult[]> {
    return this.http.get<InviteResult[]>(`${this.apiBase}/invites/campaigns/${campaignId}`);
  }

  /** Views a single invite by its code. */
  getInvite(code: string): Observable<InviteResult> {
    return this.http.get<InviteResult>(`${this.apiBase}/invites/codes/${code}`);
  }

  /** Redeems an invite, creating an active membership. */
  redeemInvite(code: string, userId?: string): Observable<MembershipResult> {
    return this.http.post<MembershipResult>(
      `${this.apiBase}/invites/${code}/redeem`,
      userId ? { userId } : {}
    );
  }

  /** Redeems a local join code, creating a pending membership. */
  redeemJoinCode(code: string, userId?: string): Observable<MembershipResult> {
    return this.http.post<MembershipResult>(
      `${this.apiBase}/invites/join-codes/${code}/redeem`,
      userId ? { userId } : {}
    );
  }

  /** Revokes a pending invite by its code. */
  revokeInvite(code: string, campaignId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiBase}/invites/${code}`, {
      params: { campaignId },
    });
  }
}
