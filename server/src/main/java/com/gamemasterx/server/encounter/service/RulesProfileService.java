package com.gamemasterx.server.encounter.service;

import com.gamemasterx.server.encounter.RulesProfileValidationException;
import com.gamemasterx.server.encounter.model.Encounter;
import com.gamemasterx.server.encounter.model.RulesProfile;
import org.springframework.stereotype.Service;

/**
 * Application service that owns the selected {@link RulesProfile} for an
 * encounter.
 *
 * <p>The profile performs two jobs, and this service is the single authority
 * for both:</p>
 *
 * <ul>
 *   <li><b>Selecting the supported rules subset.</b> The profile names the
 *       rules subset an encounter follows (its {@link RulesProfile#getRulesSubset()
 *       rules subset identifier}) and, through that, every rule-derived
 *       behaviour such as critical hits.</li>
 *   <li><b>Governing critical-hit behaviour.</b> Given a profile and an attack
 *       roll, {@link #resolveCriticalHit(int)} deterministically reports whether
 *       the roll is a critical hit and the damage multiplier that the selected
 *       profile prescribes.</li>
 * </ul>
 *
 * <p>The profile is validated server-side by {@link #validateEncounterCanProceed(Encounter)}
 * before an encounter is allowed to proceed (start). A missing profile, or a
 * profile whose subset is no longer supported, is rejected with a
 * {@link RulesProfileValidationException}, which the controller maps to a
 * {@code 400 BAD_REQUEST}.</p>
 *
 * <p>Every method here is deterministic: the same profile and inputs always
 * produce the same verdict.</p>
 */
@Service
public class RulesProfileService {

    /**
     * The default profile applied when an encounter is created without
     * explicitly selecting one. Kept here, in one place, so the default never
     * drifts from the service that validates against it.
     */
    public static final RulesProfile DEFAULT_PROFILE = RulesProfile.SRD_5_2024;

    /**
     * A single, immutable resolution of a critical hit under a given profile.
     *
     * @param critical       {@code true} when the attack roll met the profile's
     *                       critical-hit threshold
     * @param multiplier     the damage multiplier prescribed by the profile,
     *                       for example {@code 2.0}
     * @param roll           the attack roll that was resolved
     * @param threshold      the profile's critical-hit threshold
     * @param profile        the profile that governed the resolution
     */
    public record CriticalHitOutcome(
            boolean critical,
            double multiplier,
            int roll,
            int threshold,
            RulesProfile profile) {
    }

    /**
     * Resolves the {@link RulesProfile} from a wire representation, such as the
     * value supplied in an {@code EncounterCreateRequest}. Accepts either the
     * enum constant name or the rules-subset identifier, either case.
     *
     * @param wireValue the wire value, or {@code null} to fall back to
     *                  {@link #DEFAULT_PROFILE}
     * @return the resolved profile, never {@code null}
     * @throws IllegalArgumentException when {@code wireValue} is present but does
     *                                  not match a known profile
     */
    public RulesProfile resolve(String wireValue) {
        if (wireValue == null || wireValue.isBlank()) {
            return DEFAULT_PROFILE;
        }
        return RulesProfile.fromWire(wireValue);
    }

    /**
     * Validates that the encounter carries a selected rules profile that is
     * permitted to govern an encounter that proceeds. This is the server-side
     * gate run before an encounter starts.
     *
     * <p>The encounter may not proceed when it has no profile, or when its
     * profile names a subset that is no longer supported (for example a legacy
     * profile retained only for reading).</p>
     *
     * @param encounter the encounter to gate
     * @throws RulesProfileValidationException when the profile is missing or not
     *                                         supported
     */
    public void validateEncounterCanProceed(Encounter encounter) {
        RulesProfile profile = encounter.getRulesProfile();
        if (profile == null) {
            throw new RulesProfileValidationException(
                    "A rules profile must be selected before an encounter can proceed");
        }
        if (!profile.isSupported()) {
            throw new RulesProfileValidationException(
                    "The selected rules profile '" + profile.getRulesSubset()
                            + "' is no longer supported; select a supported profile before starting the encounter");
        }
    }

    /**
     * Asserts that a critical hit may be applied under the given profile. Critical
     * hits are only permitted when the selected rules profile is a supported one;
     * a missing or unsupported (for example legacy) profile is rejected. This is
     * the server-side gate that guards the point at which critical hits are
     * actually applied, in addition to the start-of-encounter gate.
     *
     * @param profile the profile that must permit critical hits
     * @throws RulesProfileValidationException when the profile is missing or not
     *                                         supported
     */
    public void assertCriticalHitPermitted(RulesProfile profile) {
        if (profile == null) {
            throw new RulesProfileValidationException(
                    "A rules profile must be selected before critical hits can be applied");
        }
        if (!profile.isSupported()) {
            throw new RulesProfileValidationException(
                    "Critical hits are only permitted under a supported rules profile; "
                            + "the selected profile '" + profile.getRulesSubset()
                            + "' is no longer supported");
        }
    }

    /**
     * Resolves whether an attack roll is a critical hit under the encounter's
     * currently selected profile, and the damage multiplier that profile
     * prescribes.
     *
     * @param encounter the encounter whose profile governs the resolution
     * @param roll      the resolved attack roll
     * @return the {@link CriticalHitOutcome}
     * @throws RulesProfileValidationException when the encounter has no
     *                                         supported profile
     */
    public CriticalHitOutcome resolveCriticalHit(Encounter encounter, int roll) {
        RulesProfile profile = encounter.getRulesProfile();
        if (profile == null) {
            throw new RulesProfileValidationException(
                    "A rules profile must be selected before critical hits can be resolved");
        }
        return resolveCriticalHit(profile, roll);
    }

    /**
     * Resolves whether an attack roll is a critical hit under a specific
     * profile.
     *
     * @param profile the profile that governs the resolution
     * @param roll    the resolved attack roll
     * @return the {@link CriticalHitOutcome}
     * @throws IllegalArgumentException when {@code profile} is {@code null}
     */
    public CriticalHitOutcome resolveCriticalHit(RulesProfile profile, int roll) {
        if (profile == null) {
            throw new IllegalArgumentException("Rules profile must not be null");
        }
        boolean critical = profile.isCriticalHit(roll);
        return new CriticalHitOutcome(
                critical,
                profile.getCriticalDamageMultiplier(),
                roll,
                profile.getCriticalHitThreshold(),
                profile);
    }
}
