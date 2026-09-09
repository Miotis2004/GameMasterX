package com.gamemasterx.server.adventure;

/**
 * Thrown when a local adventure package fails structural validation, identifier
 * validation, or safe-extraction checks during import.
 *
 * <p>This is deliberately <em>not</em> an {@link IllegalArgumentException} so
 * that the global exception handler can report import validation failures with a
 * {@code 400 BAD_REQUEST} response and an optional offending field or entry name.
 * Distinct failure classes are used elsewhere in the adventure subsystem:
 * {@link AdventureImportConflictException} models identifier collisions against
 * already-persisted adventures (mapped to {@code 409 Conflict}).</p>
 */
public class AdventurePackageImportException extends IllegalStateException {

    private final String location;

    public AdventurePackageImportException(String message) {
        this(null, message);
    }

    public AdventurePackageImportException(String location, String message) {
        super(message);
        this.location = location;
    }

    /**
     * @return the offending field, entry name, or path that triggered the
     * failure, or {@code null} when the failure is not tied to a specific
     * location within the package
     */
    public String getLocation() {
        return location;
    }
}
