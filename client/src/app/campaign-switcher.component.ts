import { Component, signal, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CampaignService, CampaignResult } from './campaign.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-campaign-switcher',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './campaign-switcher.component.html',
  styleUrl: './campaign-switcher.component.css'
})
export class CampaignSwitcherComponent implements OnInit {
  private campaignService = inject(CampaignService);
  private router = inject(Router);
  
  campaigns = signal<CampaignResult[]>([]);
  selectedId = signal<string | null>(null);
  loading = signal(false);
  error = signal<string | null>(null);

  ngOnInit() {
    this.loading.set(true);
    this.campaignService.listCampaigns().subscribe({
      next: (campaigns) => {
        this.campaigns.set(campaigns);
        this.loading.set(false);
        if (campaigns.length > 0) {
          this.selectedId.set(campaigns[0].id);
        }
      },
      error: () => {
        this.error.set('Unable to load campaigns');
        this.loading.set(false);
      }
    });
  }

  onSelect(id: string) {
    this.selectedId.set(id);
    this.router.navigate(['/campaigns', id, 'dashboard']);
  }
}
