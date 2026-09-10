package com.gamemasterx.server.ai.operation;

/**
 * The recognised schema versions for {@link ProposedOperation}s.
 *
 * <p>Each entry is a distinct, self-describing schema version. A schema version
 * is an explicit, first-class attribute of a proposal: the proposal always
 * carries the {@link #wire()} value it was built against, and the backend
 * resolves that value to the matching {@link com.gamemasterx.server.ai.operation.OperationSchema}
 * for validation.</p>
 *
 * <p>Multiple versions coexist: the {@link OperationSchemaRegistry} holds one
 * schema per version and validates a proposal against the schema that matches
 * the version the proposal declares. When the shape of a proposal evolves, a
 * new version is introduced alongside the old one rather than replacing it, so
 * proposals captured under different versions can be validated simultaneously.</p>
 */
public enum OperationSchemaVersion {

    /**
     * The first released schema. Requires a non-blank {@code operationType},
     * {@code targetKind}, {@code targetId} and a non-blank justification, and
     * forbids the V2-only {@code priority} and {@code constraints} fields.
     */
    V1("1"),

    /**
     * The second schema, extending V1 with a bounded {@code priority} and a
     * list of {@code constraints}, and exposing the {@code NARRATE} operation
     * type.
     */
    V2("2");

    private final String wire;

    OperationSchemaVersion(String wire) {
        this.wire = wire;
    }

    /**
     * @return the wire representation of this version, used verbatim on the
     * proposal (for example {@code "1"} or {@code "2"})
     */
    public String wire() {
        return wire;
    }

    /**
     * Resolve a wire value to its {@link OperationSchemaVersion}.
     *
     * @param wire the version label carried on a proposal
     * @return the matching version
     * @throws IllegalArgumentException if {@code wire} is {@code null}, blank,
     *                                or does not correspond to a recognised
     *                                version
     */
    public static OperationSchemaVersion fromWire(String wire) {
        if (wire == null || wire.isBlank()) {
            throw new IllegalArgumentException("A proposed operation must declare a schema version");
        }
        for (OperationSchemaVersion version : values()) {
            if (version.wire.equals(wire.trim())) {
                return version;
            }
        }
        throw new IllegalArgumentException("Unsupported operation schema version: '" + wire + "'");
    }
}
