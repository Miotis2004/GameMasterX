import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CharacterService, CharacterSheet, ABILITY_NAMES, AbilityName } from './character.service';

/**
 * The character sheet displays the full detail of a single character the caller
 * can access, loaded through {@link CharacterService.getCharacter}.
 */
@Component({
  selector: 'app-character-sheet',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './character-sheet.component.html',
  styleUrl: './character-sheet.component.css',
})
export class CharacterSheetComponent implements OnInit {
  private readonly service = inject(CharacterService);
  private readonly route = inject(ActivatedRoute);

  readonly abilityLabels: Record<AbilityName, string> = {
    strength: 'Strength',
    dexterity: 'Dexterity',
    constitution: 'Constitution',
    intelligence: 'Intelligence',
    wisdom: 'Wisdom',
    charisma: 'Charisma',
  };

  readonly character = signal<CharacterSheet | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  /** Pairs each ability label with its key for rendering. */
  abilityEntries(character: CharacterSheet): { label: string; key: AbilityName }[] {
    return ABILITY_NAMES.map((key) => ({ label: this.abilityLabels[key], key }));
  }

  load(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.error.set('No character id was provided.');
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.service.getCharacter(id).subscribe({
      next: (character) => {
        this.character.set(character);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        const message =
          err && typeof err === 'object' && 'message' in err
            ? (err as { message?: string }).message
            : 'Unable to load the character. Please try again later.';
        this.error.set(message ?? null);
        this.loading.set(false);
      },
    });
  }
}
