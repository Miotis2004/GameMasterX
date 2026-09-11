import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { EncounterService } from './encounter.service';
import { EncounterPlayService } from './encounter-play.service';
import { Encounter } from '@contracts/encounter';

/**
 * Reactive facade for encounter operations.
 *
 * <p>Wraps the encounter API client ({@link EncounterService} and
 * {@link EncounterPlayService}) and exposes reactive state via RxJS
 * BehaviorSubjects. UI components subscribe to the subjects for loading,
 * encounter data, and error states, keeping state centralized and
 * observable-driven.</p>
 */
@Injectable({ providedIn: 'root' })
export class EncounterFacadeService {
  private readonly encounterService = inject(EncounterService);
  private readonly playService = inject(EncounterPlayService);

  private readonly _encounter$ = new BehaviorSubject<Encounter | null>(null);
  private readonly _loading$ = new BehaviorSubject<boolean>(false);
  private readonly _error$ = new BehaviorSubject<string | null>(null);

  /** Observable stream of the current encounter. */
  readonly encounter$ = this._encounter$.asObservable();
  /** Observable stream of loading state. */
  readonly loading$ = this._loading$.asObservable();
  /** Observable stream of error state. */
  readonly error$ = this._error$.asObservable();

  // ----- Setup / creation -----

  /** Create a new draft encounter and publish it to the state stream. */
  createEncounter(request: any) {
    this._loading$.next(true);
    this._error$.next(null);
    return this.encounterService.createEncounter(request).subscribe({
      next: (encounter) => {
        this._encounter$.next(encounter);
        this._loading$.next(false);
      },
      error: (err) => {
        this._loading$.next(false);
        this._error$.next(this.resolveError(err));
      }
    });
  }

  /** Fetch an encounter by id and publish to state. */
  loadEncounter(id: string) {
    this._loading$.next(true);
    this._error$.next(null);
    return this.playService.fetchEncounter(id).subscribe({
      next: (encounter) => {
        this._encounter$.next(encounter);
        this._loading$.next(false);
      },
      error: (err) => {
        this._loading$.next(false);
        this._error$.next(this.resolveError(err));
      }
    });
  }

  // ----- Lifecycle -----

  startEncounter(id: string, rollMode?: string, seed?: string) {
    return this.playService.startEncounter(id, rollMode as any, seed);
  }

  generateInitiative(id: string, rollMode?: string, seed?: string) {
    return this.playService.generateInitiative(id, rollMode as any, seed);
  }

  pauseEncounter(id: string) {
    return this.playService.pauseEncounter(id);
  }

  resumeEncounter(id: string) {
    return this.playService.resumeEncounter(id);
  }

  completeEncounter(id: string) {
    return this.playService.completeEncounter(id);
  }

  advanceTurn(id: string) {
    return this.playService.advanceTurn(id);
  }

  // ----- Participant mutations -----

  addParticipant(encounterId: string, participant: any) {
    return this.encounterService.addParticipant(encounterId, participant);
  }

  // ----- Helpers -----

  private resolveError(err: unknown): string {
    if (err && typeof err === 'object' && 'message' in err) {
      const message = (err as { message?: string }).message;
      if (typeof message === 'string' && message.trim()) {
        return message;
      }
    }
    return 'Request failed. Please try again later.';
  }

  /** Clear error state. */
  clearError() {
    this._error$.next(null);
  }
}
