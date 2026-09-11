import { Injectable, signal, inject } from '@angular/core';
import {
  CharacterService,
  CharacterSheet,
  CampaignCharacterSummary,
  CharacterSheetCreateRequest,
  CharacterSheetUpdateRequest,
} from './character.service';

/**
 * Reactive facade for character operations, exposing UI state as signals.
 * Wraps CharacterService (the API client) and provides loading/error state
 * for the character list, creation, editing, and sheet views.
 */
@Injectable({ providedIn: 'root' })
export class CharacterFacadeService {
  private readonly service = inject(CharacterService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly characters = signal<CharacterSheet[]>([]);
  readonly character = signal<CharacterSheet | null>(null);

  /** Load all characters owned by the caller. */
  loadOwnedCharacters() {
    this.loading.set(true);
    this.error.set(null);
    this.service.listOwnedCharacters().subscribe({
      next: (characters) => {
        this.characters.set(characters);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        const message =
          err && typeof err === 'object' && 'message' in err
            ? (err as { message?: string }).message
            : 'Unable to load characters.';
        this.error.set(message ?? 'Unable to load characters.');
        this.loading.set(false);
      }
    });
  }

  /** Load a single character sheet by id. */
  loadCharacter(id: string) {
    this.loading.set(true);
    this.error.set(null);
    this.service.getCharacter(id).subscribe({
      next: (character) => {
        this.character.set(character);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        const message =
          err && typeof err === 'object' && 'message' in err
            ? (err as { message?: string }).message
            : 'Unable to load character.';
        this.error.set(message ?? 'Unable to load character.');
        this.loading.set(false);
      }
    });
  }

  /** Create a new character. */
  createCharacter(request: CharacterSheetCreateRequest) {
    this.loading.set(true);
    this.error.set(null);
    return this.service.createCharacter(request);
  }

  /** Update an existing character. */
  updateCharacter(id: string, request: CharacterSheetUpdateRequest) {
    this.loading.set(true);
    this.error.set(null);
    return this.service.updateCharacter(id, request);
  }

  /** List campaign-accessible character summaries. */
  listCampaignCharacters(campaignId: string) {
    return this.service.listCampaignCharacters(campaignId);
  }

  /** Clear error state. */
  clearError() {
    this.error.set(null);
  }
}
