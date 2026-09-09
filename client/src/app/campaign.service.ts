import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { FormBuilder, Validators } from '@angular/forms';

/**
 * Lifecycle status of a campaign as exposed by the API.
 *
 * Mirrors the server-side {@code com.gamemasterx.server.campaign.model.CampaignStatus}.
 */
export type CampaignStatus =
  | 'DRAFT'
  | 'OPEN'
  | 'ACTIVE'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'ARCHIVED';

/**
 * API-facing representation of a Campaign.
 *
 * Mirrors the server-side {@code com.gamemasterx.server.campaign.model.CampaignDto}.
 */
export interface CampaignResult {
  id: string;
  schemaVersion: number;
  createdAt: string;
  updatedAt: string;
  name: string;
  description: string | null;
  gameSystem: string | null;
  status: CampaignStatus;
  maxPlayers: number;
  adventureId: string | null;
}

/**
 * Request payload used when creating a campaign.
 *
 * Mirrors the server-side {@code com.gamemasterx.server.campaign.model.CampaignCreateRequest}.
 */
export interface CampaignCreation {
  name: string;
  description?: string;
  gameSystem?: string;
  status?: CampaignStatus;
  maxPlayers: number;
}

/**
 * Validation constraints for a new campaign, kept on the client so the reactive
 * form can validate before the request is sent. The server enforces the same
 * constraints through Bean Validation.
 */
export const CAMPAIGN_NAME_MAX = 100;
export const CAMPAIGN_DESCRIPTION_MAX = 5000;
export const CAMPAIGN_SYSTEM_MAX = 100;
export const CAMPAIGN_MIN_PLAYERS = 1;
export const CAMPAIGN_MAX_PLAYERS = 100;

/**
 * Client service for the campaign listing and creation endpoints.
 *
 * <p>The browser screen reads campaigns via {@link listCampaigns}; the
 * create-campaign screen writes one via {@link createCampaign}. Both use the
 * backend API on port 5172.</p>
 */
@Injectable({ providedIn: 'root' })
export class CampaignService {
  private readonly http = inject(HttpClient);
  private readonly formBuilder = inject(FormBuilder);
  private readonly apiBase = 'http://localhost:5172/api';

  /** Lists campaigns visible to the authenticated caller. */
  listCampaigns(status?: CampaignStatus): Observable<CampaignResult[]> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<CampaignResult[]>(`${this.apiBase}/campaigns`, { params });
  }

  /** Creates a new campaign; the caller becomes the campaign OWNER. */
  createCampaign(creation: CampaignCreation): Observable<CampaignResult> {
    return this.http.post<CampaignResult>(`${this.apiBase}/campaigns`, creation);
  }

  /**
   * The reactive form model for creating a campaign. Field validators mirror
   * the server-side Bean Validation constraints on
   * {@code CampaignCreateRequest}.
   */
  createForm() {
    return this.formBuilder.group({
      name: ['', [Validators.required, Validators.maxLength(CAMPAIGN_NAME_MAX)]],
      description: ['', Validators.maxLength(CAMPAIGN_DESCRIPTION_MAX)],
      gameSystem: ['', Validators.maxLength(CAMPAIGN_SYSTEM_MAX)],
      status: ['DRAFT', Validators.required],
      maxPlayers: [
        CAMPAIGN_MIN_PLAYERS,
        [Validators.required, Validators.min(CAMPAIGN_MIN_PLAYERS), Validators.max(CAMPAIGN_MAX_PLAYERS)],
      ],
    });
  }
}
