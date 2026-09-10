package com.gamemasterx.server.ai.operation.v2;

import com.gamemasterx.server.ai.operation.OperationSchema;
import com.gamemasterx.server.ai.operation.OperationSchemaException;
import com.gamemasterx.server.ai.operation.OperationSchemaVersion;
import com.gamemasterx.server.ai.operation.OperationType;
import com.gamemasterx.server.ai.operation.ProposedOperation;

import java.util.List;

/**
 * Schema version 2 for {@link ProposedOperation}s.
 *
 * <p>V2 extends V1 with two additional, strictly typed fields and relaxes the
 * justification rule for narrative proposals. A V2 proposal must carry a
 * non-blank {@code operationType} from the V2 supported set (which adds
 * {@link OperationType#NARRATE}), a non-blank {@code targetKind}, a non-blank
 * {@code targetId}, and a non-blank {@code justification} for every type other
 * than {@link OperationType#NARRATE}. It must also carry a {@code priority} in
 * the inclusive range {@code [0, 100]}, and any {@code constraints} must be
 * non-blank strings.</p>
 */
public class ProposedOperationSchemaV2 implements OperationSchema {

    /** Inclusive lower bound for the V2 priority field. */
    public static final int MIN_PRIORITY = 0;
    /** Inclusive upper bound for the V2 priority field. */
    public static final int MAX_PRIORITY = 100;

    private static final List<OperationType> SUPPORTED = List.of(
            OperationType.APPLY_DAMAGE,
            OperationType.APPLY_HEALING,
            OperationType.APPLY_CONDITION,
            OperationType.REMOVE_CONDITION,
            OperationType.MOVE_ACTOR,
            OperationType.CHANGE_RESOURCE,
            OperationType.SET_STATE,
            OperationType.NARRATE);

    @Override
    public OperationSchemaVersion version() {
        return OperationSchemaVersion.V2;
    }

    @Override
    public List<OperationType> supportedOperationTypes() {
        return SUPPORTED;
    }

    @Override
    public void validate(ProposedOperation proposal) {
        if (!SUPPORTED.contains(proposal.operationType())) {
            throw new OperationSchemaException("operationType",
                    "'" + proposal.operationType() + "' is not a supported operation type under schema v2");
        }
        requireNonBlank(proposal.targetKind(), "targetKind");
        requireNonBlank(proposal.targetId(), "targetId");

        if (proposal.operationType() != OperationType.NARRATE) {
            requireNonBlank(proposal.justification(), "justification");
        }

        Integer priority = proposal.priority();
        if (priority == null) {
            throw new OperationSchemaException("priority", "is required under schema v2");
        }
        if (priority < MIN_PRIORITY || priority > MAX_PRIORITY) {
            throw new OperationSchemaException("priority",
                    "must be within [" + MIN_PRIORITY + ", " + MAX_PRIORITY + "] but was " + priority);
        }

        for (int i = 0; i < proposal.constraints().size(); i++) {
            String constraint = proposal.constraints().get(i);
            if (constraint == null || constraint.isBlank()) {
                throw new OperationSchemaException("constraints",
                        "constraint at index " + i + " must be a non-blank string");
            }
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new OperationSchemaException(field, "is required under schema v2");
        }
    }
}
