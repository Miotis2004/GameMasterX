import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import {
  MANAGING_ROLES,
  MembershipEntry,
  MembershipRole,
  MembershipService,
} from './membership.service';
import { AuthService } from './auth.service';

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
  private readonly membershipService = inject(MembershipService);
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  readonly campaignId = signal<string | null>(null);
  readonly actorId = signal<string | null>(null);
  readonly actorRole = signal<MembershipRole | null>(null);
  readonly members = signal<MembershipEntry[]>([]);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  /** Every role in the hierarchy, highest authority first. */
  readonly roles: MembershipRole[] = ['OWNER', 'GAME_MASTER', 'PLAYER', 'OBSERVER'];

  /** Linear level of each role, matching the server-side {@code MembershipRole} hierarchy. */
  private readonly roleLevel = new Map<MembershipRole, number>(
    this.roles.map((role, index) => [role, index] as const)
  );

  ngOnInit(): void {
    this.actorId.set(this.authService.username());
    this.route.params.subscribe((params) => {
      const id = params['campaignId'];
      if (id) {
        this.campaignId.set(id);
        this.load();
      }
    });
  }

  load(): void {
    if (!this.campaignId()) {
      return;
    }
    const campaignId = this.campaignId();
    if (!campaignId) {
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    this.membershipService
      .getCampaignDashboard(campaignId)
      .subscribe({
        next: (dashboard) => {
          this.actorId.set(dashboard.actorId);
          this.actorRole.set(dashboard.actorRole);
          this.members.set(dashboard.members);
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.loading.set(false);
          this.applyError(
            err,
            'Unable to load campaign members. Please try again later.'
          );
        },
      });
  }

  /** True when the current caller is the member and the membership is pending. */
  canAccept(member: MembershipEntry): boolean {
    return member.status === 'PENDING' && member.userId === this.actorId();
  }

  /** True when the current caller is the member and the membership is live. */
  canRevoke(member: MembershipEntry): boolean {
    return member.status !== 'REVOKED' && member.userId === this.actorId();
  }

  /**
   * True when the current caller may change this member's role: a member may
   * always readjust their own role, and an authorised manager may change the
   * role of other members.
   */
  canManageRole(member: MembershipEntry): boolean {
    if (member.userId === this.actorId()) {
      return true;
    }
    return this.actorRole() != null && MANAGING_ROLES.includes(this.actorRole()!);
  }

  /**
   * The role options offered for a member's role control.
   *
   * <p>An authorised manager editing another member sees every role. A member
   * editing their own role only sees roles at or below their current role,
   * mirroring the server rule that a member cannot elevate themselves. Either
   * way the backend remains the authoritative enforcement boundary, so the
   * options shown here are presentation only.</p>
   */
  roleOptionsFor(member: MembershipEntry): MembershipRole[] {
    if (member.userId !== this.actorId()) {
      return this.roles;
    }
    const currentLevel = this.roleLevel.get(member.role) ?? 0;
    return this.roles.filter((role) => (this.roleLevel.get(role) ?? 0) <= currentLevel);
  }

  /** Applies an accepted membership back into the list, preserving order. */
  private apply(updated: MembershipEntry): void {
    const current = this.members();
    const index = current.findIndex((m) => m.id === updated.id);
    if (index === -1) {
      this.members.set([...current, updated]);
    } else if (index === current.length - 1) {
      this.members.set([...current.slice(0, index), updated]);
    } else {
      this.members.set([
        ...current.slice(0, index),
        updated,
        ...current.slice(index + 1),
      ]);
    }
  }

  /** Applies an error from a mutating action without disturbing the list. */
  private applyError(err: unknown, fallback: string): void {
    const message =
      err && typeof err === 'object' && 'message' in err
        ? (err as { message?: string }).message
        : fallback;
    this.error.set(message ?? fallback);
  }

  accept(member: MembershipEntry): void {
    const campaignId = this.campaignId();
    if (!campaignId) {
      return;
    }
    this.error.set(null);
    this.membershipService
      .acceptMembership(campaignId, member.userId)
      .subscribe({
        next: (updated) => this.apply(updated),
        error: (err: unknown) =>
          this.applyError(
            err,
            'Unable to accept membership. Please try again later.'
          ),
      });
  }

  revoke(member: MembershipEntry): void {
    const campaignId = this.campaignId();
    if (!campaignId) {
      return;
    }
    this.error.set(null);
    this.membershipService
      .revokeMembership(campaignId, member.userId)
      .subscribe({
        next: (updated) => this.apply(updated),
        error: (err: unknown) =>
          this.applyError(
            err,
            'Unable to leave the campaign. Please try again later.'
          ),
      });
  }

  changeRole(member: MembershipEntry, role: MembershipRole): void {
    if (role === member.role) {
      return;
    }
    const campaignId = this.campaignId();
    if (!campaignId) {
      return;
    }
    this.error.set(null);
    this.membershipService
      .updateRole(campaignId, member.userId, role)
      .subscribe({
        next: (updated) => this.apply(updated),
        error: (err: unknown) =>
          this.applyError(
            err,
            'Unable to update role. Please try again later.'
          ),
      });
  }
}
