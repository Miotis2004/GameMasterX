import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AdventureService } from './adventure.service';

/**
 * Export screen for adventures. Invokes the existing backend export API,
 * requires confirmation for destructive operations, and renders progress,
 * success and failure states.
 */
@Component({
  selector: 'app-adventure-export',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './adventure-export.component.html',
  styleUrl: './adventure-export.component.css',
})
export class AdventureExportComponent {
  private readonly service = inject(AdventureService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);
  readonly adventures = signal<any[]>([]);

  ngOnInit(): void {
    this.loading.set(true);
    this.service.listAdventures().subscribe({
      next: (list) => {
        this.adventures.set(list);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load adventures');
        this.loading.set(false);
      }
    });
  }

  exportAdventure(id: string): void {
    const confirmed = confirm('Export this adventure? This operation will download a file.');
    if (!confirmed) return;

    this.loading.set(true);
    this.error.set(null);
    this.success.set(null);

    this.service.exportAdventure(id).subscribe({
      next: (blob) => {
        this.loading.set(false);
        this.success.set(`Adventure ${id} exported`);
        // Trigger download
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `adventure-${id}.zip`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Export failed');
      }
    });
  }
}
