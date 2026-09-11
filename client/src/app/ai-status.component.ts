import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AiStatusService } from './ai-status.service';

@Component({
  selector: 'app-ai-status',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './ai-status.component.html',
  styleUrl: './ai-status.component.css'
})
export class AiStatusComponent {
  private statusService = inject(AiStatusService);

  get state() {
    return this.statusService.state;
  }

  toggleEnabled() {
    this.statusService.setEnabled(!this.statusService.enabled());
  }

  toggleAvailability() {
    this.statusService.setAvailable(!this.statusService.available());
  }

  clearDegraded() {
    this.statusService.clearDegraded();
  }
}
