package com.gamemasterx.server.dice;

import com.gamemasterx.server.dice.DiceExpression.Advantage;
import com.gamemasterx.server.gameplay.model.DiceResult;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Resolves {@link DiceExpression}s into auditable {@link DiceResult} records.
 *
 * <p>The roller is the single place where die values are drawn. It supports two
 * selectable, documented {@link RollMode}s:</p>
 *
 * <ul>
 *   <li>{@link RollMode#RANDOM} &ndash; values are drawn from a
 *       {@link java.security.SecureRandom} generator. Rolls are unpredictable
 *       and not reproducible, but every individual die value is recorded on the
 *       resulting {@link DiceResult} so the exact randomness that produced an
 *       outcome is auditable.</li>
 *   <li>{@link RollMode#SEEDED} &ndash; values are drawn from a
 *       {@link java.util.Random} seeded from the caller-supplied {@code seed}.
 *       Rolling the same expression with the same seed always yields an
 *       identical sequence of die values and the same totals, so outcomes can be
 *       verified and replayed.</li>
 * </ul>
 *
 * <p>Each {@link DiceResult} records the individual {@link DiceResult#rolls()}
 * that were drawn for the group, the {@link DiceResult#modifier()} and the
 * resulting {@link DiceResult#total()}. This makes every roll both auditable
 * (the random values are preserved) and, in seeded mode, reproducible.</p>
 */
@Component
public final class DiceRoller {

    /**
     * FNV-1a 64-bit offset basis. Used to derive a stable, cross-platform
     * {@link java.util.Random} seed from a caller-supplied {@code seed} string
     * so that seeded rolls are reproducible regardless of environment.
     */
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    /** FNV-1a 64-bit prime multiplier. */
    private static final long FNV_PRIME = 0x100000001b3L;

    private final RollMode mode;
    private final java.util.Random random;
    /** The caller-supplied seed string, or {@code null} for {@link RollMode#RANDOM}. */
    private final String seed;

    /**
     * Creates a roller for the given mode.
     *
     * @param mode the {@link RollMode}; when {@link RollMode#SEEDED} is used a
     *             non-blank {@code seed} must be supplied
     * @param seed the seed for {@link RollMode#SEEDED}, or {@code null} for
     *             {@link RollMode#RANDOM}
     * @throws IllegalArgumentException when {@link RollMode#SEEDED} is used with a
     *                                  {@code null} or blank seed
     */
    public DiceRoller(RollMode mode, String seed) {
        if (mode == null) {
            throw new IllegalArgumentException("A roll mode is required");
        }
        this.mode = mode;
        this.seed = (mode == RollMode.SEEDED) ? normalizeSeed(seed) : null;
        this.random = buildGenerator(mode, this.seed);
    }

    /**
     * @return the {@link RollMode} this roller was created with
     */
    public RollMode mode() {
        return mode;
    }

    /**
     * @return the caller-supplied seed string, or {@code null} when rolling in
     *         {@link RollMode#RANDOM} mode
     */
    public String seed() {
        return seed;
    }

    /**
     * Rolls every group of the given expression into a {@link DiceResult}.
     *
     * <p>One {@link DiceResult} is produced per {@link DiceExpression#getGroups()
     * group}, in expression order. Each result records the individual
     * {@link DiceResult#rolls()} that were drawn, the group's
     * {@link DiceResult#modifier()}, and the computed
     * {@link DiceResult#total()}. Labels default to each group's canonical text
     * when {@code label} is {@code null} or blank.</p>
     *
     * @param expression the parsed, non-null expression to roll
     * @param label      a human-readable label applied to each result, or
     *                   {@code null}/blank to default to the group text
     * @return the ordered, unmodifiable list of resolved results
     * @throws IllegalArgumentException when {@code expression} is {@code null}
     */
    public List<DiceResult> roll(DiceExpression expression, String label) {
        if (expression == null) {
            throw new IllegalArgumentException("A dice expression is required");
        }
        List<DiceResult> results = new ArrayList<>();
        Instant rolledAt = Instant.now();
        String baseLabel = (label != null && !label.isBlank()) ? label : null;
        for (DiceGroup group : expression.groups()) {
            results.add(rollGroup(group, baseLabel, rolledAt));
        }
        return List.copyOf(results);
    }

    /**
     * Rolls a single {@link DiceGroup}, applying advantage/disadvantage as
     * configured. Each die is resolved to a single kept value which is recorded
     * on the resulting {@link DiceResult}; the group modifier is added to the
     * sum of those values.
     *
     * @param group   the group to roll
     * @param label   a human-readable label, or {@code null} to default to the group text
     * @param rolledAt the timestamp recorded on the result
     * @return the resolved {@link DiceResult} for the group
     */
    private DiceResult rollGroup(DiceGroup group, String label, Instant rolledAt) {
        List<Integer> rolls = new ArrayList<>(group.count());
        for (int i = 0; i < group.count(); i++) {
            rolls.add(resolveDie(group));
        }
        String resolvedLabel = (label != null) ? label : group.text();
        return new DiceResult(
                null,
                resolvedLabel,
                group.text(),
                group.sides(),
                group.count(),
                rolls,
                group.modifier(),
                rolledAt);
    }

    /**
     * Resolves a single die of the given group. For an ordinary die a value in
     * {@code [1, sides]} is drawn. For advantage/disadvantage, two values are
     * drawn and the higher (advantage) or lower (disadvantage) is kept.
     *
     * @param group the group the die belongs to
     * @return the kept die value in {@code [1, group.sides()]}
     */
    private int resolveDie(DiceGroup group) {
        Advantage advantage = group.advantage();
        int sides = group.sides();
        if (advantage == Advantage.ADVANTAGE) {
            int a = next(1, sides);
            int b = next(1, sides);
            return Math.max(a, b);
        }
        if (advantage == Advantage.DISADVANTAGE) {
            int a = next(1, sides);
            int b = next(1, sides);
            return Math.min(a, b);
        }
        return next(1, sides);
    }

    /**
     * Draws the next value in {@code [from, to)} (inclusive {@code from}) from the
     * owning generator. Both bounds are validated against the generator's
     * contract (a bound of zero is not permitted).
     *
     * @param from the inclusive lower bound
     * @param to   the exclusive upper bound (the number of possible outcomes)
     * @return the next drawn value
     */
    private int next(int from, int to) {
        if (to <= 0) {
            throw new DiceExpressionException("expression",
                    "A die must have at least one face");
        }
        return from + random.nextInt(to);
    }

    /**
     * Builds the {@link java.util.Random} backing this roller. Seeded mode uses a
     * {@link java.util.Random} seeded from a stable FNV-1a hash of the seed
     * string, so that the same seed always produces the same sequence across
     * environments. Random mode uses a {@link java.util.Random} seeded from a
     * fresh {@link SecureRandom} long, so the sequence is unpredictable yet not
     * reproducible &ndash; the individual die values are still recorded on each
     * {@link DiceResult} for auditability.
     */
    private static java.util.Random buildGenerator(RollMode mode, String seed) {
        if (mode == RollMode.SEEDED) {
            return new java.util.Random(hashSeed(seed));
        }
        long seedValue = new SecureRandom().nextLong();
        return new java.util.Random(seedValue);
    }

    /**
     * @param seed the raw seed
     * @return the trimmed seed, or throws when it is {@code null} or blank
     */
    private static String normalizeSeed(String seed) {
        if (seed == null) {
            throw new IllegalArgumentException("A seed is required for seeded rolls");
        }
        String trimmed = seed.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("A seed is required for seeded rolls");
        }
        return trimmed;
    }

    /**
     * Derives a stable {@code long} seed from a seed string using the FNV-1a
     * 64-bit hash. The computation is pure integer arithmetic, so it is
     * deterministic and identical across platforms &ndash; a property the seeded
     * reproducibility guarantee relies on.
     *
     * @param seed the non-blank seed string
     * @return the 64-bit FNV-1a hash of the seed
     */
    static long hashSeed(String seed) {
        Objects.requireNonNull(seed, "seed");
        byte[] bytes = seed.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        long hash = FNV_OFFSET_BASIS;
        for (byte b : bytes) {
            hash ^= (b & 0xff);
            hash *= FNV_PRIME;
        }
        return hash;
    }

    /**
     * @return the caller-supplied seed string when this roller is in
     *         {@link RollMode#SEEDED} mode, or {@code null} otherwise
     */
    public String effectiveSeed() {
        return seed;
    }
}
