import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  CharacterService,
  CharacterSheetUpdateRequest,
  ABILITY_NAMES,
  AbilityName,
  abilityModifier,
} from './character.service';

/**
 * The character edit screen loads an existing character, lets the caller edit
 * its fields through a reactive form whose validators mirror the server-side
 * constraints, and submits a partial update through
 * {@link CharacterService.updateCharacter}.
 */
@Component({
  selector: 'app-edit-character',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './edit-character.component.html',
  styleUrl: './edit-character.component.css',
})
export class EditCharacterComponent implements OnInit {
  private readonly service = inject(CharacterService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly form = signal<ReturnType<CharacterService['editForm']>['form'] | null>(null);
  readonly characterId = signal<string | null>(null);
  readonly abilityScores = signal<ReturnType<CharacterService['editForm']>['abilityScores'] | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly saved = signal(false);

  readonly abilityNames = ABILITY_NAMES;
  readonly abilityLabels: Record<AbilityName, string> = {
    strength: 'Strength',
    dexterity: 'Dexterity',
    constitution: 'Constitution',
    intelligence: 'Intelligence',
    wisdom: 'Wisdom',
    charisma: 'Charisma',
  };

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.error.set('No character id was provided.');
      return;
    }
    this.characterId.set(id);
    this.loading.set(true);
    this.error.set(null);
    this.service.getCharacter(id).subscribe({
      next: (character) => {
        const { form, abilityScores } = this.service.editForm(character);
        this.form.set(form);
        this.abilityScores.set(abilityScores);
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

  submit(): void {
    const form = this.form();
    if (!form || form.invalid) {
      form?.markAllAsTouched();
      return;
    }
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      return;
    }

    this.saving.set(true);
    this.error.set(null);
    const value = form.getRawValue() as CharacterSheetUpdateRequest;
    // A blank campaign id is not a valid partial update; drop it.
    if (!value.campaignId) {
      delete value.campaignId;
    }
    this.service.updateCharacter(id, value).subscribe({
      next: () => {
        this.saved.set(true);
        this.saving.set(false);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        const message =
          err && typeof err === 'object' && 'message' in err
            ? (err as { message?: string }).message
            : 'Unable to update the character. Please try again later.';
        this.error.set(message ?? 'Unable to update the character. Please try again later.');
      },
    });
  }

  modifierFor(name: AbilityName): number {
    const score = this.abilityScores()?.get(name)?.value ?? 0;
    return abilityModifier(score);
  }

  cancel(): void {
    const id = this.route.snapshot.paramMap.get('id');
    this.router.navigate(['/characters', id ?? '']);
  }
}
