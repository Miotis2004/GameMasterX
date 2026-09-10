package com.gamemasterx.server.ai.operation;

import java.util.List;

/**
 * A single, versioned schema for {@link ProposedOperation}s.
 *
 * <p>Each version of the proposed-operation contract implements this interface
 * and owns the authoritative rule set for proposals that declare that version.
 * Schemas are defined exclusively in the backend; they are never serialised to
 * the browser as editable inputs, and they cannot be mutated at runtime. The
 * {@link OperationSchemaRegistry} holds one schema per version and lets a
 * proposal be validated against whichever schema its version selects, so
 * multiple versions can coexist.</p>
 *
 * <p>Implementations must be {@link #validate(ProposedOperation) stateless} and
 * thread-safe: the same schema instance validates every proposal for its
 * version.</p>
 */
public interface OperationSchema {

    /**
     * @return the schema version this implementation defines
     */
    OperationSchemaVersion version();

    /**
     * @return the ordered list of {@link OperationType}s this version accepts.
     * Proposals naming any other type are rejected during validation.
     */
    List<OperationType> supportedOperationTypes();

    /**
     * Validate a proposal against this version's rules.
     *
     * @param proposal the proposal to validate; already known to declare
     *                 {@link #version()}
     * @throws OperationSchemaException if the proposal violates any rule of
     *                                  this schema
     */
    void validate(ProposedOperation proposal);
}
