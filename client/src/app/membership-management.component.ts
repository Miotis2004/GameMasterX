import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import {
  MANAGING_ROLES,
  MembershipEntry,
  MembershipRole,
} from './membership.service';
import { AuthFacadeService } from './auth-facade.service';
import { MembershipFacadeService } from './membership-facade.service';

/**
 * Membership management interface for a single campaign.
 *
 * <p>Displays the campaign's members with their roles and lifecycle status and
 * exposes the actions the current caller is allowed to perform:</p>
 * <ul>
 *   <li>A member may <em>accept</em> their own pending membership.</li>
 *   <li>A member may <em>revoke (leave)</em> their own membership.</li>
 *   <li>An authorised caller (a member adjusting their own role, or a
 *       {@code GAME_MASTER}/{@code OWNER} managing others) may <em>change roles</em>.</li>
 * </ul>
 *
 * <p>Every mutating action updates the in-memory member list immediately so the
 * UI reflects role changes, acceptances, and revocations without a reload.
 * Actions the caller is not authorised for are never offered.</p>
 *
 * <p><strong>Visibility is not enforcement.</strong> The controls shown here are a
 * convenience derived from the caller's campaign role so that the interface stays
 * uncluttered. Hiding a control never provides security: every mutating action is
 * still authorised server-side by {@code MembershipService} and returns
 * {@code 403 Forbidden} when the caller lacks the required role. The backend is
 * the single enforcement boundary; the absence of a control in the UI must never
 * be relied upon as an access control.</p>
 */
@Component({
  selector: 'app-membership-management',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './membership-management.component.html',
  styleUrl: './membership-management.component.css',
})
export class MembershipManagementComponent implements OnInit {
  public readonly facade = inject(MembershipFacadeService);
  private readonly authFacade = inject(AuthFacadeService);
  private readonly route = inject(ActivatedRoute);

  readonly campaignId = signal<string | null>(null);

  /** Every role in the hierarchy, highest authority first. */
  readonly roles: MembershipRole[] = ['OWNER', 'GAME_MASTER', 'PLAYER', 'OBSERVER'];

  /** Linear level of each role, matching the server-side {@code MembershipRole} hierarchy. */
  private readonly roleLevel = new Map<MembershipRole, number>(
    this.roles.map((role, index) => [role, index] as const)
  );

  ngOnInit(): void {
    this.route.params.subscribe((params) => {
      const id = params['campaignId'];
      if (id) {
        this.campaignId.set(id);
        this.load();
      }
    });
  }

  load(): void {
    const id = this.campaignId();
    if (!id) return;
    this.facade.loadCampaignMembers(id);
  }

  /** True when the current caller is the member and the membership is pending. */
  canAccept(member: MembershipEntry): boolean {
    return member.status === 'PENDING' && member.userId === this.facade.actorId();
  }

  /** True when the current caller is the member and the membership is live. */
  canRevoke(member: MembershipEntry): boolean {
    return member.status !== 'REVOKED' && member.userId === this.facade.actorId();
  }

  /**
   * True when the current caller may change this member's role: a member may
   * always readjust their own role, and an authorised manager may change the
   * role of other members.
   */
  canManageRole(member: MembershipEntry): boolean {
    if (member.userId === this.facade.actorId()) {
      return true;
    }
    const role = this.facade.actorRole();
    return role != null && MANAGING_ROLES.includes(role);
  }

  /**
   * The role options offered for a member's role control.
   */
  roleOptionsFor(member: MembershipEntry): MembershipRole[] {
    if (member.userId !== this.facade.actorId()) {
      return this.roles;
    }
    const currentLevel = this.roleLevel.get(member.role) ?? 0;
    return this.roles.filter((role) => (this.roleLevel.get(role) ?? 0) <= currentLevel);
  }

  accept(member: MembershipEntry): void {
    const campaignId = this.campaignId();
    if (!campaignId) return;
    this.facade.clearError();
    this.facade.acceptMembership(campaignId, member.userId).subscribe({
      next: (updated) => this.facade.applyMemberUpdate(updated),
      error: (err: unknown) => {
        const message =
          err && typeof err === 'object' && 'message' in err
            ? (err as { message?: string }).message
            : 'Unable to accept membership. Please try again later.';
        this.facade.error.set(message ?? 'Unable to accept membership.');
      }
    });
  }

  revoke(member: MembershipEntry): void {
    const campaignId = this.campaignId();
    if (!campaignId) return;
    this.facade.clearError();
    this.facade.revokeMembership(campaignId, member.userId).subscribe({
      next: (updated) => this.facade.applyMemberUpdate(updated),
      error: (err: unknown) => {
        const message =
          err && typeof err === 'object' && 'message' in err
            ? (err as { message?: string }).message
            : 'Unable to leave the campaign. Please try again later.';
        this.facade.error.set(message ?? 'Unable to leave the campaign.');
      }
    });
  }

  changeRole(member: MembershipEntry, role: MembershipRole): void {
    if (role === member.role) return;
    const campaignId = this.campaignId();
    if (!campaignId) return;
    this.facade.clearError();
    this.facade.updateRole(campaignId, member.userId, role).subscribe({
      next: (updated) => this.facade.applyMemberUpdate(updated),
      error: (err: unknown) => {
        const message =
          err && typeof err === 'object' && 'message' in err
            ? (err as { message?: string }).message
            : 'Unable to update role. Please try again later.';
        this.facade.error.set(message ?? 'Unable to update role.');
      }
    });
  }
}
