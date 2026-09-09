import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { CampaignService, CampaignResult, CampaignCreation } from './campaign.service';

/**
 * The create-campaign screen submits a valid campaign through
 * {@link CampaignService.createCampaign}. It uses a reactive form whose
 * validators mirror the server-side constraints, and it reports loading,
 * validation, and failure states.
 */
@Component({
  selector: 'app-create-campaign',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './create-campaign.component.html',
  styleUrl: './create-campaign.component.css',
})
export class CreateCampaignComponent implements OnInit {
  private readonly service = inject(CampaignService);
  private readonly router = inject(Router);

  readonly form = this.service.createForm();
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly created = signal<CampaignResult | null>(null);

  ngOnInit(): void {
    // Seed the initial validation state without requiring user interaction.
    this.form.updateValueAndValidity();
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    const value = this.form.getRawValue() as CampaignCreation;
    this.service.createCampaign(value).subscribe({
      next: (campaign) => {
        this.created.set(campaign);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        const message =
          err && typeof err === 'object' && 'message' in err
            ? (err as { message?: string }).message
            : 'Unable to create campaign. Please try again later.';
        this.error.set(message ?? 'Unable to create campaign. Please try again later.');
      },
    });
  }
}