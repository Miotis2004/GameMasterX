import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-loading',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="loading" *ngIf="visible()">
      <div class="spinner"></div>
      <span>{{ label() }}</span>
    </div>
  `,
  styleUrl: './loading.component.css'
})
export class LoadingComponent {
  visible = input<boolean>(true);
  label = input<string>('Loading...');
}
