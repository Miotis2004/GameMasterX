import { Injectable, inject } from '@angular/core';
import { AdventureService } from './adventure.service';
import { AdventureResult, AdventureStatus } from '../../../contracts/adventure';

/**
 * Facade for adventure operations, providing a stable API surface for UI components.
 * Wraps the AdventureService client and surfaces import validation feedback.
 */
@Injectable({ providedIn: 'root' })
export class AdventureFacadeService {
  private readonly service = inject(AdventureService);

  /** List adventures, optionally filtered by status. */
  listAdventures(status?: AdventureStatus | null) {
    return this.service.listAdventuresByStatus(status ?? null);
  }

  /** Get a single adventure by id. */
  getAdventure(id: string) {
    return this.service.getAdventure(id);
  }

  /** Import an adventure package with progress events. */
  importAdventureProgress(file: File, overwrite: boolean) {
    return this.service.importAdventureProgress(file, overwrite);
  }
}
