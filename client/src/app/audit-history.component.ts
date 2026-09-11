import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EncounterPlayService, AuditEntry } from './encounter-play.service';
import { LoadingComponent } from './shared/components/loading.component';
import { EmptyStateComponent } from './shared/components/empty-state.component';
import { ErrorMessageComponent } from './shared/components/error-message.component';

@Component({
  selector: 'app-audit-history',
  standalone: true,
  imports: [CommonModule, RouterLink, LoadingComponent, EmptyStateComponent, ErrorMessageComponent],
  templateUrl: './audit-history.component.html',
  styleUrl: './audit-history.component.css'
})
export class AuditHistoryComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(EncounterPlayService);

  readonly encounterId = signal<string | null>(null);
  readonly audits = signal<AuditEntry[]>([]);
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
    this.service.fetchAudit(id).subscribe({
      next: (audits) => {
        this.audits.set(audits ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Failed to load audit history.');
      }
    });
  }
}
