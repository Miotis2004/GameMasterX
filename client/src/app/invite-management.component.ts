import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MembershipFacadeService } from './membership-facade.service';
import { InviteRole } from './invite.service';

@Component({
  selector: 'app-invite-management',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './invite-management.component.html',
  styleUrl: './invite-management.component.css',
})
export class InviteManagementComponent implements OnInit {
  public readonly facade = inject(MembershipFacadeService);
  private readonly route = inject(ActivatedRoute);

  readonly campaignId = signal<string | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly roles: InviteRole[] = ['OBSERVER', 'PLAYER', 'GAME_MASTER', 'OWNER'];
  readonly selectedRole = signal<InviteRole>('PLAYER');
  readonly expiresAfterSeconds = signal<number | null>(null);

  ngOnInit(): void {
    this.route.params.subscribe((params) => {
      const id = params['campaignId'];
      if (id) {
        this.campaignId.set(id);
        this.loadInvites();
      }
    });
  }

  loadInvites(): void {
    const id = this.campaignId();
    if (!id) return;
    this.facade.loadInvites(id);
  }

  issueInvite(): void {
    const id = this.campaignId();
    if (!id) return;
    const creation = {
      role: this.selectedRole(),
      expiresAfterSeconds: this.expiresAfterSeconds() ?? undefined
    };
    this.facade.issueInvite(id, creation).subscribe({
      next: () => {
        this.loadInvites();
        this.error.set(null);
      },
      error: (err: unknown) => {
        const message =
          err && typeof err === 'object' && 'message' in err
            ? (err as { message?: string }).message
            : 'Unable to issue invite.';
        this.error.set(message ?? 'Unable to issue invite.');
      }
    });
  }

  generateJoinCode(): void {
    const id = this.campaignId();
    if (!id) return;
    const creation = {
      role: this.selectedRole(),
      expiresAfterSeconds: this.expiresAfterSeconds() ?? undefined
    };
    this.facade.generateJoinCode(id, creation).subscribe({
      next: () => {
        this.loadInvites();
        this.error.set(null);
      },
      error: (err: unknown) => {
        const message =
          err && typeof err === 'object' && 'message' in err
            ? (err as { message?: string }).message
            : 'Unable to generate join code.';
        this.error.set(message ?? 'Unable to generate join code.');
      }
    });
  }

  revokeInvite(invite: any): void {
    const id = this.campaignId();
    if (!id) return;
    this.facade.revokeInvite(invite.inviteCode, id).subscribe({
      next: () => this.loadInvites(),
      error: (err: unknown) => {
        const message =
          err && typeof err === 'object' && 'message' in err
            ? (err as { message?: string }).message
            : 'Unable to revoke invite.';
        this.error.set(message ?? 'Unable to revoke invite.');
      }
    });
  }
}
