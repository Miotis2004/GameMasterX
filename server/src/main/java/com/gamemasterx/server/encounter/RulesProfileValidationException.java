package com.gamemasterx.server.encounter;

/**
 * Thrown when the {@link com.gamemasterx.server.encounter.model.RulesProfile} of an
 * encounter is missing or otherwise not permitted before the encounter is
 * allowed to proceed.
 *
 * <p>This exception is intentionally <em>not</em> an
 * {@link IllegalArgumentException} so that it is clearly distinguishable from the
 * {@code NOT_FOUND} {@code IllegalArgumentException} used elsewhere in
 * {@code com.gamemasterx.server.encounter.controller.EncounterController}. A
 * dedicated type lets the controller report profile-validation failures with a
 * {@code 400 BAD_REQUEST} response and a clear message.</p>
 */
public class RulesProfileValidationException extends IllegalStateException {

    public RulesProfileValidationException(String message) {
        super(message);
    }
}
