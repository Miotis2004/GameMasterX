import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-ai-status',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './ai-status.component.html',
  styleUrl: './ai-status.component.css'
})
export class AiStatusComponent {
  readonly aiEnabled = signal(true);
  readonly aiAvailable = signal(true);

  toggleEnabled() {
    this.aiEnabled.update(v => !v);
  }

  setAvailability(available: boolean) {
    this.aiAvailable.set(available);
  }
}
