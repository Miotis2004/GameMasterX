import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AdventureService } from './adventure.service';

/**
 * Backup screen for adventures. Invokes the existing backend backup API,
 * requires confirmation for destructive operations, and renders progress,
 * success and failure states.
 */
@Component({
  selector: 'app-adventure-backup',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './adventure-backup.component.html',
  styleUrl: './adventure-backup.component.css',
})
export class AdventureBackupComponent {
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

  backup(id: string): void {
    const confirmed = confirm('Create a backup of this adventure? This operation is safe and will not modify data.');
    if (!confirmed) return;

    this.loading.set(true);
    this.error.set(null);
    this.success.set(null);

    this.service.backupAdventure(id).subscribe({
      next: () => {
        this.loading.set(false);
        this.success.set(`Backup created for adventure ${id}`);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Backup failed');
      }
    });
  }
}
