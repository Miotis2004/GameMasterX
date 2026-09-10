package com.gamemasterx.server.ai.operation;

import com.gamemasterx.server.ai.operation.OperationSchema;
import org.springframework.stereotype.Service;

/**
 * Single backend entry point for validating a {@link ProposedOperation}.
 *
 * <p>A proposal is validated strictly against the {@link OperationSchema} that
 * matches the {@link OperationSchemaVersion} it declares. The schema
 * definitions are owned entirely by this backend layer - they are never
 * exposed to the browser as editable or mutable inputs - so a proposal can only
 * ever be checked against the rules the {@link OperationSchemaRegistry}
 * authorises.</p>
 *
 * <p>The same validator instance validates proposals under every registered
 * version at once, so version 1 and version 2 proposals can be validated
 * simultaneously as each version is adopted.</p>
 */
@Service
public class ProposedOperationValidator {

    private final OperationSchemaRegistry registry;

    /**
     * @param registry the backend registry of versioned schemas
     */
    public ProposedOperationValidator(OperationSchemaRegistry registry) {
        this.registry = registry;
    }

    /**
     * Resolve and validate a proposal.
     *
     * <p>The {@code schemaVersion} carried on the proposal selects the schema;
     * the selected schema is the sole authority for what makes the proposal
     * valid.</p>
     *
     * @param proposal the proposal to validate
     * @throws OperationSchemaException if the proposal violates the rules of
     *                                  its declared schema, or declares an
     *                                  unsupported/unknown version
     */
    public void validate(ProposedOperation proposal) {
        OperationSchemaVersion version = OperationSchemaVersion.fromWire(proposal.schemaVersion());
        OperationSchema schema = registry.resolve(version);
        schema.validate(proposal);
    }

    /**
     * @return the schema that governs a proposal built against the given
     * version
     * @param version the schema version
     */
    public OperationSchema schemaFor(OperationSchemaVersion version) {
        return registry.resolve(version);
    }
}
