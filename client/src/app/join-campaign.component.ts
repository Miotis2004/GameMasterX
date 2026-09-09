import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { InviteService, MembershipResult } from './invite.service';

/**
 * Redeem mode for a code typed into the join form.
 *
 * - {@code INVITE}: a targeted single-use invite that creates an active
 *   membership.
 * - {@code JOIN_CODE}: a local join code that creates a pending membership.
 */
export type JoinMode = 'INVITE' | 'JOIN_CODE';

/**
 * The join-campaign screen accepts an invite code or a local join code and
 * redeems it through {@link InviteService}. It reports loading, validation,
 * failure, and the resulting membership state (active or pending).
 */
@Component({
  selector: 'app-join-campaign',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './join-campaign.component.html',
  styleUrl: './join-campaign.component.css',
})
export class JoinCampaignComponent implements OnInit {
  private readonly inviteService = inject(InviteService);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(FormBuilder);

  readonly form = this.formBuilder.group({
    code: ['', [Validators.required, Validators.minLength(4), Validators.maxLength(64)]],
    mode: ['INVITE'],
  });

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly membership = signal<MembershipResult | null>(null);

  readonly modes: { value: JoinMode; label: string; description: string }[] = [
    {
      value: 'INVITE',
      label: 'Invite code',
      description: 'A single-use invite creates an active membership.',
    },
    {
      value: 'JOIN_CODE',
      label: 'Join code',
      description: 'A local join code creates a pending membership awaiting approval.',
    },
  ];

  ngOnInit(): void {
    this.form.updateValueAndValidity();
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    const { code, mode } = this.form.getRawValue() as { code: string; mode: JoinMode };

    if (mode === 'JOIN_CODE') {
      this.inviteService.redeemJoinCode(code).subscribe(this.handleResult());
    } else {
      this.inviteService.redeemInvite(code).subscribe(this.handleResult());
    }
  }

  private handleResult() {
    return {
      next: (membership: MembershipResult) => {
        this.membership.set(membership);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        const message =
          err && typeof err === 'object' && 'message' in err
            ? (err as { message?: string }).message
            : 'Unable to join. Please check your code and try again.';
        this.error.set(message ?? 'Unable to join. Please check your code and try again.');
      },
    };
  }
}
