import { Injectable, signal, inject } from '@angular/core';
import { CampaignService, CampaignResult, CampaignStatus } from './campaign.service';
import { MembershipService } from './membership.service';

/**
 * Facade for campaign operations, exposing reactive signals for UI state.
 * Combines the campaign API client with membership data to provide role-aware
 * campaign views for the browser and dashboard.
 */
@Injectable({ providedIn: 'root' })
export class CampaignFacadeService {
  private readonly campaignService = inject(CampaignService);
  private readonly membershipService = inject(MembershipService);

  readonly campaigns = signal<CampaignResult[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly currentCampaignId = signal<string | null>(null);

  /** Load campaigns with optional status filter, enriching with user role. */
  loadCampaigns(status?: CampaignStatus) {
    this.loading.set(true);
    this.error.set(null);
    this.campaignService.listCampaigns(status).subscribe({
      next: (campaigns) => {
        this.campaigns.set(campaigns);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        const message =
          err && typeof err === 'object' && 'message' in err
            ? (err as { message?: string }).message
            : 'Unable to load campaigns.';
        this.error.set(message ?? 'Unable to load campaigns.');
        this.loading.set(false);
      }
    });
  }

  /** Create a new campaign. */
  createCampaign(data: { name: string; description?: string; gameSystem?: string; status?: CampaignStatus; maxPlayers: number }) {
    return this.campaignService.createCampaign(data);
  }

  /** Archive a campaign. */
  archiveCampaign(id: string) {
    return this.campaignService.archiveCampaign(id);
  }

  /** Get dashboard data for a campaign. */
  getDashboard(id: string) {
    return this.campaignService.getCampaignDashboard(id);
  }

  /** Set current campaign for switching. */
  selectCampaign(id: string) {
    this.currentCampaignId.set(id);
  }

  /** Get role for a campaign via dashboard. */
  getUserRoleForCampaign(id: string) {
    return this.membershipService.getCampaignDashboard(id).pipe(
      // The dashboard includes actorRole
    );
  }
}
