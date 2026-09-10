package com.gamemasterx.server.ai.operation.v1;

import com.gamemasterx.server.ai.operation.OperationSchema;
import com.gamemasterx.server.ai.operation.OperationSchemaException;
import com.gamemasterx.server.ai.operation.OperationSchemaVersion;
import com.gamemasterx.server.ai.operation.OperationType;
import com.gamemasterx.server.ai.operation.ProposedOperation;

import java.util.List;

/**
 * Schema version 1 for {@link ProposedOperation}s.
 *
 * <p>A V1 proposal must carry a non-blank {@code operationType} from the V1
 * supported set, a non-blank {@code targetKind}, a non-blank {@code targetId}
 * and a non-blank {@code justification}. The version-2-only fields
 * ({@code priority} and {@code constraints}) are forbidden under this schema:
 * their presence is a violation, which keeps the two versions cleanly
 * separated.</p>
 */
public class ProposedOperationSchemaV1 implements OperationSchema {

    private static final List<OperationType> SUPPORTED = List.of(
            OperationType.APPLY_DAMAGE,
            OperationType.APPLY_HEALING,
            OperationType.APPLY_CONDITION,
            OperationType.REMOVE_CONDITION,
            OperationType.MOVE_ACTOR,
            OperationType.CHANGE_RESOURCE,
            OperationType.SET_STATE);

    @Override
    public OperationSchemaVersion version() {
        return OperationSchemaVersion.V1;
    }

    @Override
    public List<OperationType> supportedOperationTypes() {
        return SUPPORTED;
    }

    @Override
    public void validate(ProposedOperation proposal) {
        if (!SUPPORTED.contains(proposal.operationType())) {
            throw new OperationSchemaException("operationType",
                    "'" + proposal.operationType() + "' is not a supported operation type under schema v1");
        }
        requireNonBlank(proposal.targetKind(), "targetKind");
        requireNonBlank(proposal.targetId(), "targetId");
        requireNonBlank(proposal.justification(), "justification");

        if (proposal.priority() != null) {
            throw new OperationSchemaException("priority",
                    "the priority field is not permitted under schema v1");
        }
        if (!proposal.constraints().isEmpty()) {
            throw new OperationSchemaException("constraints",
                    "the constraints field is not permitted under schema v1");
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new OperationSchemaException(field, "is required under schema v1");
        }
    }
}
