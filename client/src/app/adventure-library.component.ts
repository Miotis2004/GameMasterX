import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AdventureFacadeService } from './adventure-facade.service';
import { AdventureResult, AdventureStatus } from '../../../contracts/adventure';

/**
 * The adventure library lists the adventures that have been imported into the
 * platform. It exposes a status filter and links to the adventure-detail and
 * import flows.
 */
@Component({
  selector: 'app-adventure-library',
  standalone: true,
  imports: [CommonModule, RouterLink, ReactiveFormsModule],
  templateUrl: './adventure-library.component.html',
  styleUrl: './adventure-library.component.css',
})
export class AdventureLibraryComponent implements OnInit {
  private readonly facade = inject(AdventureFacadeService);
  private readonly formBuilder = inject(FormBuilder);

  readonly adventures = signal<AdventureResult[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly filterForm = this.formBuilder.group({
    status: ['ALL', Validators.required],
  });

  readonly statusOptions: { value: 'ALL' | AdventureStatus; label: string }[] = [
    { value: 'ALL', label: 'All' },
    { value: 'DRAFT', label: 'Draft' },
    { value: 'PLAYTEST', label: 'Playtest' },
    { value: 'PUBLISHED', label: 'Published' },
    { value: 'ARCHIVED', label: 'Archived' },
  ];

  ngOnInit(): void {
    this.load();
    this.filterForm.get('status')?.valueChanges.subscribe(() => this.load());
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    const status = (this.filterForm.get('status')?.value ?? 'ALL') as 'ALL' | AdventureStatus;
    this.facade
      .listAdventures(status === 'ALL' ? null : status)
      .subscribe({
        next: (adventures) => {
          this.adventures.set(adventures);
          this.loading.set(false);
          this.error.set(null);
        },
        error: (err: unknown) => {
          const body =
            err && typeof err === 'object' && 'message' in (err as object)
              ? ((err as { message?: unknown }).message as string | undefined)
              : undefined;
          this.error.set(
            body ?? 'Unable to load adventures. Please try again later.'
          );
          this.loading.set(false);
        },
      });
  }
}
