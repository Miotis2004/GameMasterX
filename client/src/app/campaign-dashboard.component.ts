import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { MembershipService } from './membership.service';

/**
 * Campaign dashboard overview showing members, characters, and selected campaign.
 * Loads data via the campaign dashboard endpoint.
 */
@Component({
  selector: 'app-campaign-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './campaign-dashboard.component.html',
  styleUrl: './campaign-dashboard.component.css',
})
export class CampaignDashboardComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly membershipService = inject(MembershipService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly dashboard = signal<any>(null);
  private campaignId = signal<string | null>(null);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('campaignId');
    if (!id) {
      this.error.set('Campaign ID missing');
      return;
    }
    this.campaignId.set(id);
    this.load();
  }

  load() {
    const id = this.campaignId();
    if (!id) return;
    this.loading.set(true);
    this.error.set(null);
    this.membershipService.getCampaignDashboard(id).subscribe({
      next: (data) => {
        this.dashboard.set(data);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        const message =
          err && typeof err === 'object' && 'message' in err
            ? (err as { message?: string }).message
            : 'Unable to load dashboard.';
        this.error.set(message ?? 'Unable to load dashboard.');
        this.loading.set(false);
      }
    });
  }
}
