import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SettingsFacadeService } from './settings-facade.service';

@Component({
  selector: 'app-ai-endpoint-settings',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './ai-endpoint-settings.component.html',
  styleUrl: './ai-endpoint-settings.component.css'
})
export class AiEndpointSettingsComponent implements OnInit {
  private facade = inject(SettingsFacadeService);

  loading = this.facade.loading;
  error = this.facade.error;
  success = this.facade.success;

  ngOnInit() {
    this.facade.loadAiEndpointInfo();
  }

  get aiInfo() {
    return this.facade.aiInfo();
  }
}
