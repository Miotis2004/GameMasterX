import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AdventureFacadeService } from './adventure-facade.service';
import { AdventureResult, ChapterResult } from '../../../contracts/adventure';

/**
 * The adventure-detail screen displays an imported adventure: its chapters and
 * the scenes within them, plus the referenced content collections (locations,
 * NPCs, creatures, objectives, branches, rewards, secret notes, world facts and
 * tags). The detail is read from the backend API on port 5172 by id.
 */
@Component({
  selector: 'app-adventure-detail',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './adventure-detail.component.html',
  styleUrl: './adventure-detail.component.css',
})
export class AdventureDetailComponent implements OnInit {
  private readonly facade = inject(AdventureFacadeService);
  private readonly route = inject(ActivatedRoute);

  readonly adventure = signal<AdventureResult | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.error.set('No adventure identifier was provided.');
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.adventure.set(null);
    this.facade
      .getAdventure(id)
      .subscribe({
        next: (adventure) => {
          this.adventure.set(adventure);
          this.loading.set(false);
        },
        error: (err: unknown) => {
          const body =
            err && typeof err === 'object' && 'message' in (err as object)
              ? ((err as { message?: unknown }).message as string | undefined)
              : undefined;
          this.error.set(
            body ?? 'Unable to load the adventure. Please try again later.'
          );
          this.loading.set(false);
        },
      });
  }

  /** Total scene count across all chapters, for a quick summary line. */
  sceneCount(adventure: AdventureResult): number {
    return adventure.chapters.reduce((sum, chapter) => sum + (chapter.scenes?.length ?? 0), 0);
  }

  chapterSceneCount(chapter: ChapterResult): number {
    return chapter.scenes?.length ?? 0;
  }

  /** Resolves a scene's referenced location by id, if it exists. */
  locationById(adventure: AdventureResult, locationId: string | null): { name: string } | null {
    if (!locationId) {
      return null;
    }
    const found = adventure.locations.find((location) => location.id === locationId);
    return found ? { name: found.name } : null;
  }

  /** Resolves referenced NPCs by id. */
  npcsById(adventure: AdventureResult, npcIds: string[]): { name: string }[] {
    const result: { name: string }[] = [];
    for (const id of npcIds) {
      const npc = adventure.npcs.find((candidate) => candidate.id === id);
      if (npc) {
        result.push({ name: npc.name });
      }
    }
    return result;
  }

  creaturesById(adventure: AdventureResult, creatureIds: string[]): { name: string }[] {
    const result: { name: string }[] = [];
    for (const id of creatureIds) {
      const creature = adventure.creatures.find((candidate) => candidate.id === id);
      if (creature) {
        result.push({ name: creature.name });
      }
    }
    return result;
  }

  objectiveById(adventure: AdventureResult, objectiveId: string | null): { title: string } | null {
    if (!objectiveId) {
      return null;
    }
    const found = adventure.objectives.find((objective) => objective.id === objectiveId);
    return found ? { title: found.title } : null;
  }
}
