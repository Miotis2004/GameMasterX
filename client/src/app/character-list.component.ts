import { Component, EventEmitter, Input, OnInit, Output, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin, of, Observable } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { CharacterService, CharacterSheet, CampaignCharacterSummary } from './character.service';

/**
 * A row in the character list. Owned and campaign-accessible characters are
 * rendered uniformly; {@link CharacterRow.isOwner} distinguishes the two.
 */
export interface CharacterRow {
  id: string;
  name: string;
  campaignId: string | null;
  ownerId: string | null;
  level: number;
  gameSystem: string | null;
  isOwner: boolean;
}

/**
 * The character list shows the characters a caller can see: every character it
 * owns plus every character it can access through a campaign it belongs to.
 *
 * <p>Owned characters are loaded from {@link CharacterService.listOwnedCharacters}
 * (full {@link CharacterSheet}). When {@link campaignIds} is provided, the
 * characters of those campaigns are fetched from each campaign's dashboard as
 * {@link CampaignCharacterSummary} projections and rendered alongside the owned
 * characters. Characters owned by the caller are de-duplicated by id across both
 * sources.</p>
 */
@Component({
  selector: 'app-character-list',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './character-list.component.html',
  styleUrl: './character-list.component.css',
})
export class CharacterListComponent implements OnInit {
  private readonly service = inject(CharacterService);

  /** Optional campaigns whose characters should be included as campaign-accessible. */
  @Input() campaignIds: string[] = [];

  /** Fired when a campaign-accessible character is removed so the parent can
   * drop it from the set it is tracking. */
  @Output() campaignCharacterRemoved = new EventEmitter<string>();

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly rows = signal<CharacterRow[]>([]);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);

    const campaignIds = this.campaignIds;

    this.service.listOwnedCharacters().subscribe({
      next: (owned) => {
        // Resolve campaign-accessible characters next. Failures must not hide
        // the owned characters, so per-campaign failures are swallowed.
        this.loadCampaignSummaries(campaignIds).subscribe((summaries) => {
          this.rows.set(this.buildRows(owned, summaries));
          this.loading.set(false);
        });
      },
      error: (err: unknown) => {
        const message =
          err && typeof err === 'object' && 'message' in err
            ? (err as { message?: string }).message
            : 'Unable to load characters. Please try again later.';
        this.error.set(message ?? null);
        this.loading.set(false);
      },
    });
  }

  /**
   * Fetches the campaign-accessible character summaries for every provided
   * campaign id. Failures for an individual campaign are swallowed into an
   * empty list so the owned characters are never hidden, and the summaries are
   * de-duplicated by id before they are merged with the owned characters.
   */
  private loadCampaignSummaries(campaignIds: string[]): Observable<CampaignCharacterSummary[]> {
    if (!campaignIds.length) {
      return of([]);
    }
    return forkJoin(
      campaignIds.map((campaignId) =>
        this.service
          .listCampaignCharacters(campaignId)
          .pipe(catchError(() => of<CampaignCharacterSummary[]>([])))
      )
    ).pipe(
      map((groups) => {
        // De-duplicate by id across campaigns.
        const byId = new Map<string, CampaignCharacterSummary>();
        for (const summary of groups.flat()) {
          byId.set(summary.id, summary);
        }
        return [...byId.values()];
      })
    );
  }

  /**
   * Builds the deduplicated row list from the owned characters plus the
   * campaign-accessible summaries.
   */
  private buildRows(owned: CharacterSheet[], campaignSummaries: CampaignCharacterSummary[]): CharacterRow[] {
    const byId = new Map<string, CharacterRow>();
    for (const character of owned) {
      byId.set(character.id, {
        id: character.id,
        name: character.name,
        campaignId: character.campaignId,
        ownerId: character.ownerId,
        level: character.level,
        gameSystem: character.gameSystem,
        isOwner: true,
      });
    }
    for (const summary of campaignSummaries) {
      if (!byId.has(summary.id)) {
        byId.set(summary.id, {
          id: summary.id,
          name: summary.name,
          campaignId: summary.campaignId,
          ownerId: summary.ownerId,
          level: 1,
          gameSystem: null,
          isOwner: summary.ownerId !== null && summary.ownerId !== undefined,
        });
      }
    }
    return [...byId.values()];
  }

  removeCampaignCharacter(id: string): void {
    this.rows.set(this.rows().filter((row) => row.id !== id));
    this.campaignCharacterRemoved.emit(id);
  }
}
