import { Component, inject, signal, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { SettingsFacadeService } from './settings-facade.service';

@Component({
  selector: 'app-campaign-settings',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './campaign-settings.component.html',
  styleUrl: './campaign-settings.component.css'
})
export class CampaignSettingsComponent {
  private facade = inject(SettingsFacadeService);
  private fb = inject(FormBuilder);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  campaignId = signal<string | null>(null);
  loading = this.facade.loading;
  error = this.facade.error;
  success = this.facade.success;

  form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    description: ['', Validators.maxLength(5000)],
    gameSystem: ['', Validators.maxLength(100)],
    maxPlayers: [1, [Validators.required, Validators.min(1), Validators.max(100)]]
  });

  constructor() {
    const id = this.route.snapshot.paramMap.get('campaignId');
    if (id) {
      this.campaignId.set(id);
      this.facade.loadCampaignSettings(id);
    }

    effect(() => {
      const settings = this.facade.campaignSettings();
      if (settings) {
        this.form.patchValue({
          name: settings.name,
          description: settings.description ?? '',
          gameSystem: settings.gameSystem ?? '',
          maxPlayers: settings.maxPlayers
        });
      }
    });
  }

  save() {
    const id = this.campaignId();
    if (!id) return;
    if (this.form.invalid) {
      this.facade.error.set('Please correct validation errors');
      return;
    }
    const value = this.form.value;
    this.facade.updateCampaignSettings(id, {
      name: value.name ?? '',
      description: value.description ?? null,
      gameSystem: value.gameSystem ?? null,
      maxPlayers: value.maxPlayers ?? 1
    });
  }

  cancel() {
    this.router.navigate(['/campaigns']);
  }
}
