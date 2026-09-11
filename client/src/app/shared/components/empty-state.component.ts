import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="empty-state" *ngIf="visible()">
      <div class="empty-icon">📭</div>
      <h3>{{ title() }}</h3>
      <p>{{ description() }}</p>
      <ng-content></ng-content>
    </div>
  `,
  styleUrl: './empty-state.component.css'
})
export class EmptyStateComponent {
  visible = input<boolean>(true);
  title = input<string>('No items');
  description = input<string>('There is nothing to display yet.');
}
