package com.gamemasterx.server.ai.operation;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable, typed record describing a single operation that an AI
 * proposes to apply to encounter state.
 *
 * <p>A proposal is the transport boundary between an {@link
 * com.gamemasterx.server.ai.AiProvider} completion and the backend's validation
 * layer. The proposal always carries the {@link #schemaVersion()} it conforms
 * to, so the backend knows exactly which {@link OperationSchema} is
 * authoritative for validating it.</p>
 *
 * <p>The field set is fixed and typed at the language level. Every field is
 * final, the collections are defensively copied and returned unmodifiable, and
 * the constructor refuses to build a proposal with a missing or blank schema
 * version or operation type. A proposal that reaches the backend has therefore
 * already been constructed through a single, controlled path.</p>
 *
 * <p>The schema definitions themselves live exclusively in the backend and are
 * never sent to the browser as editable inputs. A caller may reference a
 * version and operation type, but it cannot rewrite what a version means: the
 * {@link OperationSchemaRegistry} and {@link ProposedOperationValidator} are the
 * sole authority for validating a proposal.</p>
 */
public record ProposedOperation(

        /**
         * The {@link OperationSchemaVersion} this proposal was built against.
         * Non-blank and resolved to a backend-owned schema for validation.
         */
        String schemaVersion,

        /**
         * The kind of change being proposed, from the closed set declared by
         * {@link OperationType}.
         */
        OperationType operationType,

        /**
         * The kind of state being changed, for example {@code "actor"},
         * {@code "combatant"} or {@code "resource"}.
         */
        String targetKind,

        /**
         * The identifier of the subject whose state is being changed.
         */
        String targetId,

        /**
         * A human-readable label for the subject, or {@code null} when
         * unavailable.
         */
        String targetName,

        /**
         * Version-specific structured parameters. Immutable; keys are
         * case-sensitive strings, values are opaque to the transport layer.
         */
        Map<String, Object> parameters,

        /**
         * A human-readable justification for the proposed change, or
         * {@code null} when the schema does not require one.
         */
        String justification,

        /**
         * An optional ordering hint. Present only under schema version 2, and
         * only when the schema that validated the proposal permits it.
         */
        Integer priority,

        /**
         * An optional ordered list of natural-language constraints. Present
         * only under schema version 2.
         */
        List<String> constraints) {

    /**
     * Normalising constructor. Copies collections defensively and rejects a
     * proposal that is missing the fields every schema treats as mandatory.
     *
     * @param schemaVersion the schema version this proposal conforms to; must
     *                      be a non-blank wire value
     * @param operationType the kind of change; must not be {@code null}
     * @param targetKind    the kind of state changed; may be {@code null}
     *                      (per-schema validation decides whether it is
     *                      mandatory)
     * @param targetId      the subject identifier; may be {@code null}
     * @param targetName    a label for the subject, or {@code null}
     * @param parameters    structured parameters, or {@code null}
     * @param justification a justification, or {@code null}
     * @param priority      an ordering hint, or {@code null}
     * @param constraints   an ordered constraint list, or {@code null}
     */
    public ProposedOperation(String schemaVersion, OperationType operationType, String targetKind,
                             String targetId, String targetName, Map<String, Object> parameters,
                             String justification, Integer priority, List<String> constraints) {
        if (schemaVersion == null || schemaVersion.isBlank()) {
            throw new IllegalArgumentException(
                    "A proposed operation must declare a schema version");
        }
        if (operationType == null) {
            throw new IllegalArgumentException(
                    "A proposed operation must declare an operation type");
        }
        this.schemaVersion = schemaVersion.trim();
        this.operationType = operationType;
        this.targetKind = targetKind;
        this.targetId = targetId;
        this.targetName = targetName;
        this.parameters = (parameters != null) ? Map.copyOf(parameters) : Map.of();
        this.justification = justification;
        this.priority = priority;
        this.constraints = (constraints != null) ? List.copyOf(constraints) : List.of();
    }

    /**
     * @return {@code true} when this proposal carries no structured parameters
     */
    public boolean hasNoParameters() {
        return parameters.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProposedOperation other)) return false;
        return schemaVersion.equals(other.schemaVersion)
                && operationType == other.operationType
                && Objects.equals(targetKind, other.targetKind)
                && Objects.equals(targetId, other.targetId)
                && Objects.equals(targetName, other.targetName)
                && Objects.equals(parameters, other.parameters)
                && Objects.equals(justification, other.justification)
                && Objects.equals(priority, other.priority)
                && Objects.equals(constraints, other.constraints);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemaVersion, operationType, targetKind, targetId, targetName,
                parameters, justification, priority, constraints);
    }

    @Override
    public String toString() {
        return "ProposedOperation[" +
                "schemaVersion=" + schemaVersion +
                ", operationType=" + operationType +
                ", targetKind=" + targetKind +
                ", targetId=" + targetId +
                ", justification=" + justification +
                ", priority=" + priority +
                ", constraints=" + constraints +
                ']';
    }
}
