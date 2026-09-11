import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EncounterPlayService, Turn } from './encounter-play.service';
import { LoadingComponent } from './shared/components/loading.component';
import { EmptyStateComponent } from './shared/components/empty-state.component';
import { ErrorMessageComponent } from './shared/components/error-message.component';

@Component({
  selector: 'app-turn-history',
  standalone: true,
  imports: [CommonModule, RouterLink, LoadingComponent, EmptyStateComponent, ErrorMessageComponent],
  templateUrl: './turn-history.component.html',
  styleUrl: './turn-history.component.css'
})
export class TurnHistoryComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(EncounterPlayService);

  readonly encounterId = signal<string | null>(null);
  readonly turns = signal<Turn[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.route.params.subscribe(params => {
      const id = params['id'];
      this.encounterId.set(id);
      this.load();
    });
  }

  load(): void {
    const id = this.encounterId();
    if (!id) return;
    this.loading.set(true);
    this.error.set(null);
    this.service.fetchTurns(id).subscribe({
      next: (turns) => {
        this.turns.set(turns ?? []);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set('Failed to load turn history.');
      }
    });
  }

  participantName(id: string | null): string {
    return id ?? '—';
  }
}
