package com.gamemasterx.server.ai.operation;

import com.gamemasterx.server.ai.operation.v1.ProposedOperationSchemaV1;
import com.gamemasterx.server.ai.operation.v2.ProposedOperationSchemaV2;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Backend authority on the versioned {@link OperationSchema}s.
 *
 * <p>The registry is the single place that instantiates and exposes the schema
 * for every recognised {@link OperationSchemaVersion}. Because a {@link
 * OperationSchema} is defined here and never serialised to the browser, a
 * caller cannot rewrite what any version means; it can only submit a proposal
 * that declares a version, and the backend then validates that proposal
 * against the authoritative schema the registry returns.</p>
 *
 * <p>Multiple versions coexist: every registered schema is live at the same
 * time, so proposals captured under different versions can each be validated
 * against their own schema.</p>
 */
@Component
public class OperationSchemaRegistry {

    private final Map<OperationSchemaVersion, OperationSchema> byVersion = new TreeMap<>();

    /**
     * Instantiates and registers the schema for every recognised version. The
     * constructor runs once at application startup.
     */
    public OperationSchemaRegistry() {
        register(new ProposedOperationSchemaV1());
        register(new ProposedOperationSchemaV2());
    }

    private void register(OperationSchema schema) {
        byVersion.put(schema.version(), schema);
    }

    /**
     * Resolve the authoritative schema for a given version.
     *
     * @param version the schema version to resolve
     * @return the matching schema
     * @throws IllegalArgumentException if no schema is registered for
     *                                  {@code version}
     */
    public OperationSchema resolve(OperationSchemaVersion version) {
        OperationSchema schema = byVersion.get(version);
        if (schema == null) {
            throw new IllegalArgumentException(
                    "No operation schema registered for version " + version.wire());
        }
        return schema;
    }

    /**
     * @return the immutable, version-ordered set of currently supported
     * schema versions
     */
    public Set<OperationSchemaVersion> supportedVersions() {
        return byVersion.keySet();
    }
}
