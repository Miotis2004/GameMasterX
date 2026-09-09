package com.gamemasterx.server.adventure;

/**
 * Thrown when an adventure package targets an identifier ({@code id}) that is
 * already used by a persisted adventure, which would otherwise cause a silent
 * overwrite.
 *
 * <p>Import is a create-by-default operation: importing a package whose
 * {@code id} collides with an existing adventure is rejected with a
 * {@code 409 Conflict}. An explicit {@code overwrite} request opt-out turns the
 * collision into a non-silent, audited update. This exception is deliberately
 * distinct from {@link AdventurePackageImportException} so the global exception
 * handler can map it to {@code 409 Conflict} rather than {@code 400
 * BAD_REQUEST}.</p>
 */
public class AdventureImportConflictException extends IllegalStateException {

    private final String identifier;

    public AdventureImportConflictException(String identifier) {
        this(identifier, "An adventure with id '" + identifier + "' already exists; refusing to overwrite");
    }

    public AdventureImportConflictException(String identifier, String message) {
        super(message);
        this.identifier = identifier;
    }

    /**
     * @return the colliding identifier, or {@code null} when unknown
     */
    public String getIdentifier() {
        return identifier;
    }
}
