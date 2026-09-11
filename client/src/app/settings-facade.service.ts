import { Injectable, inject, signal } from '@angular/core';
import { SettingsApiService, CampaignSettings, AiEndpointInfo } from './settings-api.service';

@Injectable({ providedIn: 'root' })
export class SettingsFacadeService {
  private readonly api = inject(SettingsApiService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);

  private campaignSettingsSignal = signal<CampaignSettings | null>(null);
  private aiInfoSignal = signal<AiEndpointInfo | null>(null);

  get campaignSettings() {
    return this.campaignSettingsSignal.asReadonly();
  }

  get aiInfo() {
    return this.aiInfoSignal.asReadonly();
  }

  loadCampaignSettings(campaignId: string) {
    this.loading.set(true);
    this.error.set(null);
    this.api.getCampaignSettings(campaignId).subscribe({
      next: (data) => {
        this.campaignSettingsSignal.set(data);
        this.loading.set(false);
        this.success.set('Campaign settings loaded');
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.message ?? 'Failed to load campaign settings');
      }
    });
  }

  updateCampaignSettings(campaignId: string, patch: Partial<CampaignSettings>) {
    this.loading.set(true);
    this.error.set(null);
    this.api.updateCampaignSettings(campaignId, patch).subscribe({
      next: (data) => {
        this.campaignSettingsSignal.set(data);
        this.loading.set(false);
        this.success.set('Campaign settings updated');
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.message ?? 'Failed to update campaign settings');
      }
    });
  }

  loadAiEndpointInfo() {
    this.loading.set(true);
    this.error.set(null);
    this.api.getAiEndpointInfo().subscribe({
      next: (info) => {
        this.aiInfoSignal.set(info);
        this.loading.set(false);
        this.success.set('AI endpoint info loaded');
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.message ?? 'Failed to load AI endpoint info');
      }
    });
  }

  clearMessages() {
    this.error.set(null);
    this.success.set(null);
  }
}
