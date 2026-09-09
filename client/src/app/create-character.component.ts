import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  CharacterService,
  CharacterSheetCreateRequest,
  ABILITY_NAMES,
  AbilityName,
  abilityModifier,
} from './character.service';

/**
 * The character creation screen submits a valid character through
 * {@link CharacterService.createCharacter}. It uses a reactive form whose
 * validators mirror the server-side constraints, including ability-score range
 * validation, and it reports loading, validation, and failure states.
 */
@Component({
  selector: 'app-create-character',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './create-character.component.html',
  styleUrl: './create-character.component.css',
})
export class CreateCharacterComponent implements OnInit {
  private readonly service = inject(CharacterService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);

  readonly form = this.service.createForm(this.resolveCampaignId());
  readonly abilityScores = this.form.abilityScores;
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly created = signal<string | null>(null);

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
    this.form.form.updateValueAndValidity();
  }

  private resolveCampaignId(): string {
    const campaignId = this.route.snapshot.queryParams['campaignId'];
    return typeof campaignId === 'string' && campaignId ? campaignId : '';
  }

  submit(): void {
    if (this.form.form.invalid) {
      this.form.form.markAllAsTouched();
      return;
    }

    this.loading.set(true);
    this.error.set(null);
    const value = this.form.form.getRawValue() as CharacterSheetCreateRequest;
    this.service.createCharacter(value).subscribe({
      next: (character) => {
        this.created.set(character.id);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        const message =
          err && typeof err === 'object' && 'message' in err
            ? (err as { message?: string }).message
            : 'Unable to create character. Please try again later.';
        this.error.set(message ?? 'Unable to create character. Please try again later.');
      },
    });
  }

  /** The ability modifier that would be derived from a score, shown for preview. */
  modifierFor(name: AbilityName): number {
    const score = this.abilityScores.get(name)?.value ?? 0;
    return abilityModifier(score);
  }

}
