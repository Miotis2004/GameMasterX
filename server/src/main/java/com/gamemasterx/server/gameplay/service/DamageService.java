package com.gamemasterx.server.gameplay.service;

import com.gamemasterx.server.encounter.model.Encounter;
import com.gamemasterx.server.encounter.model.RulesProfile;
import com.gamemasterx.server.encounter.service.RulesProfileService;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Application service that owns the deterministic resolution of the hit-point,
 * temporary-hit-point, death-save and condition rules that govern an encounter.
 *
 * <p>This service is the single authority for every rule-derived change to a
 * participant's body state. It is deliberately pure: every method is a
 * deterministic function of its inputs and the governing {@link RulesProfile}
 * and never touches storage. The {@link EncounterService} is the only layer that
 * loads the aggregate, calls this service to resolve a change, applies the
 * resolved outcome to the stored {@link Encounter.Participant} and persists it
 * (recording it in the immutable audit trail).</p>
 *
 * <h2>Damage and healing</h2>
 *
 * <p>{@link #resolveDamage(DamageInput)} and {@link #resolveHealing(HealingInput)}
 * modify hit points deterministically. Damage first absorbs any temporary hit
 * points, and only the remainder reduces current hit points; current hit points
 * are clamped at {@code 0}. Healing restores current hit points up to the
 * maximum, never beyond, and never touches temporary hit points. Healing is
 * not applied to a creature at {@code 0} hit points, matching the supported
 * rules subset.</p>
 *
 * <h2>Temporary hit points</h2>
 *
 * <p>{@link #resolveTemporaryHitPoints(TemporaryHitPointsInput)} adds a
 * non-negative amount of temporary hit points. Temporary hit points are
 * always tracked, are always consumed before current hit points when damage is
 * dealt, and are never allowed to go below {@code 0}.</p>
 *
 * <h2>Death saves</h2>
 *
 * <p>{@link #resolveDeathSave(DeathSaveInput)} resolves a death saving throw
 * under the encounter's currently selected, supported rules subset. A creature
 * must be at {@code 0} hit points to be making death saving throws. On a roll,
 * {@code roll + modifier} yields a success or a failure according to the subset;
 * a natural {@code 20} and a natural {@code 10} are resolved by the subset, and
 * three failures mean death while three successes make the creature stable.</p>
 *
 * <p>Every method here is deterministic: the same inputs and governing profile
 * always produce the same outcome.</p>
 */
@Service
public final class DamageService {

    /**
     * The number of accumulated failures at which a creature dies while making
     * death saving throws under the supported rules subset.
     */
    public static final int FAILURES_FATAL = 3;

    /**
     * The number of accumulated successes at which a creature becomes stable
     * while making death saving throws under the supported rules subset.
     */
    public static final int SUCCESSES_STABLE = 3;

    private final RulesProfileService rulesProfileService;

    /**
     * Creates the damage service with its rules-profile authority, so death-save
     * behaviour is resolved against the encounter's selected, supported subset.
     *
     * @param rulesProfileService the single authority for the selected profile
     */
    public DamageService(RulesProfileService rulesProfileService) {
        this.rulesProfileService = Objects.requireNonNull(
                rulesProfileService, "A rules profile service is required");
    }

    /**
     * Resolves the effect of {@link DamageInput#amount()} points of damage on a
     * participant's hit points.
     *
     * <p>The deterministic rule is: temporary hit points are absorbed first, so
     * {@code temporaryHitPointsAbsorbed = min(temporary, damage)}; the remaining
     * damage {@code damage - temporaryHitPointsAbsorbed} then reduces current hit
     * points, which are clamped at {@code 0}. The returned
     * {@link ResolvedDamage} records both the amount of damage that actually
     * reduced hit points and the participant's resulting current hit points.</p>
     *
     * @param input the damage input
     * @return the resolved damage and its effect on the participant's hit points
     * @throws IllegalArgumentException when {@code input} is {@code null}, the
     *                                  amount is negative, or the current hit
     *                                  points are not within {@code [0, max]}
     */
    public ResolvedDamage resolveDamage(DamageInput input) {
        Objects.requireNonNull(input, "A damage input is required");
        int damage = input.amount();
        if (damage < 0) {
            throw new IllegalArgumentException("Damage amount must not be negative");
        }
        Encounter.HitPoints hp = input.hitPoints();
        HitPointsState.validate(hp);

        // Temporary hit points are absorbed before current hit points.
        int temporaryAbsorbed = Math.min(hp.getTemporary(), damage);
        int remaining = damage - temporaryAbsorbed;
        int newCurrent = Math.max(0, hp.getCurrent() - remaining);

        return new ResolvedDamage(
                damage,
                temporaryAbsorbed,
                remaining,
                newCurrent,
                wasReducedToZero(hp.getCurrent(), newCurrent));
    }

    /**
     * Applies a previously-resolved {@link ResolvedDamage} to the given hit
     * points, mutating them in place to reflect the absorbed temporary hit
     * points and the reduced current hit points.
     *
     * @param hp        the participant's hit points to mutate
     * @param resolved  the resolved damage to apply
     * @throws IllegalArgumentException when {@code hp} or {@code resolved} is
     *                                  {@code null}
     */
    public void applyDamage(Encounter.HitPoints hp, ResolvedDamage resolved) {
        Objects.requireNonNull(hp, "Hit points are required");
        Objects.requireNonNull(resolved, "Resolved damage is required");
        hp.setTemporary(Math.max(0, hp.getTemporary() - resolved.temporaryHitPointsAbsorbed()));
        hp.setCurrent(resolved.newCurrentHitPoints());
    }

    /**
     * Resolves the effect of {@link HealingInput#amount()} points of healing on a
     * participant's hit points.
     *
     * <p>The deterministic rule is: healing restores current hit points up to
     * {@code max} and never beyond, and it never touches temporary hit points.
     * Healing is not applied to a creature at {@code 0} hit points under the
     * supported rules subset, so {@link ResolvedHealing#applied()} is
     * {@code 0} in that case.</p>
     *
     * @param input the healing input
     * @return the resolved healing and its effect on the participant's hit points
     * @throws IllegalArgumentException when {@code input} is {@code null}, the
     *                                  amount is negative, or the current hit
     *                                  points are not within {@code [0, max]}
     */
    public ResolvedHealing resolveHealing(HealingInput input) {
        Objects.requireNonNull(input, "A healing input is required");
        int amount = input.amount();
        if (amount < 0) {
            throw new IllegalArgumentException("Healing amount must not be negative");
        }
        Encounter.HitPoints hp = input.hitPoints();
        HitPointsState.validate(hp);

        int applied;
        int newCurrent;
        if (hp.getCurrent() == 0) {
            // A creature at 0 hit points cannot be healed under the subset.
            applied = 0;
            newCurrent = 0;
        } else {
            newCurrent = Math.min(hp.getMax(), hp.getCurrent() + amount);
            applied = newCurrent - hp.getCurrent();
        }

        return new ResolvedHealing(amount, applied, newCurrent);
    }

    /**
     * Applies a previously-resolved {@link ResolvedHealing} to the given hit
     * points, mutating the current hit points in place. Temporary hit points are
     * left untouched.
     *
     * @param hp       the participant's hit points to mutate
     * @param resolved the resolved healing to apply
     * @throws IllegalArgumentException when {@code hp} or {@code resolved} is
     *                                  {@code null}
     */
    public void applyHealing(Encounter.HitPoints hp, ResolvedHealing resolved) {
        Objects.requireNonNull(hp, "Hit points are required");
        Objects.requireNonNull(resolved, "Resolved healing is required");
        hp.setCurrent(resolved.newCurrentHitPoints());
    }

    /**
     * Resolves the addition of {@link TemporaryHitPointsInput#amount()} temporary
     * hit points.
     *
     * <p>The deterministic rule is: temporary hit points are increased by the
     * amount and are never allowed to fall below {@code 0}. Temporary hit points
     * do not exceed the participant's current hit point maximum in a way that
     * would inflate their effective health beyond {@code max} plus any previously
     * held temporary hit points; this method simply adds the requested amount
     * to the existing temporary hit points.</p>
     *
     * @param input the temporary-hit-points input
     * @return the resolved temporary hit points and the resulting total
     * @throws IllegalArgumentException when {@code input} is {@code null} or the
     *                                  amount is negative
     */
    public ResolvedTemporaryHitPoints resolveTemporaryHitPoints(TemporaryHitPointsInput input) {
        Objects.requireNonNull(input, "A temporary hit points input is required");
        int amount = input.amount();
        if (amount < 0) {
            throw new IllegalArgumentException("Temporary hit points must not be negative");
        }
        int newTemporary = input.hitPoints().getTemporary() + amount;
        return new ResolvedTemporaryHitPoints(amount, newTemporary);
    }

    /**
     * Applies a previously-resolved {@link ResolvedTemporaryHitPoints} to the
     * given hit points, adding the amount to the temporary hit points in place
     * and clamping the result at {@code 0}.
     *
     * @param hp       the participant's hit points to mutate
     * @param resolved the resolved temporary hit points to apply
     * @throws IllegalArgumentException when {@code hp} or {@code resolved} is
     *                                  {@code null}
     */
    public void applyTemporaryHitPoints(Encounter.HitPoints hp, ResolvedTemporaryHitPoints resolved) {
        Objects.requireNonNull(hp, "Hit points are required");
        Objects.requireNonNull(resolved, "Resolved temporary hit points are required");
        hp.setTemporary(Math.max(0, hp.getTemporary() + resolved.amount()));
    }

    /**
     * Resolves a single death saving throw under the encounter's currently
     * selected, supported rules subset.
     *
     * <p>A creature must be at {@code 0} hit points to be making death saving
     * throws. The roll is compared to the subset's thresholds after adding the
     * saving-throw modifier: a natural {@code 20} recovers {@code 1} hit point
     * (the 2024 subset), a roll of {@code 10} or above yields a success and a
     * roll below {@code 10} yields a failure. The accumulated {@code failures}
     * and {@code successes} are then updated; reaching
     * {@link #FAILURES_FATAL} failures results in death and reaching
     * {@link #SUCCESSES_STABLE} successes makes the creature stable.</p>
     *
     * <p>The resolution is governed exclusively by the encounter's stored
     * profile, which is never taken from a value supplied on the input; a
     * missing or unsupported (legacy) profile is rejected.</p>
     *
     * @param input the death-save input
     * @return the resolved death saving throw
     * @throws IllegalArgumentException when {@code input} is {@code null} or the
     *                                  creature is not at {@code 0} hit points
     * @throws com.gamemasterx.server.encounter.RulesProfileValidationException
     *                                  when the governing profile is missing or
     *                                  not supported
     */
    public DeathSaveOutcome resolveDeathSave(DeathSaveInput input) {
        Objects.requireNonNull(input, "A death save input is required");
        if (input.hitPoints().getCurrent() != 0) {
            throw new IllegalArgumentException(
                    "A creature making a death saving throw must be at 0 hit points");
        }

        // The governing profile is the encounter's stored profile; it is never
        // taken from the input so death-save behaviour cannot be overridden.
        RulesProfile profile = input.rulesProfile();
        if (profile == null) {
            profile = RulesProfile.SRD_5_2024;
        }
        rulesProfileService.assertCriticalHitPermitted(profile);

        int roll = input.roll();
        int modifier = input.savingThrowModifier();
        int failures = input.failures();
        int successes = input.successes();

        boolean naturalTwenty = roll == 20;
        boolean naturalTen = roll == 10;

        // The result of the raw roll (before accumulation): RECOVERED on a
        // natural 20, a SUCCESS or FAILURE otherwise per the subset.
        String rollResult;
        boolean recovered;
        if (naturalTwenty) {
            // 2024 subset: a natural 20 recovers 1 hit point.
            recovered = true;
            rollResult = "RECOVERED";
        } else if (roll >= 10) {
            recovered = false;
            rollResult = "SUCCESS";
        } else {
            recovered = false;
            rollResult = "FAILURE";
        }

        boolean alive;
        int newFailures = failures;
        int newSuccesses = successes;
        String finalResult;
        int newCurrent = hpCurrentAfterDeathSave(input.hitPoints(), recovered);

        if (recovered) {
            finalResult = "RECOVERED";
            alive = true;
        } else if ("SUCCESS".equals(rollResult)) {
            newSuccesses = successes + 1;
            if (newSuccesses >= SUCCESSES_STABLE && failures < FAILURES_FATAL) {
                finalResult = "STABLE";
            } else {
                finalResult = "SUCCESS";
            }
            alive = failures < FAILURES_FATAL;
            if (!alive) {
                finalResult = "DEAD";
            }
        } else {
            newFailures = failures + 1;
            if (newFailures >= FAILURES_FATAL) {
                finalResult = "DEAD";
                alive = false;
            } else {
                finalResult = "FAILURE";
                alive = true;
            }
        }

        return new DeathSaveOutcome(
                roll,
                naturalTwenty,
                naturalTen,
                modifier,
                recovered,
                failures,
                successes,
                newFailures,
                newSuccesses,
                alive,
                finalResult,
                newCurrent);
    }

    /**
     * The set of common conditions this subset recognises. Each condition carries
     * a short mechanical description. Unknown names are not rejected here;
     * {@link #recognisesCondition(String)} is the single, closed test for a
     * common condition.
     *
     * @param name the canonical condition name (for example {@code "POISONED"})
     * @param description a short mechanical description of the condition
     */
    public enum CommonCondition {
        BLINDED("BLINDED", "A blinded creature can't see and fails ability checks that require sight."),
        DEAFENED("DEAFENED", "A deafened creature can't hear and fails ability checks that require hearing."),
        CHARMED("CHARMED", "A charmed creature won't attack the caller and is inclined to trust them."),
        FRIGHTENED("FRIGHTENED", "A frightened creature has disadvantage on checks while the source is in view and moves away."),
        GRAPPLED("GRAPPLED", "A grappled creature's speed is 0 until the grapple ends."),
        INCAPACITATED("INCAPACITATED", "An incapacitated creature can't act or move and takes damage from any attack."),
        PARALYZED("PARALYZED", "A paralyzed creature is unconscious and immune to damage."),
        PENT("PENT", "A pent creature is restrained and can't move."),
        PHOBICNA("PHOBICNA", "A phobic creature flees the object of its phobia."),
        POISONED("POISONED", "A poisoned creature has disadvantage on attack rolls and ability checks."),
        DAZED("DAZED", "A dazed creature has disadvantage on actions until the end of its next turn."),
        STUNNED("STUNNED", "A stunned creature can't act and takes damage from any attack."),
        UNCONSCIOUS("UNCONSCIOUS", "An unconscious creature is prone, immune to damage, and makes death saving throws."),
        EXHAUSTION("EXHAUSTION", "Exhaustion stacks levels; each level imposes worsening penalties.");

        private final String canonicalName;
        private final String description;

        CommonCondition(String canonicalName, String description) {
            this.canonicalName = canonicalName;
            this.description = description;
        }

        /**
         * @return the canonical condition name (all caps, no spaces)
         */
        public String canonicalName() {
            return canonicalName;
        }

        /**
         * @return a short mechanical description of the condition
         */
        public String description() {
            return description;
        }
    }

    /**
     * The single, closed test for whether a condition name names a common
     * condition recognised by this subset. Matching is case-insensitive and
     * ignores surrounding whitespace.
     *
     * @param name the candidate condition name
     * @return {@code true} when {@code name} names a recognised common condition
     */
    public boolean recognisesCondition(String name) {
        if (name == null) {
            return false;
        }
        String normalized = name.trim().toUpperCase();
        for (CommonCondition condition : CommonCondition.values()) {
            if (condition.name().equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param name the candidate condition name
     * @return the canonical {@link CommonCondition}, or {@code null} when the
     *         name is not a recognised common condition
     */
    public CommonCondition commonCondition(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.trim().toUpperCase();
        for (CommonCondition condition : CommonCondition.values()) {
            if (condition.name().equals(normalized)) {
                return condition;
            }
        }
        return null;
    }

    /**
     * @param current before damage
     * @param after   after damage
     * @return {@code true} when the creature dropped from above {@code 0} to
     *         {@code 0} hit points and therefore begins making death saving
     *         throws
     */
    private static boolean wasReducedToZero(int current, int after) {
        return current > 0 && after == 0;
    }

    private static int hpCurrentAfterDeathSave(Encounter.HitPoints hp, boolean recovered) {
        return recovered ? Math.min(1, hp.getMax()) : hp.getCurrent();
    }

    private static final class HitPointsState {
        private HitPointsState() {
        }

        static void validate(Encounter.HitPoints hp) {
            if (hp == null) {
                throw new IllegalArgumentException("Hit points are required");
            }
            if (hp.getMax() < 0) {
                throw new IllegalArgumentException("Maximum hit points must not be negative");
            }
            if (hp.getCurrent() < 0 || hp.getCurrent() > hp.getMax()) {
                throw new IllegalArgumentException(
                        "Current hit points must be within [0, max]");
            }
            if (hp.getTemporary() < 0) {
                throw new IllegalArgumentException("Temporary hit points must not be negative");
            }
        }
    }

    /**
     * Input for {@link #resolveDamage(DamageInput)}.
     *
     * @param hitPoints the participant's hit points (not mutated by this method)
     * @param amount    the amount of damage to apply
     */
    public record DamageInput(Encounter.HitPoints hitPoints, int amount) {
    }

    /**
     * The auditable outcome of a resolved damage application.
     *
     * @param amount                         the damage declared
     * @param temporaryHitPointsAbsorbed     the temporary hit points that absorbed part of the damage
     * @param damageToHitPoints              the damage that reduced current hit points
     * @param newCurrentHitPoints            the participant's current hit points after the damage
     * @param droppedToZeroHitPoints       {@code true} when the creature fell to {@code 0} hit points
     */
    public record ResolvedDamage(
            int amount,
            int temporaryHitPointsAbsorbed,
            int damageToHitPoints,
            int newCurrentHitPoints,
            boolean droppedToZeroHitPoints) {
    }

    /**
     * Input for {@link #resolveHealing(HealingInput)}.
     *
     * @param hitPoints the participant's hit points (not mutated by this method)
     * @param amount    the amount of healing to apply
     */
    public record HealingInput(Encounter.HitPoints hitPoints, int amount) {
    }

    /**
     * The auditable outcome of a resolved healing application.
     *
     * @param amount            the healing declared
     * @param applied           the hit points actually restored
     * @param newCurrentHitPoints the participant's current hit points after the healing
     */
    public record ResolvedHealing(
            int amount,
            int applied,
            int newCurrentHitPoints) {
    }

    /**
     * Input for {@link #resolveTemporaryHitPoints(TemporaryHitPointsInput)}.
     *
     * @param hitPoints the participant's hit points (not mutated by this method)
     * @param amount    the amount of temporary hit points to add
     */
    public record TemporaryHitPointsInput(Encounter.HitPoints hitPoints, int amount) {
    }

    /**
     * The auditable outcome of a resolved temporary hit points application.
     *
     * @param amount                    the temporary hit points added
     * @param newTemporaryHitPoints     the participant's temporary hit points after the addition
     */
    public record ResolvedTemporaryHitPoints(int amount, int newTemporaryHitPoints) {
    }

    /**
     * Input for {@link #resolveDeathSave(DeathSaveInput)}. The {@code hitPoints}
     * must be at {@code 0} current hit points; {@code rulesProfile} is the
     * encounter's stored, governing profile (never taken from the wire).
     *
     * @param hitPoints               the participant's hit points, which must be at 0
     * @param roll                    the resolved d20 death-save roll
     * @param savingThrowModifier     the saving-throw modifier to add to the roll
     * @param failures                the creature's accumulated failures before this throw
     * @param successes               the creature's accumulated successes before this throw
     * @param rulesProfile            the governing rules profile, or {@code null} to default
     */
    public record DeathSaveInput(
            Encounter.HitPoints hitPoints,
            int roll,
            int savingThrowModifier,
            int failures,
            int successes,
            RulesProfile rulesProfile) {
    }

    /**
     * The auditable outcome of a resolved death saving throw.
     *
     * @param roll              the resolved d20 roll
     * @param naturalTwenty     {@code true} when the natural roll was a 20
     * @param naturalTen        {@code true} when the natural roll was a 10
     * @param modifier          the saving-throw modifier that was applied
     * @param recoveredHp       {@code true} when the creature recovered 1 hit point (natural 20)
     * @param failuresBefore    accumulated failures before this throw
     * @param successesBefore   accumulated successes before this throw
     * @param failuresAfter     accumulated failures after this throw
     * @param successesAfter    accumulated successes after this throw
     * @param alive             {@code true} when the creature is still alive
     * @param result            the resolved result: {@code RECOVERED} / {@code SUCCESS} /
     *                          {@code FAILURE} / {@code STABLE} / {@code DEAD}
     * @param hitPointsAfter    the creature's current hit points after this throw
     */
    public record DeathSaveOutcome(
            int roll,
            boolean naturalTwenty,
            boolean naturalTen,
            int modifier,
            boolean recoveredHp,
            int failuresBefore,
            int successesBefore,
            int failuresAfter,
            int successesAfter,
            boolean alive,
            String result,
            int hitPointsAfter) {
    }
}
