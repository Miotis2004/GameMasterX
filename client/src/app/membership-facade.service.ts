import { Injectable, signal, inject } from '@angular/core';
import { MembershipService, MembershipEntry, MembershipRole } from './membership.service';
import { InviteService, InviteResult, InviteCreation } from './invite.service';

/**
 * Facade for membership and invitation management, exposing reactive signals for UI state.
 * Combines MembershipService and InviteService to provide role-aware membership views.
 * All mutating actions delegate to the backend for authorization enforcement.
 */
@Injectable({ providedIn: 'root' })
export class MembershipFacadeService {
  private readonly membershipService = inject(MembershipService);
  private readonly inviteService = inject(InviteService);

  readonly members = signal<MembershipEntry[]>([]);
  readonly actorId = signal<string | null>(null);
  readonly actorRole = signal<MembershipRole | null>(null);
  readonly invites = signal<InviteResult[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  /** Load dashboard data for a campaign, populating members and actor context. */
  loadCampaignMembers(campaignId: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.membershipService.getCampaignDashboard(campaignId).subscribe({
      next: (dashboard) => {
        this.actorId.set(dashboard.actorId);
        this.actorRole.set(dashboard.actorRole);
        this.members.set(dashboard.members);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        const message =
          err && typeof err === 'object' && 'message' in err
            ? (err as { message?: string }).message
            : 'Unable to load members.';
        this.error.set(message ?? 'Unable to load members.');
      }
    });
  }

  /** Load invites for a campaign. */
  loadInvites(campaignId: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.inviteService.listInvites(campaignId).subscribe({
      next: (invites) => {
        this.invites.set(invites);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        const message =
          err && typeof err === 'object' && 'message' in err
            ? (err as { message?: string }).message
            : 'Unable to load invites.';
        this.error.set(message ?? 'Unable to load invites.');
      }
    });
  }

  /** Accept a pending membership. */
  acceptMembership(campaignId: string, userId: string) {
    this.error.set(null);
    return this.membershipService.acceptMembership(campaignId, userId);
  }

  /** Revoke a membership. */
  revokeMembership(campaignId: string, userId: string) {
    this.error.set(null);
    return this.membershipService.revokeMembership(campaignId, userId);
  }

  /** Change a member's role. */
  updateRole(campaignId: string, userId: string, role: MembershipRole) {
    this.error.set(null);
    return this.membershipService.updateRole(campaignId, userId, role);
  }

  /** Issue an invite for a campaign. */
  issueInvite(campaignId: string, creation: InviteCreation) {
    this.error.set(null);
    return this.inviteService.issueInvite(campaignId, creation);
  }

  /** Generate a join code for a campaign. */
  generateJoinCode(campaignId: string, creation: InviteCreation) {
    this.error.set(null);
    return this.inviteService.generateJoinCode(campaignId, creation);
  }

  /** Revoke an invite by code. */
  revokeInvite(code: string, campaignId: string) {
    this.error.set(null);
    return this.inviteService.revokeInvite(code, campaignId);
  }

  /** Update local members signal after a mutation. */
  applyMemberUpdate(updated: MembershipEntry): void {
    const current = this.members();
    const index = current.findIndex((m) => m.id === updated.id);
    if (index === -1) {
      this.members.set([...current, updated]);
    } else {
      this.members.set([
        ...current.slice(0, index),
        updated,
        ...current.slice(index + 1)
      ]);
    }
  }

  /** Clear error state. */
  clearError(): void {
    this.error.set(null);
  }
}
