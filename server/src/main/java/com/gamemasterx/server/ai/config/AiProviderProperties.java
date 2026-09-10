package com.gamemasterx.server.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Configuration properties for the internal AI provider layer.
 *
 * <p>All values are safe to leave unset: when no external endpoint is
 * configured the bundled offline local provider is used so the
 * application builds and runs with no cloud AI provider and no credentials.
 *
 * <p>Every property is optionally overridable through an environment variable
 * using the {@code GAME_MASTER_X_AI_...} naming convention, so no secret or
 * provider configuration is ever baked into the build or shipped to the
 * browser.</p>
 *
 * <p>Bound from the prefix {@code game.master.x.ai}.</p>
 */
@ConfigurationProperties(prefix = "game.master.x.ai")
public class AiProviderProperties {

    /** Overall switch for the AI layer. */
    private boolean enabled = true;

    /**
     * External provider endpoint. Empty means "no external provider", which
     * selects the bundled offline local provider. Never treated as a secret;
     * a public base URL is safe to expose, but credentials are separate.
     */
    private String endpoint = "";

    /**
     * Whether an external endpoint is mandatory. When {@code true} and no
     * endpoint is configured the context load fails loudly instead of silently
     * falling back to the local provider.
     */
    private boolean externalRequired = false;

    /** Model identity. Exposed for diagnostics; never a credential. */
    private String model = "local-stub";

    /** API key location. Only the variable name is stored here, never the value. */
    private String apiKeyEnv = "";

    /** Context window size in tokens. */
    private int contextSize = 4096;

    /** Hard cap on output tokens per completion (generation limit). */
    private int maxTokens = 512;

    /** Per-generation timeout. */
    private Duration timeout = Duration.ofSeconds(15);

    /** Cancellation behaviour for in-flight completions. */
    private Cancellation cancellation = new Cancellation();

    /** Health check exposure. */
    private Health health = new Health();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public boolean isExternalRequired() {
        return externalRequired;
    }

    public void setExternalRequired(boolean externalRequired) {
        this.externalRequired = externalRequired;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiKeyEnv() {
        return apiKeyEnv;
    }

    public void setApiKeyEnv(String apiKeyEnv) {
        this.apiKeyEnv = apiKeyEnv;
    }

    public int getContextSize() {
        return contextSize;
    }

    public void setContextSize(int contextSize) {
        this.contextSize = contextSize;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public Cancellation getCancellation() {
        return cancellation;
    }

    public void setCancellation(Cancellation cancellation) {
        this.cancellation = cancellation;
    }

    public Health getHealth() {
        return health;
    }

    public void setHealth(Health health) {
        this.health = health;
    }

    /**
     * Cancellation configuration for in-flight completions.
     */
    public static class Cancellation {

        /** Whether callers may cancel an in-flight completion. */
        private boolean enabled = true;

        /**
         * Polling interval the local provider uses to check for cancellation.
         */
        private Duration pollInterval = Duration.ofMillis(100);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getPollInterval() {
            return pollInterval;
        }

        public void setPollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
        }
    }

    /**
     * Health check configuration.
     */
    public static class Health {

        /** Whether the AI health indicator is registered. */
        private boolean enabled = true;

        /** Whether an unavailable external provider reports {@code DOWN}. */
        private boolean failWhenUnavailable = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isFailWhenUnavailable() {
            return failWhenUnavailable;
        }

        public void setFailWhenUnavailable(boolean failWhenUnavailable) {
            this.failWhenUnavailable = failWhenUnavailable;
        }
    }
}
