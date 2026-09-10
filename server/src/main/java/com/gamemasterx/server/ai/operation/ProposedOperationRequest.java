package com.gamemasterx.server.ai.operation;

import java.util.List;
import java.util.Map;

/**
 * The request boundary for a single AI-proposed operation submitted to the
 * backend for validation and, only when it is valid, execution.
 *
 * <p>This mirrors the fields of an immutable {@link ProposedOperation} closely
 * enough to be deserialized directly from JSON, but it is deliberately a
 * separate type: the transport contract the API accepts is not the same object
 * the validation layer operates on. Every field is optional at the transport
 * level; {@link ProposedOperation} itself refuses to build a proposal without a
 * schema version and operation type, so a proposal that is missing one of those
 * mandatory fields is rejected during construction rather than silently.</p>
 *
 * <p>The {@code operationType} is carried as its raw wire string here and is
 * resolved to the backend-authoritative {@link OperationType} by the service that
 * consumes this request, so an unrecognised type is reported clearly.</p>
 */
public record ProposedOperationRequest(

        /** The {@link OperationSchemaVersion} wire label the proposal targets. */
        String schemaVersion,

        /** The {@link OperationType} wire name, for example {@code "APPLY_DAMAGE"}. */
        String operationType,

        /** The kind of state being changed, for example {@code "actor"}. */
        String targetKind,

        /** The identifier of the subject whose state is being changed. */
        String targetId,

        /** A human-readable label for the subject, or {@code null} when unavailable. */
        String targetName,

        /** Version-specific structured parameters, or an empty object when absent. */
        Map<String, Object> parameters,

        /** A human-readable justification, or {@code null} where the schema permits it. */
        String justification,

        /** A V2-only ordering hint, or {@code null} for schema version 1. */
        Integer priority,

        /** A V2-only ordered list of natural-language constraints. */
        List<String> constraints) {
}
