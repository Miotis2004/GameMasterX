package com.gamemasterx.server.gameplay.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable record describing a single proposed change to gameplay state.
 *
 * <p>A mutation is the atomic unit of state change that the game master
 * reviews. It captures the <em>inputs</em> (which subject and what kind of
 * change), the state <em>before</em> the change and the state <em>after</em>
 * it. The decision to accept or reject a mutation is recorded separately in an
 * {@link Audit} entry; this proposal record itself is decision-free so the same
 * proposal can be audited multiple times as context changes.</p>
 *
 * <p>{@link #before} and {@link #after} are captured as structured values that
 * are serialised to their JSON representation for persistence, so the exact
 * state transitions are preserved verbatim in the audit log.</p>
 */
public record Mutation(
        /** Stable identifier, or {@code null} when transient. */
        String id,

        /**
         * The kind of state this mutation changes, for example
         * {@code "hitPoints"}, {@code "resource"}, {@code "condition"} or
         * {@code "resourcePool"}.
         */
        String subjectType,

        /** Identifier of the subject whose state is being changed. */
        String subjectId,

        /** Human-readable description of the proposed change. */
        String description,

        /** The structured state before the change, serialised to JSON for storage. */
        Object before,

        /** The structured state after the change, serialised to JSON for storage. */
        Object after,

        /** Timestamp of when the mutation was proposed, or {@code null} when transient. */
        Instant proposedAt) {

    /**
     * Convenience factory that normalises the timestamp.
     *
     * @param id            stable identifier, or {@code null}
     * @param subjectType   the kind of state being changed; must not be blank
     * @param subjectId     the subject's identifier; must not be blank
     * @param description   a human-readable description; must not be blank
     * @param before        the structured before state, or {@code null}
     * @param after         the structured after state, or {@code null}
     * @param proposedAt    the time of proposal, or {@code Instant.now()}
     */
    public Mutation(String id, String subjectType, String subjectId, String description,
                    Object before, Object after, Instant proposedAt) {
        this.id = id;
        if (subjectType == null || subjectType.isBlank()) {
            throw new IllegalArgumentException("Mutation subjectType must not be blank");
        }
        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("Mutation subjectId must not be blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Mutation description must not be blank");
        }
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.description = description;
        this.before = before;
        this.after = after;
        this.proposedAt = (proposedAt != null) ? proposedAt : Instant.now();
    }

    /**
     * @return the structured before state rendered as JSON, or {@code null}
     */
    public String beforeJson() {
        return Json.encode(before);
    }

    /**
     * @return the structured after state rendered as JSON, or {@code null}
     */
    public String afterJson() {
        return Json.encode(after);
    }

    /**
     * @return {@code true} when the before and after states are equal (a
     * no-op mutation)
     */
    public boolean isNoOp() {
        return Objects.equals(before, after);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Mutation other)) return false;
        return Objects.equals(id, other.id)
                && Objects.equals(subjectType, other.subjectType)
                && Objects.equals(subjectId, other.subjectId)
                && Objects.equals(description, other.description)
                && Objects.equals(before, other.before)
                && Objects.equals(after, other.after)
                && Objects.equals(proposedAt, other.proposedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, subjectType, subjectId, description, before, after, proposedAt);
    }

    @Override
    public String toString() {
        return "Mutation[" +
                "id=" + id +
                ", subjectType=" + subjectType +
                ", subjectId=" + subjectId +
                ", description=" + description +
                ", before=" + beforeJson() +
                ", after=" + afterJson() + ']';
    }

    /**
     * Minimal JSON encoding helper for structured before/after values. Uses a
     * simple, dependency-free encoder sufficient for the nested maps, lists,
     * strings, numbers and booleans that represent gameplay state snapshots.
     */
    static final class Json {
        private Json() {
        }

        static String encode(Object value) {
            StringBuilder sb = new StringBuilder();
            writeValue(sb, value);
            return sb.toString();
        }

        private static void writeValue(StringBuilder sb, Object value) {
            if (value == null) {
                sb.append("null");
            } else if (value instanceof String s) {
                writeString(sb, s);
            } else if (value instanceof Boolean || value instanceof Number) {
                sb.append(value.toString());
            } else if (value instanceof Map<?,?> map) {
                writeMap(sb, map);
            } else if (value instanceof Iterable<?> iterable) {
                writeIterable(sb, iterable);
            } else if (value instanceof Object[] array) {
                writeArray(sb, array);
            } else {
                writeString(sb, value.toString());
            }
        }

        private static void writeMap(StringBuilder sb, Map<?,?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?,?> e : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                writeValue(sb, e.getValue());
            }
            sb.append('}');
        }

        private static void writeIterable(StringBuilder sb, Iterable<?> iterable) {
            sb.append('[');
            boolean first = true;
            for (Object o : iterable) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeValue(sb, o);
            }
            sb.append(']');
        }

        private static void writeArray(StringBuilder sb, Object[] array) {
            sb.append('[');
            for (int i = 0; i < array.length; i++) {
                if (i != 0) {
                    sb.append(',');
                }
                writeValue(sb, array[i]);
            }
            sb.append(']');
        }

        private static void writeString(StringBuilder sb, String s) {
            sb.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                    }
                }
            }
            sb.append('"');
        }
    }
}
