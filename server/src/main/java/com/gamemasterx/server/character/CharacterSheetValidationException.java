package com.gamemasterx.server.character;

/**
 * Thrown when a character sheet fails the deterministic SRD 5.x validation rules
 * for ability scores, modifiers, proficiency, hit points, armor class, resources
 * or inventory quantities.
 *
 * <p>This exception is intentionally <em>not</em> an {@link IllegalArgumentException}
 * so that it is not confused with the {@code NOT_FOUND} {@code IllegalArgumentException}
 * used elsewhere in {@code com.gamemasterx.server.character.controller.CharacterController}.
 * A dedicated type lets the global exception handler report validation failures with
 * a {@code 400 BAD_REQUEST} response and an optional offending field name.</p>
 */
public class CharacterSheetValidationException extends IllegalStateException {

    private final String field;

    public CharacterSheetValidationException(String message) {
        this(null, message);
    }

    public CharacterSheetValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
