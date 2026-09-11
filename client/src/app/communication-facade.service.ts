import { Injectable, signal, inject } from '@angular/core';
import { CampaignEventsService } from './campaign-events.service';
import { HttpClient } from '@angular/common/http';
import { StatusService } from './status.service';
import { map } from 'rxjs/operators';

interface ChatMessage {
  id: string;
  author: string;
  role: string;
  text: string;
  timestamp: string;
  type: 'chat' | 'whisper' | 'gm_note' | 'system';
}

@Injectable({ providedIn: 'root' })
export class CommunicationFacadeService {
  private eventsService = inject(CampaignEventsService);
  private http = inject(HttpClient);
  private statusService = inject(StatusService);

  readonly campaignId = signal<string | null>(null);
  readonly role = signal<string>('PLAYER');
  readonly connectionStatus = signal<'connected' | 'reconnecting' | 'disconnected'>('disconnected');
  readonly chatMessages = signal<ChatMessage[]>([]);
  readonly whispers = signal<ChatMessage[]>([]);
  readonly gmNotes = signal<ChatMessage[]>([]);
  readonly systemEvents = signal<ChatMessage[]>([]);

  private readonly apiBase = 'http://localhost:5172/api';

  setCampaign(id: string, role: string = 'PLAYER') {
    this.campaignId.set(id);
    this.role.set(role);
    this.eventsService.connect(id);
    this.subscribeToEvents();
    this.subscribeToConnectionStatus();
  }

  disconnect() {
    this.eventsService.disconnect();
    this.connectionStatus.set('disconnected');
  }

  private subscribeToConnectionStatus() {
    this.eventsService.connectionStatus$.subscribe(status => {
      this.connectionStatus.set(status);
      if (status === 'disconnected') {
        this.statusService.setDisconnected(true);
      } else {
        this.statusService.setDisconnected(false);
      }
    });
  }

  private subscribeToEvents() {
    this.eventsService.events$.subscribe(event => {
      const payload = event.payload ?? {};
      const type = event.type;
      const baseMessage: ChatMessage = {
        id: `${event.sequenceNumber}`,
        author: payload.author ?? 'System',
        role: payload.role ?? 'SYSTEM',
        text: payload.text ?? '',
        timestamp: event.timestamp,
        type: 'chat'
      };

      switch (type) {
        case 'chat_message':
          this.chatMessages.update(msgs => [...msgs, baseMessage]);
          break;
        case 'whisper':
          this.whispers.update(msgs => [...msgs, baseMessage]);
          break;
        case 'gm_note':
          if (this.isGmVisible()) {
            this.gmNotes.update(msgs => [...msgs, baseMessage]);
          }
          break;
        case 'system_event':
          this.systemEvents.update(msgs => [...msgs, baseMessage]);
          break;
        default:
          // ignore unknown
          break;
      }
    });

    this.eventsService.state$.subscribe(() => {
      // State refresh: reset sequences, optionally clear history
      // For simplicity, keep existing messages
    });
  }

  private isGmVisible(): boolean {
    const r = this.role();
    return r === 'GAME_MASTER' || r === 'OWNER';
  }

  // Reactions submitted via backend API
  addReaction(campaignId: string, messageId: string, reaction: string) {
    return this.http.post(`${this.apiBase}/campaigns/${campaignId}/messages/${messageId}/reactions`, { reaction });
  }

  // Polls via backend APIs
  createPoll(campaignId: string, request: { question: string; options: string[] }) {
    return this.http.post(`${this.apiBase}/campaigns/${campaignId}/polls`, request);
  }

  votePoll(campaignId: string, pollId: string, optionIndex: number) {
    return this.http.post(`${this.apiBase}/campaigns/${campaignId}/polls/${pollId}/vote`, { optionIndex });
  }

  closePoll(campaignId: string, pollId: string) {
    return this.http.post(`${this.apiBase}/campaigns/${campaignId}/polls/${pollId}/close`, {});
  }
}
