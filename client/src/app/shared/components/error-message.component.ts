import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-error-message',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="error-message" *ngIf="message()">
      <div class="error-icon">⚠️</div>
      <div class="error-content">
        <strong>Error</strong>
        <p>{{ message() }}</p>
        <ng-content></ng-content>
      </div>
    </div>
  `,
  styleUrl: './error-message.component.css'
})
export class ErrorMessageComponent {
  message = input<string>('');
}
