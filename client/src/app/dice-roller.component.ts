import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DiceRollRequest, DiceRollResponse } from '@contracts/dice';
import { EncounterPlayService, TurnDiceResult } from './encounter-play.service';

/**
 * The resolved state of a single dice roll, enriched for display.
 */
export interface DiceRollState {
  request: DiceRollRequest;
  response: DiceRollResponse;
}

/**
 * The auditable dice roller.
 *
 * <p>Resolves a dice expression through the backend {@code POST /api/dice/roll}
 * endpoint and displays each resolved die record with its <em>inputs</em>
 * (the dice expression and label), its <em>modifiers</em>, the <em>random values
 * that were drawn</em> ({@code rolls}), and the resulting <em>outcome</em>
 * ({@code total}). Roll mode and seed are shown for seeded rolls so a roll can be
 * reproduced and verified.</p>
 *
 * <p>The dice resolution is fully backend-owned: the client supplies an
 * expression (and optionally a seed for reproducible rolls) and never computes a
 * total itself.</p>
 */
@Component({
  selector: 'app-dice-roller',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './dice-roller.component.html',
  styleUrl: './dice-roller.component.css',
})
export class DiceRollerComponent {
  private readonly service = inject(EncounterPlayService);
  private readonly formBuilder = inject(FormBuilder);

  readonly form = this.formBuilder.group({
    expression: ['1d20', [Validators.required]],
    mode: ['random'],
    seed: [''],
    label: [''],
  });

  readonly rolls = signal<DiceRollState[]>([]);
  readonly error = signal<string | null>(null);
  readonly rolling = signal(false);

  readonly rollModes = ['random', 'seeded'] as const;

  roll(): void {
    this.error.set(null);
    const value = this.form.getRawValue() as {
      expression: string;
      mode: 'random' | 'seeded';
      seed: string;
      label: string;
    };
    const expression = (value.expression ?? '').trim();
    if (!expression) {
      this.error.set('Enter a dice expression, for example "2d6+3" or "1d20adv".');
      return;
    }

    const request: DiceRollRequest = {
      expression,
      mode: value.mode === 'seeded' ? 'seeded' : 'random',
      seed: value.mode === 'seeded' && value.seed ? value.seed : null,
      label: value.label || null,
    };

    this.rolling.set(true);
    this.service.rollDice(request).subscribe({
      next: (response) => {
        this.rolls.update((history) => [{ request, response }, ...history]);
        this.rolling.set(false);
      },
      error: (err: unknown) => {
        this.rolling.set(false);
        this.error.set(this.resolveError(err));
      },
    });
  }

  /**
   * Renders the auditable inputs of a single die result: the number of dice,
   * their size and the applied modifier, for example {@code "2d6 +3"}.
   */
  dieExpression(result: TurnDiceResult): string {
    const sides = `d${result.dieSize}`;
    const dice = `${result.numberOfDice}${sides}`;
    const modifier = result.modifier > 0 ? `+${result.modifier}` : `${result.modifier}`;
    return `${dice} ${modifier}`;
  }

  private resolveError(err: unknown): string {
    if (err && typeof err === 'object' && 'message' in err) {
      const message = (err as { message?: string }).message;
      if (typeof message === 'string' && message.trim()) {
        return message;
      }
    }
    return 'Unable to roll the dice. Please try again later.';
  }
}
