import { Injectable, signal, inject } from '@angular/core';
import { CampaignEventsService } from './campaign-events.service';

export type AiState = 'enabled' | 'disabled' | 'unavailable' | 'degraded';

@Injectable({ providedIn: 'root' })
export class AiStatusService {
  private eventsService = inject(CampaignEventsService);
  readonly enabled = signal(true);
  readonly available = signal(true);
  readonly degraded = signal(false);

  constructor() {
    // Listen for failure events to mark degraded
    this.eventsService.events$.subscribe(event => {
      if (event.type === 'failure') {
        this.degraded.set(true);
      }
    });
  }

  get state(): AiState {
    if (!this.enabled()) return 'disabled';
    if (!this.available()) return 'unavailable';
    if (this.degraded()) return 'degraded';
    return 'enabled';
  }

  setEnabled(enabled: boolean) {
    this.enabled.set(enabled);
  }

  setAvailable(available: boolean) {
    this.available.set(available);
  }

  clearDegraded() {
    this.degraded.set(false);
  }
}
