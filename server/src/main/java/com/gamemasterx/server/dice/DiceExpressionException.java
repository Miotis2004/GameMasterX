package com.gamemasterx.server.dice;

/**
 * Thrown when a dice expression cannot be parsed into a valid
 * {@link DiceExpression}. This is a <em>deterministic</em>, input-driven
 * validation failure: the same malformed input always fails in the same way,
 * with the same message, so callers can rely on stable, reproducible errors.
 *
 * <p>This exception is intentionally <b>not</b> an {@link IllegalArgumentException}
 * so that it is reported distinctly from the generic {@code BAD_REQUEST} handled
 * for arbitrary {@link IllegalArgumentException}s. A dedicated type lets the
 * global exception handler map the failure to a {@code 400 BAD_REQUEST} response
 * tagged with the offending {@link #field()}, matching the shape used by bean
 * validation failures.</p>
 */
public class DiceExpressionException extends IllegalStateException {

    private final String field;

    /**
     * @param field the name of the offending input field, for example
     *              {@code "expression"}
     * @param message the human-readable description of why parsing failed
     */
    public DiceExpressionException(String field, String message) {
        super(message);
        this.field = field;
    }

    /**
     * @return the name of the input field that failed to parse, never
     * {@code null}
     */
    public String getField() {
        return field;
    }
}
