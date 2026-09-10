import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { EncounterService, RULES_PROFILES } from './encounter.service';
import {
  ActorControl,
  Encounter,
} from '@contracts/encounter';

/**
 * The encounter setup view.
 *
 * <p>It creates a new draft encounter through
 * {@link EncounterService.createEncounter} and then adds player (
 * {@link ActorControl#SELF}) and non-player (
 * {@link ActorControl#GM}) participants via
 * {@link EncounterService.addParticipant}. It uses a reactive form whose
 * validators mirror the server-side constraints, and it reports loading,
 * validation, and failure states with safe, user-facing messages.</p>
 */
@Component({
  selector: 'app-encounter-setup',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './encounter-setup.component.html',
  styleUrl: './encounter-setup.component.css',
})
export class EncounterSetupComponent implements OnInit {
  private readonly service = inject(EncounterService);
  private readonly route = inject(ActivatedRoute);

  readonly createForm = this.service.createForm();
  readonly participantEntries = this.service.participantEntries();
  readonly rulesProfiles = RULES_PROFILES;
  readonly actorControlLabels: Record<ActorControl, string> = {
    SELF: 'Player',
    GM: 'Non-player (GM)',
    AUTOMATED: 'Automated',
  };

  readonly encounter = signal<Encounter | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly participantError = signal<string | null>(null);

  ngOnInit(): void {
    // Seed the initial validation state without requiring user interaction.
    this.createForm.updateValueAndValidity();
    // Pre-fill the campaign id when supplied as a query parameter (for example
    // when navigating from a campaign's encounter list).
    const campaignId = this.route.snapshot.queryParamMap.get('campaignId');
    if (campaignId) {
      this.createForm.get('campaignId')?.setValue(campaignId);
    }
  }

  /** Whether the encounter has been created and we are in the participant phase. */
  get awaitingParticipants(): boolean {
    return !!this.encounter();
  }

  /** Whether the encounter name field is currently invalid. */
  get onCreateNameInvalid(): boolean {
    return !!this.createForm.get('name')?.invalid;
  }

  /** Whether the campaign id field is currently invalid. */
  get onCreateCampaignInvalid(): boolean {
    return !!this.createForm.get('campaignId')?.invalid;
  }

  /** The number of participants persisted for the current encounter, if any. */
  get summaryParticipantCount(): number {
    return this.encounter()?.participants?.length ?? 0;
  }

  /** A display title for the current encounter (name when present, otherwise id). */
  get summaryTitle(): string {
    return this.encounter()?.name ?? this.encounter()?.id ?? '';
  }

  submitEncounter(): void {
    if (this.createForm.invalid) {
      this.createForm.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    const value = this.createForm.getRawValue() as Parameters<
      EncounterService['createEncounter']
    >[0];
    this.service.createEncounter(value).subscribe({
      next: (encounter) => {
        this.encounter.set(encounter);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(this.resolveError(err));
      },
    });
  }

  /** Appends a new blank player participant entry to the form. */
  addPlayer(): void {
    this.service.appendParticipant(this.participantEntries, 'SELF');
  }

  /** Appends a new blank non-player participant entry to the form. */
  addNonPlayer(): void {
    this.service.appendParticipant(this.participantEntries, 'GM');
  }

  removeParticipantEntry(index: number): void {
    this.service.removeParticipantEntry(this.participantEntries, index);
  }

  /** Persists every participant entry already present in the form. */
  saveParticipants(): void {
    const entries = this.participantEntries.controls;
    if (entries.length === 0) {
      return;
    }
    // Mark every entry as touched so inline validation is surfaced before we
    // attempt any write.
    entries.forEach((entry) => entry.markAllAsTouched());
    if (this.participantEntries.invalid) {
      return;
    }

    this.loading.set(true);
    this.participantError.set(null);
    const encounterId = this.encounter()?.id ?? '';
    if (!encounterId) {
      this.participantError.set('Cannot add participants: the encounter has no id yet.');
      this.loading.set(false);
      return;
    }

    // Participants are persisted one at a time so that a partial set can still
    // be saved and so each failure maps to a clear, single outcome.
    let pending = entries.length;
    let completed = 0;
    let failed = 0;
    for (const entry of entries) {
      const request = this.service.toParticipantRequest(entry);
      this.service
        .addParticipant(encounterId, request)
        .subscribe({
          next: () => {
            completed += 1;
            if (--pending === 0) {
              this.finishParticipants();
            }
          },
          error: () => {
            failed += 1;
            if (--pending === 0) {
              this.finishParticipants(failed > 0);
            }
          },
        });
    }
  }

  private finishParticipants(hasFailures = false): void {
    this.loading.set(false);
    // Refresh the stored encounter so the participant list reflects the backend.
    const encounterId = this.encounter()?.id ?? '';
    if (encounterId) {
      this.service
        .fetchEncounter(encounterId)
        .subscribe({
          next: (updated) => {
            this.encounter.set(updated);
            this.participantEntries.clear();
            if (hasFailures) {
              this.participantError.set(
                'Some participants could not be added. Review the highlighted fields and try again.',
              );
            }
          },
          error: () => {
            this.participantError.set(
              'Unable to confirm the added participants. Please refresh and check the list.',
            );
          },
        });
    } else {
      this.participantEntries.clear();
    }
  }

  /** Extracts a safe, user-facing message from a failure payload. */
  private resolveError(err: unknown): string {
    if (err && typeof err === 'object' && 'message' in err) {
      const message = (err as { message?: string }).message;
      if (typeof message === 'string' && message.trim()) {
        return message;
      }
    }
    return 'Unable to create the encounter. Please try again later.';
  }
}
