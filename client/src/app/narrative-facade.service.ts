import { Injectable, signal, computed, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CampaignEventsService } from './campaign-events.service';
import { Subject, fromEvent, Observable, of } from 'rxjs';
import { takeUntil, filter, map, distinctUntilChanged } from 'rxjs/operators';

interface NarrativeRecord {
  id: string;
  campaignId: string;
  turnId?: string;
  messages: Array<{ type: string; text: string; actor: string; timestamp: string }>;
}

@Injectable({ providedIn: 'root' })
export class NarrativeFacadeService {
  private http = inject(HttpClient);
  private eventsService = inject(CampaignEventsService);
  private destroy$ = new Subject<void>();

  readonly campaignId = signal<string | null>(null);
  readonly streaming = signal(false);
  readonly completedNarrative = signal<string>('');
  readonly streamingText = signal<string>('');
  readonly history = signal<NarrativeRecord[]>([]);

  private lastSequenceNumber = 0;
  private accumulatedDelta = '';

  readonly narrativeHistory = computed(() => {
    const completed = this.completedNarrative();
    const streaming = this.streamingText();
    return completed + streaming;
  });

  setCampaign(id: string) {
    this.campaignId.set(id);
    this.eventsService.connect(id);
    this.loadHistory(id);
  }

  private loadHistory(campaignId: string) {
    // Placeholder: fetch durable narrative records if endpoint exists
    // For now, clear history
    this.history.set([]);
    this.completedNarrative.set('');
  }

  startStream(campaignId: string, params: { encounterId?: string; instruction?: string }) {
    this.streaming.set(true);
    this.accumulatedDelta = '';
    this.streamingText.set('');
    this.lastSequenceNumber = 0;

    // Subscribe to events with RxJS cancellation
    this.eventsService.events$
      .pipe(
        takeUntil(this.destroy$),
        filter(event => event.type === 'narrative_delta'),
        filter(event => {
          // Duplicate suppression
          if (event.sequenceNumber <= this.lastSequenceNumber) {
            return false;
          }
          this.lastSequenceNumber = event.sequenceNumber;
          return true;
        })
      )
      .subscribe({
        next: (event: any) => {
          const delta = event.payload?.delta;
          if (delta) {
            this.accumulatedDelta += delta;
            this.streamingText.set(this.accumulatedDelta);
          }
        }
      });

    // In a real implementation, trigger SSE stream via HTTP POST to /api/narration/stream
    // For now, we rely on WebSocket events
  }

  cancelStream() {
    this.streaming.set(false);
    this.destroy$.next();
  }

  stopStream() {
    this.streaming.set(false);
    this.completedNarrative.set(this.completedNarrative() + this.streamingText());
    this.streamingText.set('');
    this.accumulatedDelta = '';
  }

  submitAction(action: string) {
    const campaignId = this.campaignId();
    if (!campaignId) {
      throw new Error('No campaign selected');
    }
    return this.http.post(`http://localhost:5172/api/campaigns/${campaignId}/actions`, { action }, { withCredentials: true });
  }
}
