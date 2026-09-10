package com.gamemasterx.server.gameplay.service;

import com.gamemasterx.server.dice.DiceExpression;
import com.gamemasterx.server.dice.DiceRoller;
import com.gamemasterx.server.dice.RollMode;
import com.gamemasterx.server.encounter.model.RulesProfile;
import com.gamemasterx.server.gameplay.model.Action;
import com.gamemasterx.server.gameplay.model.ActionOutcome;
import com.gamemasterx.server.gameplay.model.ActionType;
import com.gamemasterx.server.gameplay.model.CheckType;
import com.gamemasterx.server.gameplay.model.DiceResult;
import com.gamemasterx.server.gameplay.model.Modifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Application service that owns the deterministic resolution of the three
 * backend-owned check kinds: {@link CheckType#ABILITY_CHECK}s, {@link
 * CheckType#SKILL_CHECK}s and {@link CheckType#SAVING_THROW}s.
 *
 * <p>The service is the single authority that turns a check request into an
 * auditable {@link Action}. Every check is resolved by:</p>
 *
 * <ol>
 *   <li>Rolling a {@code 1d20} through the shared dice subsystem &ndash; the
 *       {@link DiceRoller} &ndash; using the caller-chosen {@link RollMode}
 *       (unpredictable random or reproducible seeded). The individual die value
 *       is captured on a {@link DiceResult}, so the exact randomness that
 *       produced the outcome is preserved.</li>
 *   <li>Adding a deterministic set of modifiers &ndash; the ability modifier
 *       ({@code floor((score - 10) / 2)}) and, when it applies, the proficiency
 *       bonus &ndash; to the die total.</li>
 *   <li>Recording the inputs, the ordered modifiers, the random values and the
 *       final {@link Action#total() total} on an immutable {@link Action}.</li>
 * </ol>
 *
 * <p>Proficiency and ability modifiers are therefore applied deterministically:
 * the same ability score, proficiency and roll mode/seed always yield the same
 * modifiers and, for a given seed, the same die values and the same total. The
 * outcome ({@link ActionOutcome}) is derived from an optional difficulty class
 * (DC): {@code total >= DC} is a success, otherwise a failure; with no DC the
 * outcome is {@link ActionOutcome#UNKNOWN}.</p>
 *
 * <p>This service owns <em>only</em> the computation. It never persists. Storage
 * as an auditable record with a revision before and after is the
 * responsibility of the caller (typically the gameplay controller, which
 * delegates to {@link GameplayAuditService}).</p>
 */
@Service
public final class GameplayRulesService {

    /** The single die rolled for every check: a standard twenty-sided die. */
    static final int CHECK_DIE_SIZE = 20;
    /** The single die rolled for every check. */
    static final int CHECK_DIE_COUNT = 1;

    private final DiceRoller diceRoller;

    /**
     * Creates the rules service with its dice roller. The roller is shared with
     * the dice controller so that the same {@link RollMode} semantics apply
     * everywhere.
     *
     * @param diceRoller the shared dice roller
     */
    public GameplayRulesService(DiceRoller diceRoller) {
        this.diceRoller = diceRoller;
    }

    /**
     * Resolves an attack roll: {@code 1d20 + attack modifier} compared against
     * the target's {@link AttackInput#armorClass()} armor class, with the
     * critical-hit verdict taken from the governing {@link RulesProfile}.
     *
     * <p>The to-hit total is {@code roll + ability modifier + (proficiency bonus,
     * when proficient)}. A natural attack roll meeting the profile's critical-hit
     * threshold (a natural 20 under the default profile) is a critical hit.
     * Otherwise the outcome is a hit when the to-hit total meets or exceeds the
     * armor class and a miss otherwise.</p>
     *
     * @param input the attack input
     * @return the resolved attack
     * @throws IllegalArgumentException when {@code input} is {@code null} or no
     *                                  armor class is supplied
     */
    public ResolvedAttack resolveAttack(AttackInput input) {
        Objects.requireNonNull(input, "An attack input is required");

        Integer armorClass = input.armorClass();
        if (armorClass == null) {
            throw new IllegalArgumentException("A target armor class is required for an attack");
        }

        RulesProfile profile = (input.rulesProfile() != null)
                ? input.rulesProfile()
                : RulesProfile.SRD_5_2024;

        // Deterministic attack modifier: ability modifier and, when the actor is
        // proficient, the proficiency bonus.
        int abilityModifier = abilityModifier(input.abilityScore());
        int proficiencyBonus = input.proficiencyBonus() == 0 ? 0 : input.proficiencyBonus();
        boolean appliesProficiency = input.proficient();
        int effectiveProficiency = appliesProficiency ? proficiencyBonus : 0;

        // Roll the 1d20 through the shared dice subsystem.
        DiceExpression expression = DiceExpression.parse(
                CHECK_DIE_COUNT + "d" + CHECK_DIE_SIZE);
        List<DiceResult> diceResults = diceRoller.roll(expression, input.label());
        DiceResult dieResult = firstDie(diceResults);
        int roll = rawDieValue(dieResult);

        // Build the ordered, named modifier list.
        List<Modifier> modifiers = new ArrayList<>();
        modifiers.add(new Modifier(abilityModifierName(input.abilityName()), abilityModifier));
        if (appliesProficiency && effectiveProficiency != 0) {
            modifiers.add(new Modifier("Proficiency", effectiveProficiency));
        }

        int modifierTotal = modifierTotal(modifiers);
        int toHitTotal = roll + modifierTotal;

        // To-hit comparison against the target's armor class.
        boolean hit = toHitTotal >= armorClass;

        // Critical-hit verdict and the damage multiplier that the governing
        // profile prescribes. Both are owned entirely by the backend rules:
        // the threshold and the multiplier come from the selected rules
        // subset, never from any language-model output.
        boolean critical = profile.isCriticalHit(roll);
        double criticalDamageMultiplier = profile.getCriticalDamageMultiplier();

        // Classify the outcome consistently: a crit is always a hit; otherwise a
        // hit meets or exceeds the armor class and a miss falls below it.
        ActionOutcome outcome;
        if (critical) {
            outcome = ActionOutcome.CRIT;
        } else if (hit) {
            outcome = ActionOutcome.SUCCESS;
        } else {
            outcome = ActionOutcome.FAILURE;
        }

        Action action = new Action(
                null,
                ActionType.ATTACK,
                input.actorId(),
                input.targetId(),
                input.targetName(),
                input.abilityName(),
                List.copyOf(modifiers),
                diceResults,
                modifierTotal,
                toHitTotal,
                outcome,
                input.note(),
                Instant.now());

        return new ResolvedAttack(
                input.actorId(),
                input.abilityName(),
                input.proficient(),
                proficiencyBonus,
                appliesProficiency,
                abilityModifier,
                modifiers,
                dieResult,
                roll,
                modifierTotal,
                toHitTotal,
                armorClass,
                hit,
                critical,
                outcome,
                profile,
                criticalDamageMultiplier,
                action);
    }

    /**
     * A fully-specified input to {@link #resolveAttack(AttackInput)}.
     *
     * <p>The attack rolls a {@code 1d20} through the shared dice subsystem and
     * compares the resulting to-hit total (roll plus the deterministic attack
     * modifiers) against the target's {@link #armorClass()}.</p>
     *
     * @param actorId          who performs the attack (the acting participant)
     * @param targetId         the target id, when applicable
     * @param targetName       the target name, when applicable
     * @param abilityName      the ability the attack is based on (for example {@code "Strength"})
     * @param abilityScore     the raw ability score; the modifier is derived
     * @param proficient       whether the actor is proficient in the attack
     * @param proficiencyBonus the proficiency bonus when proficient (treated as
     *                         {@code 0} when {@code 0})
     * @param armorClass       the target's armor class the to-hit total must beat
     * @param rulesProfile     the rules profile governing critical hits, or
     *                         {@code null} to fall back to {@link
     *                         RulesProfile#SRD_5_2024}
     * @param rollMode         the {@link RollMode} wire value ({@code random} /
     *                         {@code seeded}); defaults to {@code random}
     * @param seed             the seed for {@code seeded} rolls, or {@code null}
     * @param label            a human-readable label for the die result, or
     *                         {@code null}
     * @param note             a free-form note on the action, or {@code null}
     */
    public record AttackInput(
            String actorId,
            String targetId,
            String targetName,
            String abilityName,
            int abilityScore,
            boolean proficient,
            int proficiencyBonus,
            Integer armorClass,
            RulesProfile rulesProfile,
            String rollMode,
            String seed,
            String label,
            String note) {
    }

    /**
     * The auditable outcome of a resolved attack.
     *
     * <p>This bundles everything the audit trail needs: the actor and ability,
     * the attack modifiers that were applied, the resolved {@link DiceResult}
     * (recording the individual die value that was drawn), the natural attack
     * roll, the aggregate to-hit total, the target armor class, the {@link #hit}
     * comparison verdict, the {@link #critical} verdict, the resolved {@link
     * #outcome} and the governing {@link #profile}. The immutable {@link
     * Action} carries the full record.</p>
     *
     * @param actorId          who performed the attack
     * @param abilityName      the ability the attack was based on
     * @param proficient       whether the actor was proficient
     * @param proficiencyBonus the declared proficiency bonus
     * @param appliesProficiency whether the proficiency bonus was actually applied
     * @param abilityModifier  the ability modifier that was applied
     * @param modifiers        the ordered, named modifiers that were applied
     * @param dieResult        the resolved {@link DiceResult} for the attack
     * @param roll             the natural {@code 1d20} value that was rolled
     * @param modifierTotal    the sum of every applied modifier
     * @param toHitTotal       the to-hit total: {@code roll + modifierTotal}
     * @param armorClass       the target armor class the attack was resolved against
     * @param hit              {@code true} when {@code toHitTotal} met or exceeded {@code armorClass}
     * @param critical         {@code true} when the natural roll was a critical hit
     * @param outcome          the resolved {@link ActionOutcome}
     * @param profile          the rules profile that governed critical hits
     * @param criticalDamageMultiplier the damage multiplier prescribed by the
     *                                 governing profile for a critical hit (for
     *                                 example {@code 2.0}); backend-owned, never
     *                                 language-model output
     * @param action           the immutable {@link Action} capturing the full record
     */
    public record ResolvedAttack(
            String actorId,
            String abilityName,
            boolean proficient,
            int proficiencyBonus,
            boolean appliesProficiency,
            int abilityModifier,
            List<Modifier> modifiers,
            DiceResult dieResult,
            int roll,
            int modifierTotal,
            int toHitTotal,
            int armorClass,
            boolean hit,
            boolean critical,
            ActionOutcome outcome,
            RulesProfile profile,
            double criticalDamageMultiplier,
            Action action) {
    }

    /**
     * Resolves a general ability check: {@code 1d20 + ability modifier}, applying
     * the proficiency bonus only when the actor is proficient.
     *
     * @param input the check input
     * @return the resolved check
     */
    public ResolvedCheck resolveAbilityCheck(CheckInput input) {
        return resolve(input.withType(CheckType.ABILITY_CHECK));
    }

    /**
     * Resolves a skill check: {@code 1d20 + ability modifier + proficiency bonus}.
     * Proficiency is always applied, as skills are, by definition, professed.
     *
     * @param input the check input
     * @return the resolved check
     */
    public ResolvedCheck resolveSkillCheck(CheckInput input) {
        return resolve(input.withType(CheckType.SKILL_CHECK));
    }

    /**
     * Resolves a saving throw: {@code 1d20 + ability modifier}, applying the
     * proficiency bonus only when the actor is proficient.
     *
     * @param input the check input
     * @return the resolved check
     */
    public ResolvedCheck resolveSavingThrow(CheckInput input) {
        return resolve(input.withType(CheckType.SAVING_THROW));
    }

    /**
     * The shared resolution routine used by the three public methods.
     *
     * @param input the fully-specified check input
     * @return the resolved check, embedding the immutable {@link Action}
     * @throws IllegalArgumentException when {@code input} is {@code null} or the
     *                                  roll mode is unrecognized
     */
    public ResolvedCheck resolve(CheckInput input) {
        Objects.requireNonNull(input, "A check input is required");

        CheckType type = input.checkType();
        if (type == null) {
            throw new IllegalArgumentException("A check type is required");
        }

        // Deterministic modifiers.
        int abilityModifier = abilityModifier(input.abilityScore());
        int proficiencyBonus = input.proficiencyBonus() == 0 ? 0 : input.proficiencyBonus();
        boolean appliesProficiency = appliesProficiency(type, input.proficient());
        int effectiveProficiency = appliesProficiency ? proficiencyBonus : 0;

        // Roll the 1d20 through the shared dice subsystem.
        DiceExpression expression = DiceExpression.parse(
                CHECK_DIE_COUNT + "d" + CHECK_DIE_SIZE);
        List<DiceResult> diceResults = diceRoller.roll(expression, input.label());
        DiceResult dieResult = firstDie(diceResults);

        int roll = rawDieValue(dieResult);

        // Build the ordered, named modifier list.
        List<Modifier> modifiers = new ArrayList<>();
        modifiers.add(new Modifier(abilityModifierName(input.abilityName()), abilityModifier));
        if (appliesProficiency && effectiveProficiency != 0) {
            modifiers.add(new Modifier("Proficiency", effectiveProficiency));
        }

        int modifierTotal = modifierTotal(modifiers);
        int total = roll + modifierTotal;

        // Outcome is derived deterministically from the optional DC.
        Integer dc = input.dc();
        ActionOutcome outcome;
        boolean success;
        if (dc == null) {
            outcome = ActionOutcome.UNKNOWN;
            success = false;
        } else {
            success = total >= dc;
            outcome = success ? ActionOutcome.SUCCESS : ActionOutcome.FAILURE;
        }

        Action action = new Action(
                null,
                type.toActionType(),
                input.actorId(),
                input.targetId(),
                input.targetName(),
                input.abilityName(),
                List.copyOf(modifiers),
                diceResults,
                modifierTotal,
                total,
                outcome,
                input.note(),
                Instant.now());

        return new ResolvedCheck(
                type,
                input.abilityName(),
                input.proficient(),
                proficiencyBonus,
                appliesProficiency,
                abilityModifier,
                modifiers,
                dieResult,
                roll,
                modifierTotal,
                total,
                dc,
                outcome,
                success,
                action);
    }

    /**
     * @param type the check kind
     * @param proficient whether the actor declared proficiency
     * @return {@code true} when proficiency applies to this check. Skill checks
     *         always apply proficiency; ability checks and saving throws apply
     *         it only when the actor is proficient.
     */
    private static boolean appliesProficiency(CheckType type, boolean proficient) {
        return switch (type) {
            case SKILL_CHECK -> true;
            case ABILITY_CHECK, SAVING_THROW -> proficient;
        };
    }

    /**
     * @param score the ability score
     * @return the standard ability modifier {@code floor((score - 10) / 2)}
     */
    private static int abilityModifier(int score) {
        return Math.floorDiv(score - 10, 2);
    }

    /**
     * @param abilityName the ability the check is based on, for example
     *                    {@code "Strength"}
     * @return the modifier label, for example {@code "Strength modifier"}
     */
    private static String abilityModifierName(String abilityName) {
        String name = (abilityName != null && !abilityName.isBlank()) ? abilityName : "Ability";
        return name + " modifier";
    }

    /**
     * @param diceResults the resolved results from the dice subsystem
     * @return the first result, or a fresh zero result when none were produced
     */
    private static DiceResult firstDie(List<DiceResult> diceResults) {
        if (diceResults == null || diceResults.isEmpty()) {
            throw new IllegalStateException(
                    "The dice subsystem produced no result for the check");
        }
        return diceResults.get(0);
    }

    /**
     * The raw twenty-sided value that was rolled. Because a check rolls a plain
     * {@code 1d20} with no modifier, the raw value is the single recorded die
     * value (the sum of the, exactly one, rolls).
     *
     * @param dieResult the resolved die result
     * @return the raw die value in {@code [1, 20]}
     */
    private static int rawDieValue(DiceResult dieResult) {
        if (dieResult.hasNoRolls()) {
            return 0;
        }
        return dieResult.rolls().get(0);
    }

    private static int modifierTotal(List<Modifier> modifiers) {
        int sum = 0;
        for (Modifier m : modifiers) {
            sum += m.value();
        }
        return sum;
    }

    /**
     * A fully-specified input to {@link GameplayRulesService#resolve(CheckInput)}.
     *
     * <p>The three public entry points build one of these with a concrete
     * {@link CheckType}; {@link #withType(CheckType)} allows a caller to reuse a
     * base input while fixing the check kind.</p>
     *
     * @param checkType       the check kind (may be {@code null} on a base input; it is
     *                        required on a resolved input)
     * @param actorId         who performs the check
     * @param targetId        the target id, when applicable
     * @param targetName      the target name, when applicable
     * @param abilityName     the ability the check is based on (for example {@code "Strength"})
     * @param abilityScore    the raw ability score; the modifier is derived
     * @param proficient      whether the actor is proficient in the check
     * @param proficiencyBonus the proficiency bonus when proficient (treated as
     *                         {@code 0} when {@code 0})
     * @param dc              the difficulty class the total must meet or exceed, or
     *                        {@code null} for an unqualified roll
     * @param rollMode        the {@link RollMode} wire value ({@code random} /
     *                        {@code seeded}); defaults to {@code random}
     * @param seed            the seed for {@code seeded} rolls, or {@code null}
     * @param label           a human-readable label for the die result, or {@code null}
     * @param note            a free-form note on the action, or {@code null}
     */
    public record CheckInput(
            CheckType checkType,
            String actorId,
            String targetId,
            String targetName,
            String abilityName,
            int abilityScore,
            boolean proficient,
            int proficiencyBonus,
            Integer dc,
            String rollMode,
            String seed,
            String label,
            String note) {

        /**
         * @param checkType the check kind to apply
         * @return a copy of this input with the given {@code checkType}
         */
        public CheckInput withType(CheckType checkType) {
            return new CheckInput(
                    checkType,
                    actorId,
                    targetId,
                    targetName,
                    abilityName,
                    abilityScore,
                    proficient,
                    proficiencyBonus,
                    dc,
                    rollMode,
                    seed,
                    label,
                    note);
        }
    }

    /**
     * The auditable outcome of a resolved check.
     *
     * <p>This bundles everything the audit trail needs: the {@link CheckType},
     * the ability and proficiency modifiers that were applied, the resolved
     * {@link DiceResult} (recording the individual die value that was drawn), the
     * raw die value, the aggregate {@link #total}, the optional DC, the derived
     * {@link #outcome} and {@link #success} verdict, and the immutable {@link
     * Action} that carries the full record.</p>
     *
     * @param checkType       the kind of check resolved
     * @param abilityName     the ability the check was based on
     * @param proficient      whether the actor was proficient
     * @param proficiencyBonus the declared proficiency bonus
     * @param appliesProficiency whether the proficiency bonus was actually applied
     * @param abilityModifier the ability modifier that was applied
     * @param modifiers       the ordered, named modifiers that were applied
     * @param dieResult       the resolved {@link DiceResult} for the check
     * @param roll            the raw {@code 1d20} value that was rolled
     * @param modifierTotal   the sum of every applied modifier
     * @param total           the final outcome: {@code roll + modifierTotal}
     * @param dc              the difficulty class, or {@code null}
     * @param outcome         the resolved {@link ActionOutcome}
     * @param success         {@code true} when {@code total} met or exceeded {@code dc}
     * @param action          the immutable {@link Action} capturing the full record
     */
    public record ResolvedCheck(
            CheckType checkType,
            String abilityName,
            boolean proficient,
            int proficiencyBonus,
            boolean appliesProficiency,
            int abilityModifier,
            List<Modifier> modifiers,
            DiceResult dieResult,
            int roll,
            int modifierTotal,
            int total,
            Integer dc,
            ActionOutcome outcome,
            boolean success,
            Action action) {
    }
}
