import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MembershipService } from './membership.service';
import {
  Encounter,
  Participant,
  EncounterStatus,
} from '@contracts/encounter';
import {
  CheckRequest,
  AttackRequest,
  ResolvedCheck,
  ResolvedAttack,
  ActionOutcome,
} from '@contracts/gameplay';
import {
  EncounterPlayService,
  Turn,
  PlayRole,
} from './encounter-play.service';
import { DiceRollerComponent } from './dice-roller.component';

/** A participant in its initiative-order slot. */
interface InitiativeEntry {
  participant: Participant;
  initiative: number | null;
  isActive: boolean;
}

/**
 * Active encounter view.
 *
 * <p>This is the live encounter UI. It shows the initiative order and the
 * current actor, the hit points, resources and conditions of each participant,
 * an auditable dice roller, action submission (ability checks, skill checks,
 * saving throws and attacks) that posts to the backend turn APIs with idempotency
 * keys, and the immutable turn history. Pause, resume, advance and lifecycle
 * controls are provided for the game master.</p>
 *
 * <p><strong>Visibility is not enforcement.</strong> The controls shown or hidden
 * here follow the caller's campaign role so the interface stays focused. This
 * visibility is derived purely from the role and is always reversible by a
 * client, so it never provides security. Every state-changing request below is
 * authorised server-side and returns {@code 403 Forbidden} when the caller lacks
 * the required role. The backend is the single enforcement boundary; the absence
 * of a control in the UI must never be relied upon as an access control.</p>
 */
@Component({
  selector: 'app-encounter-play',
  standalone: true,
  imports: [CommonModule, RouterLink, ReactiveFormsModule, DiceRollerComponent],
  templateUrl: './encounter-play.component.html',
  styleUrl: './encounter-play.component.css',
})
export class EncounterPlayComponent implements OnInit {
  private readonly service = inject(EncounterPlayService);
  private readonly membership = inject(MembershipService);
  private readonly route = inject(ActivatedRoute);
  private readonly formBuilder = inject(FormBuilder);

  readonly encounterId = signal<string | null>(null);
  readonly campaignId = signal<string | null>(null);
  readonly actorRole = signal<PlayRole | null>(null);
  readonly encounter = signal<Encounter | null>(null);
  readonly turns = signal<Turn[]>([]);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly applying = signal<string | null>(null);

  readonly rollMode = signal<'random' | 'seeded'>('random');
  readonly seed = signal<string>('');

  /** The last resolved check result, for display. */
  readonly lastCheck = signal<ResolvedCheck | null>(null);
  /** The last resolved attack result, for display. */
  readonly lastAttack = signal<ResolvedAttack | null>(null);

  /** Reactive form for ability/skill/saving-throw submissions. */
  readonly checkForm = this.formBuilder.group({
    kind: ['ability-check'],
    actorId: ['', Validators.required],
    targetId: [''],
    abilityName: ['Strength', Validators.required],
    abilityScore: [10, [Validators.required, Validators.min(1), Validators.max(30)]],
    proficient: [false],
    proficiencyBonus: [2, [Validators.min(0)]],
    dc: [null as number | null, [Validators.min(1), Validators.max(30)]],
    note: [''],
  });
  /** Reactive form for attack submissions. */
  readonly attackForm = this.formBuilder.group({
    actorId: ['', Validators.required],
    targetId: [''],
    abilityName: ['Strength', Validators.required],
    abilityScore: [18, [Validators.required, Validators.min(1), Validators.max(30)]],
    proficient: [true],
    proficiencyBonus: [2, [Validators.min(0)]],
    armorClass: [15, [Validators.required, Validators.min(1), Validators.max(30)]],
    note: [''],
  });

  /** The three check kinds offered in the check selector. */
  readonly checkKinds = ['ability-check', 'skill-check', 'saving-throw'] as const;
  readonly abilities = ['Strength', 'Dexterity', 'Constitution', 'Intelligence', 'Wisdom', 'Charisma'];

  ngOnInit(): void {
    this.route.params.subscribe((params) => {
      const id = params['id'];
      this.encounterId.set(id);
      this.campaignId.set(this.route.snapshot.queryParamMap.get('campaignId'));
      if (id) {
        this.load();
      }
    });
  }

  // ----- Role-gated visibility (presentation only) -----

  /** Whether the current caller may perform game-master actions. */
  canManage(): boolean {
    return EncounterPlayService.isAtLeast(this.actorRole(), 'GAME_MASTER');
  }

  /** Whether the current caller may view the encounter. */
  canView(): boolean {
    return this.actorRole() != null;
  }

  statusOf(): EncounterStatus | null {
    return this.encounter()?.status ?? null;
  }

  // ----- Loading -----

  load(): void {
    const id = this.encounterId();
    if (!id) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);

