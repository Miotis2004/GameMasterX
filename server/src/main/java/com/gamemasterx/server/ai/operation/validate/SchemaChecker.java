package com.gamemasterx.server.ai.operation.validate;

import org.springframework.stereotype.Component;
import com.gamemasterx.server.ai.operation.OperationSchema;
import com.gamemasterx.server.ai.operation.OperationSchemaException;
import com.gamemasterx.server.ai.operation.OperationSchemaRegistry;
import com.gamemasterx.server.ai.operation.OperationSchemaVersion;
import com.gamemasterx.server.ai.operation.ProposedOperation;

import java.util.Optional;

/**
 * Enforces the {@link ValidationDimension#SCHEMA} dimension.
 *
 * <p>The schema is the sole authority for what makes a proposal well-formed for
 * its declared version. This checker delegates to the backend-owned
 * {@link OperationSchema} selected by the proposal's {@code schemaVersion} and
 * converts any {@link OperationSchemaException} into a
 * {@link OperationValidationError}. An unknown or blank version is reported as a
 * schema failure.</p>
 */

@Component
public class SchemaChecker implements OperationChecker {

    private final OperationSchemaRegistry registry;

    /**
     * @param registry the backend registry of versioned schemas
     */
    public SchemaChecker(OperationSchemaRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ValidationDimension dimension() {
        return ValidationDimension.SCHEMA;
    }

    @Override
    public Optional<OperationValidationError> check(ProposedOperation proposal, ProposedOperationContext context) {
        OperationSchemaVersion version;
        try {
            version = OperationSchemaVersion.fromWire(proposal.schemaVersion());
        } catch (IllegalArgumentException invalid) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.SCHEMA, "schemaVersion", invalid.getMessage()));
        }
        try {
            registry.resolve(version).validate(proposal);
            return Optional.empty();
        } catch (OperationSchemaException ex) {
            return Optional.of(new OperationValidationError(
                    ValidationDimension.SCHEMA, ex.getField(), ex.getMessage()));
        }
    }
}
