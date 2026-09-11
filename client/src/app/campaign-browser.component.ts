import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CampaignService, CampaignResult, CampaignStatus } from './campaign.service';
import { MembershipService } from './membership.service';
import { forkJoin } from 'rxjs';

interface CampaignWithRole extends CampaignResult {
  role?: string;
}

/**
 * The campaign browser lists the campaigns available to the authenticated
 * caller. It exposes a status filter and links to the join and create flows.
 */
@Component({
  selector: 'app-campaign-browser',
  standalone: true,
  imports: [CommonModule, RouterLink, ReactiveFormsModule],
  templateUrl: './campaign-browser.component.html',
  styleUrl: './campaign-browser.component.css',
})
export class CampaignBrowserComponent implements OnInit {
  private readonly service = inject(CampaignService);
  private readonly membershipService = inject(MembershipService);
  private readonly formBuilder = inject(FormBuilder);

  readonly campaigns = signal<CampaignWithRole[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly filterForm = this.formBuilder.group({
    status: ['ALL', Validators.required],
  });

  readonly statusOptions: { value: 'ALL' | CampaignStatus; label: string }[] = [
    { value: 'ALL', label: 'All' },
    { value: 'DRAFT', label: 'Draft' },
    { value: 'OPEN', label: 'Open' },
    { value: 'ACTIVE', label: 'Active' },
    { value: 'COMPLETED', label: 'Completed' },
    { value: 'CANCELLED', label: 'Cancelled' },
    { value: 'ARCHIVED', label: 'Archived' },
  ];

  ngOnInit(): void {
    this.load();
    this.filterForm.get('status')?.valueChanges.subscribe(() => this.load());
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    const status = (this.filterForm.get('status')?.value ?? 'ALL') as 'ALL' | CampaignStatus;
    this.service
      .listCampaigns(status === 'ALL' ? undefined : status)
      .subscribe({
        next: (campaigns) => {
          const roleRequests = campaigns.map(c =>
            this.membershipService.getCampaignDashboard(c.id)
          );
          if (roleRequests.length === 0) {
            this.campaigns.set([]);
            this.loading.set(false);
            return;
          }
          forkJoin(roleRequests).subscribe({
            next: (dashboards) => {
              const enriched = campaigns.map((c, idx) => {
                const dash = dashboards[idx];
                return { ...c, role: dash?.actorRole ?? 'UNKNOWN' } as CampaignWithRole;
              });
              this.campaigns.set(enriched);
              this.loading.set(false);
            },
            error: () => {
              this.campaigns.set(campaigns.map(c => ({ ...c } as CampaignWithRole)));
              this.loading.set(false);
            }
          });
        },
        error: (err: unknown) => {
          const message =
            err && typeof err === 'object' && 'message' in err
              ? (err as { message?: string }).message
              : 'Unable to load campaigns. Please try again later.';
          this.error.set(message ?? 'Unable to load campaigns. Please try again later.');
          this.loading.set(false);
        },
      });
  }

  archive(id: string): void {
    this.service.archiveCampaign(id).subscribe({
      next: () => this.load(),
      error: (err: unknown) => {
        const message =
          err && typeof err === 'object' && 'message' in err
            ? (err as { message?: string }).message
            : 'Unable to archive campaign.';
        this.error.set(message ?? 'Unable to archive campaign.');
      }
    });
  }
}
