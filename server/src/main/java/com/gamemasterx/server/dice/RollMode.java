package com.gamemasterx.server.dice;

/**
 * The source of randomness used when resolving a {@link DiceExpression}.
 *
 * <p>Exactly one mode is applied to a single roll request. The mode is part of
 * the API contract: callers choose {@link #RANDOM} for unpredictable,
 * session-scoped rolls or {@link #SEEDED} for a roll that reproduces an
 * identical sequence of die values for a given seed, so outcomes can be
 * verified and replayed.</p>
 *
 * <p>The wire representation is the lowercase name of the constant (for example
 * {@code "random"} or {@code "seeded"}), as documented in the dice API
 * contract.</p>
 */
public enum RollMode {

    /**
     * Unpredictable, auditable randomness sourced from
     * {@link java.security.SecureRandom}. Each die value is drawn from a
     * cryptographically strong generator and the individual values are recorded
     * on the resulting {@link com.gamemasterx.server.gameplay.model.DiceResult}
     * records. No seed is used and the result is not reproducible.
     */
    RANDOM("random"),

    /**
     * Deterministic, reproducible randomness. The generator is seeded from a
     * caller-supplied {@code seed}, so rolling the same expression with the same
     * seed always yields an identical sequence of die values and the same totals.
     */
    SEEDED("seeded");

    private final String wire;

    RollMode(String wire) {
        this.wire = wire;
    }

    /**
     * @return the canonical lower-case wire representation used in the API
     */
    public String wire() {
        return wire;
    }

    /**
     * Parses a case-insensitive wire value into a {@link RollMode}.
     *
     * @param wire the wire value, for example {@code "random"} or {@code "SEEDED"}
     * @return the matching {@link RollMode}
     * @throws DiceExpressionException when {@code wire} is {@code null}, blank,
     *                                 or does not match a known mode
     */
    public static RollMode fromWire(String wire) {
        if (wire == null) {
            throw new DiceExpressionException("mode", "A roll mode is required");
        }
        String normalized = wire.trim().toLowerCase();
        for (RollMode mode : values()) {
            if (mode.wire.equals(normalized)) {
                return mode;
            }
        }
        throw new DiceExpressionException("mode",
                "Unknown roll mode '" + wire + "'; expected 'random' or 'seeded'");
    }
}