    this.service.fetchEncounter(id).subscribe({
      next: (encounter) => {
        this.encounter.set(encounter);
        this.loading.set(false);
        this.loadTurns();
        this.loadRole();
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(this.resolveError(err));
      },
    });
  }

  private loadTurns(): void {
    const id = this.encounterId();
    if (!id) {
      return;
    }
    this.service.fetchTurns(id).subscribe({
      next: (turns) => this.turns.set(turns ?? []),
      error: () => {
        /* turn history is secondary; the encounter is still usable */
      },
    });
  }

  /**
   * Loads the caller's campaign role to drive UI visibility. The role only
   * decides which controls are shown; the backend still authorises every
   * mutating call, so this is never an access control.
   */
  private loadRole(): void {
    const campaignId = this.campaignId();
    if (!campaignId) {
      this.loading.set(false);
      return;
    }
    this.membership.getCampaignDashboard(campaignId).subscribe({
      next: (dashboard) => {
        this.actorRole.set(dashboard.actorRole as PlayRole);
      },
      error: () => {
        /* role unknown; the view still renders in read-only form */
      },
    });
  }

  // ----- Initiative order -----

  /** Participants ordered by the backend-established initiative order. */
  get initiativeEntries(): InitiativeEntry[] {
    const encounter = this.encounter();
    if (!encounter) {
      return [];
    }
    const order = encounter.initiativeOrder ?? [];
    const byId = new Map<string, Participant>(
      (encounter.participants ?? []).map((p) => [p.id, p]),
    );
    const entries: InitiativeEntry[] = order.map((pid) => ({
      participant: byId.get(pid)!,
      initiative: byId.get(pid)?.initiative ?? null,
      isActive: this.isCurrentTurn(pid),
    }));
    // Append participants the backend did not rank (no initiative yet).
    for (const participant of encounter.participants ?? []) {
      if (!order.includes(participant.id)) {
        entries.push({ participant, initiative: participant.initiative, isActive: false });
      }
    }
    return entries;
  }

  /** Whether the given participant id is on the current turn. */
  isCurrentTurn(participantId: string): boolean {
    const encounter = this.encounter();
    if (!encounter) {
      return false;
    }
    const order = encounter.initiativeOrder ?? [];
    const index = encounter.turn;
    return index >= 0 && index < order.length && order[index] === participantId;
  }

  /** The participant whose turn it currently is, or null. */
  currentParticipant(): Participant | null {
    const encounter = this.encounter();
    if (!encounter) {
      return null;
    }
    const order = encounter.initiativeOrder ?? [];
    const index = encounter.turn;
    if (index < 0 || index >= order.length) {
      return null;
    }
    const current = encounter.participants?.find((p) => p.id === order[index]);
    return current ?? null;
  }

  // ----- Lifecycle controls (game master) -----

  startEncounter(): void {
    this.act('start', () =>
      this.service.startEncounter(
        this.encounterId()!,
        this.rollMode() === 'seeded' ? 'seeded' : 'random',
        this.rollMode() === 'seeded' && this.seed() ? this.seed() : undefined,
      ),
    );
  }

  rollInitiative(): void {
    this.act('initiative', () =>
      this.service.generateInitiative(
        this.encounterId()!,
        this.rollMode() === 'seeded' ? 'seeded' : 'random',
        this.rollMode() === 'seeded' && this.seed() ? this.seed() : undefined,
      ),
    );
  }

  pauseEncounter(): void {
    this.act('pause', () => this.service.pauseEncounter(this.encounterId()!));
  }

  resumeEncounter(): void {
    this.act('resume', () => this.service.resumeEncounter(this.encounterId()!));
  }

  completeEncounter(): void {
    this.act('complete', () => this.service.completeEncounter(this.encounterId()!));
  }

  advanceTurn(): void {
    this.act('advance', () => this.service.advanceTurn(this.encounterId()!));
  }

  // ----- Participant state (damage / healing) -----

  applyDamage(participant: Participant, amount: number): void {
    if (amount <= 0) {
      return;
    }
    this.act(
      `damage-${participant.id}`,
      () =>
        this.service.applyDamage(
          this.encounterId()!,
          participant.id,
          amount,
          undefined,
          this.encounter()?.revision,
          this.service.generateIdempotencyKey('damage', participant.id),
        ),
    );
  }

  applyHealing(participant: Participant, amount: number): void {
    if (amount <= 0) {
      return;
    }
    this.act(
      `heal-${participant.id}`,
      () =>
        this.service.applyHealing(
          this.encounterId()!,
          participant.id,
          amount,
          undefined,
          this.encounter()?.revision,
          this.service.generateIdempotencyKey('heal', participant.id),
        ),
    );
  }

  // ----- Action submission -----

  submitCheck(): void {
    if (this.checkForm.invalid) {
      this.checkForm.markAllAsTouched();
      return;
    }
    const value = this.checkForm.getRawValue() as {
      kind: string;
      actorId: string;
      targetId: string;
      abilityName: string;
      abilityScore: number;
      proficient: boolean;
      proficiencyBonus: number;
      dc: number | null;
    };
    const actorId = value.actorId;
    const request: CheckRequest = {
      actorId,
      campaignId: this.campaignId() ?? undefined,
      encounterId: this.encounterId() ?? undefined,
      targetId: value.targetId || undefined,
      abilityName: value.abilityName,
      abilityScore: value.abilityScore,
      proficient: value.proficient,
      proficiencyBonus: value.proficiencyBonus,
      dc: value.dc ?? undefined,
    };
    const key = this.service.generateIdempotencyKey(value.kind, actorId);
    const revision = this.encounter()?.revision;

    let source;
    if (value.kind === 'skill-check') {
      source = this.service.skillCheck(request, revision, key);
    } else if (value.kind === 'saving-throw') {
      source = this.service.savingThrow(request, revision, key);
    } else {
      source = this.service.abilityCheck(request, revision, key);
    }
    source.subscribe({
      next: (result: ResolvedCheck) => {
        this.lastCheck.set(result);
        this.loadTurns();
      },
      error: (err) => this.error.set(this.resolveError(err)),
    });
  }

  submitAttack(): void {
    if (this.attackForm.invalid) {
      this.attackForm.markAllAsTouched();
      return;
    }
    const value = this.attackForm.getRawValue() as {
      actorId: string;
      targetId: string;
      abilityName: string;
      abilityScore: number;
      proficient: boolean;
      proficiencyBonus: number;
      armorClass: number;
    };
    const actorId = value.actorId;
    const request: AttackRequest = {
      actorId,
      campaignId: this.campaignId() ?? undefined,
      encounterId: this.encounterId()!,
      targetId: value.targetId || undefined,
      abilityName: value.abilityName,
      abilityScore: value.abilityScore,
      proficient: value.proficient,
      proficiencyBonus: value.proficiencyBonus,
      armorClass: value.armorClass,
    };
    const key = this.service.generateIdempotencyKey('attack', actorId);
    const revision = this.encounter()?.revision;
    this.service.attack(request, revision, key).subscribe({
      next: (result: ResolvedAttack) => {
        this.lastAttack.set(result);
        this.loadTurns();
      },
      error: (err) => this.error.set(this.resolveError(err)),
    });
  }

  // ----- HP helpers -----

  hpPercent(participant: Participant | null): number {
    if (!participant) {
      return 100;
    }
    const hp = participant.hitPoints;
    if (!hp || hp.max <= 0) {
      return 100;
    }
    return Math.max(0, Math.min(100, Math.round((hp.current / hp.max) * 100)));
  }

  hpClass(participant: Participant | null): string {
    const percent = this.hpPercent(participant);
    if (percent <= 0) {
      return 'hp-empty';
    }
    if (percent < 25) {
      return 'hp-low';
    }
    if (percent < 60) {
      return 'hp-moderate';
    }
    return 'hp-healthy';
  }

  /** Converts a template-provided string value to a number. */
  toNumber(value: string | number | null | undefined): number {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  hpLabel(participant: Participant | null): string {
    if (!participant) {
      return '-';
    }
    const hp = participant.hitPoints;
    if (!hp) {
      return '—';
    }
    return `${Math.max(0, hp.current)} / ${hp.max}`;
  }

  // ----- Turn history -----

  participantName(id: string | null): string | null {
    if (!id) {
      return null;
    }
    return this.encounter()?.participants?.find((p) => p.id === id)?.name ?? null;
  }

  outcomeLabel(outcome: ActionOutcome | string | null | undefined): string {
    switch (outcome) {
      case 'CRIT':
        return 'Critical hit';
      case 'SUCCESS':
        return 'Success / Hit';
      case 'FAILURE':
        return 'Failure / Miss';
      case 'PARTIAL':
        return 'Partial';
      default:
        return String(outcome);
    }
  }

  // ----- Helpers -----

  /**
   * Runs a lifecycle/participant mutation that returns an updated encounter.
   * The encounter is refreshed and turn history reloaded on success.
   */
  private act(id: string, action: () => ReturnType<EncounterPlayService['pauseEncounter']>): void {
    this.error.set(null);
    this.applying.set(id);
    action().subscribe({
      next: (encounter) => {
        this.applying.set(null);
        if (encounter) {
          this.encounter.set(encounter);
          this.loadTurns();
        }
      },
      error: (err) => {
        this.applying.set(null);
        this.error.set(this.resolveError(err));
      },
    });
  }

  private resolveError(err: unknown): string {
    if (err && typeof err === 'object' && 'message' in err) {
      const message = (err as { message?: string }).message;
      if (typeof message === 'string' && message.trim()) {
        return message;
      }
    }
    return 'The request failed. Please try again later.';
  }
}
