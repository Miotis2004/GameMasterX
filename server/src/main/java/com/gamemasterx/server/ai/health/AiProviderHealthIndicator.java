package com.gamemasterx.server.ai.health;

import com.gamemasterx.server.ai.AiProvider;
import com.gamemasterx.server.ai.config.AiProviderProperties;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds the AI-provider health section reported by
 * {@code GET /health/readiness}.
 *
 * <p>This is a plain, dependency-free component (no Actuator required) so the
 * health check stays cheap and available even when the application runs with no
 * external AI provider. It reflects the configured provider: the offline local
 * provider is always {@code UP}; a provider that depends on an external service
 * can be marked {@code DOWN} when {@code failWhenUnavailable} is set.</p>
 */
public class AiProviderHealthIndicator {

    private final AiProvider provider;
    private final AiProviderProperties properties;

    public AiProviderHealthIndicator(AiProvider provider, AiProviderProperties properties) {
        this.provider = provider;
        this.properties = properties;
    }

    /**
     * @return a health map suitable for embedding in the readiness envelope.
     */
    public Map<String, Object> health() {
        if (!properties.getHealth().isEnabled()) {
            Map<String, Object> details = new HashMap<>();
            details.put("status", "UP");
            details.put("reachable", provider.isAvailable());
            details.put("provider", provider.providerName());
            details.put("model", provider.model());
            details.put("message", "AI health checks are disabled");
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("status", "UP");
            envelope.put("timestamp", Instant.now().toString());
            envelope.put("provider", details);
            return envelope;
        }

        boolean available = provider.isAvailable();
        boolean unhealthy = available
                ? false
                : properties.getHealth().isFailWhenUnavailable();
        String status = unhealthy ? "DOWN" : "UP";

        Map<String, Object> details = new HashMap<>();
        details.put("status", status);
        details.put("reachable", available);
        details.put("provider", provider.providerName());
        details.put("model", provider.model());
        details.put("contextSize", provider.contextSize());
        details.put("maxTokens", provider.maxTokens());
        details.put("message", available
                ? "AI provider is available"
                : "AI provider is not available");

        Map<String, Object> envelope = new HashMap<>();
        envelope.put("status", status);
        envelope.put("timestamp", Instant.now().toString());
        envelope.put("provider", details);
        return envelope;
    }
}
