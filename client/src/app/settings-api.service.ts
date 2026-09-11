import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';

export interface CampaignSettings {
  id: string;
  name: string;
  description: string | null;
  gameSystem: string | null;
  maxPlayers: number;
  status: string;
}

export interface AiEndpointInfo {
  endpoint: string;
  model: string;
  provider: string;
  status: string;
  reachable: boolean;
  contextSize?: number;
  maxTokens?: number;
}

@Injectable({ providedIn: 'root' })
export class SettingsApiService {
  private readonly http = inject(HttpClient);
  private readonly apiBase = 'http://localhost:5172/api';
  private readonly healthBase = 'http://localhost:5172';

  getCampaignSettings(campaignId: string): Observable<CampaignSettings> {
    return this.http.get<CampaignSettings>(`${this.apiBase}/campaigns/${campaignId}`);
  }

  updateCampaignSettings(campaignId: string, patch: Partial<CampaignSettings>): Observable<CampaignSettings> {
    return this.http.put<CampaignSettings>(`${this.apiBase}/campaigns/${campaignId}`, patch);
  }

  getAiEndpointInfo(): Observable<AiEndpointInfo> {
    return this.http.get<any>(`${this.healthBase}/health/readiness`).pipe(
      map((resp: any) => {
        const ai = resp?.details?.ai?.provider;
        if (!ai) {
          return {
            endpoint: '',
            model: '',
            provider: '',
            status: 'UNKNOWN',
            reachable: false
          } as AiEndpointInfo;
        }
        return {
          endpoint: '', // Endpoint is not exposed for security
          model: ai.model ?? '',
          provider: ai.provider ?? '',
          status: ai.status ?? 'UNKNOWN',
          reachable: ai.reachable ?? false,
          contextSize: ai.contextSize,
          maxTokens: ai.maxTokens
        } as AiEndpointInfo;
      })
    );
  }
}
