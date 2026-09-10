package com.gamemasterx.server.encounter.model;

/**
 * The selected {@link RulesProfile} that governs a single encounter.
 *
 * <p>A rules profile selects the <em>supported rules subset</em> that governs
 * how the encounter is resolved and, in doing so, governs every rule-derived
 * behaviour that flows from that subset. The single most visible rule-derived
 * behaviour is <b>critical-hit behaviour</b>: which attack rolls count as
 * critical hits and by what factor their damage is multiplied.</p>
 *
 * <p>The profile is a closed, deterministic enumeration. There is exactly one
 * source of truth for both the rules subset an encounter supports and the
 * critical-hit threshold and damage multiplier that subset prescribes, so the
 * gameplay layer never has to branch on the profile by name.</p>
 *
 * <p>The {@link #supported} flag distinguishes profiles a campaign may actively
 * select from legacy profiles that are retained for reading existing documents
 * but are no longer selectable. This is what makes the set of supported rules
 * subsets explicit: only {@link #isSupported() supported} profiles may govern
 * an encounter that proceeds.</p>
 */
public enum RulesProfile {

    /**
     * The SRD 5.2 (2024) rules subset. Supported and the recommended default.
     * A natural 20 on an attack roll is a critical hit and the damage is dealt
     * at double the normal amount.
     */
    SRD_5_2024(true, "SRD-5.2-2024", 20, 2.0,
            "2024 edition rules; critical hits on a natural 20 at double damage"),

    /**
     * The SRD 5.1 (2014) rules subset. Retained for reading legacy documents but
     * no longer supported, so an encounter using it cannot proceed.
     */
    SRD_5_2014(false, "SRD-5.1-2014", 20, 2.0,
            "Legacy 2014 edition rules; not selectable for new encounters");

    private final boolean supported;
    private final String rulesSubset;
    private final int criticalHitThreshold;
    private final double criticalDamageMultiplier;
    private final String description;

    RulesProfile(boolean supported, String rulesSubset, int criticalHitThreshold,
                 double criticalDamageMultiplier, String description) {
        this.supported = supported;
        this.rulesSubset = rulesSubset;
        this.criticalHitThreshold = criticalHitThreshold;
        this.criticalDamageMultiplier = criticalDamageMultiplier;
        this.description = description;
    }

    /**
     * @return {@code true} when this profile may be selected to govern an
     *         encounter that proceeds; legacy profiles are retained for reading
     *         only and return {@code false}
     */
    public boolean isSupported() {
        return supported;
    }

    /**
     * @return the identifier of the rules subset this profile selects, for
     *         example {@code "SRD-5.2-2024"}
     */
    public String getRulesSubset() {
        return rulesSubset;
    }

    /**
     * @return the minimum attack roll (inclusive) that counts as a critical hit
     *         under this profile's rules subset, for example {@code 20} for a
     *         natural-20 critical rule on a d20
     */
    public int getCriticalHitThreshold() {
        return criticalHitThreshold;
    }

    /**
     * @return the factor by which damage is multiplied when a critical hit is
     *         rolled under this profile, for example {@code 2.0}
     */
    public double getCriticalDamageMultiplier() {
        return criticalDamageMultiplier;
    }

    /**
     * @return a short human-readable description of this profile
     */
    public String getDescription() {
        return description;
    }

    /**
     * Determines whether a d20 attack roll is a critical hit under this
     * profile's rules subset.
     *
     * @param roll the resolved attack roll (typically 1&ndash;20)
     * @return {@code true} when {@code roll} meets or exceeds the profile's
     *         {@link #getCriticalHitThreshold() critical-hit threshold}
     */
    public boolean isCriticalHit(int roll) {
        return roll >= criticalHitThreshold;
    }

    /**
     * Resolves a {@link RulesProfile} from its wire representation. The wire
     * representation may be either the enum constant name (for example
     * {@code "SRD_5_2024"}) or the rules-subset identifier (for example
     * {@code "SRD-5.2-2024"}); matching is case-insensitive.
     *
     * @param wireValue the wire representation of the profile, or {@code null}
     * @return the matching profile
     * @throws IllegalArgumentException when {@code wireValue} does not match any
     *                                  known profile
     */
    public static RulesProfile fromWire(String wireValue) {
        if (wireValue == null) {
            return null;
        }
        String normalized = wireValue.trim().toUpperCase();
        for (RulesProfile profile : values()) {
            if (profile.name().equals(normalized)
                    || profile.rulesSubset.toUpperCase().equals(normalized)) {
                return profile;
            }
        }
        throw new IllegalArgumentException(
                "Unknown rules profile: '" + wireValue + "'");
    }

    /**
     * @return the wire representation to expose on the API contract. This is the
     *         rules-subset identifier (for example {@code "SRD-5.2-2024"}),
     *         rather than the enum constant name, so the wire format is stable
     *         even if constant names are refactored.
     */
    public String toWire() {
        return rulesSubset;
    }
}
