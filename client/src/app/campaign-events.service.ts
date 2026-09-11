import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

export interface CampaignEvent {
  type: string;
  campaignId: string;
  sequenceNumber: number;
  timestamp: string;
  payload?: any;
}

@Injectable({ providedIn: 'root' })
export class CampaignEventsService {
  private socket: WebSocket | null = null;
  private reconnectAttempts = 0;
  private maxBackoff = 30000;
  private baseBackoff = 1000;
  private lastSequenceNumber = 0;
  private currentCampaignId: string | null = null;
  private eventSubject = new Subject<CampaignEvent>();
  private stateSubject = new Subject<{ campaignId: string; sequenceNumber: number }>();

  events$ = this.eventSubject.asObservable();
  state$ = this.stateSubject.asObservable();

  connect(campaignId: string) {
    if (this.currentCampaignId !== campaignId) {
      this.currentCampaignId = campaignId;
      this.lastSequenceNumber = 0;
      this.reconnectAttempts = 0;
    }
    this.openSocket();
  }

  disconnect() {
    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }
    this.reconnectAttempts = 0;
  }

  private openSocket() {
    if (!this.currentCampaignId) return;
    const url = `ws://localhost:5172/ws/campaign/${this.currentCampaignId}`;
    try {
      this.socket = new WebSocket(url);
      this.socket.onopen = () => {
        this.reconnectAttempts = 0;
        // State refresh is handled by server sending state message on connect
      };
      this.socket.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          if (data.type === 'state') {
            this.stateSubject.next({ campaignId: data.campaignId, sequenceNumber: data.sequenceNumber });
            // Reset duplicate suppression baseline to server's last sequence
            this.lastSequenceNumber = data.sequenceNumber;
            return;
          }
          if (typeof data.sequenceNumber === 'number') {
            if (data.sequenceNumber <= this.lastSequenceNumber) {
              // Duplicate or out of order, ignore
              return;
            }
            this.lastSequenceNumber = data.sequenceNumber;
            this.eventSubject.next(data as CampaignEvent);
          }
        } catch (e) {
          console.error('Failed to parse WebSocket message', e);
        }
      };
      this.socket.onerror = () => {
        // Error will trigger onclose
      };
      this.socket.onclose = () => {
        this.socket = null;
        this.scheduleReconnect();
      };
    } catch (e) {
      this.scheduleReconnect();
    }
  }

  private scheduleReconnect() {
    const delay = Math.min(this.baseBackoff * Math.pow(2, this.reconnectAttempts), this.maxBackoff);
    this.reconnectAttempts++;
    setTimeout(() => this.openSocket(), delay);
  }
}
