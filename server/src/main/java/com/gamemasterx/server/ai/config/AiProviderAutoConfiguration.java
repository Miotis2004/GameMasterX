package com.gamemasterx.server.ai.config;

import com.gamemasterx.server.ai.AiProvider;
import com.gamemasterx.server.ai.AiGenerationService;
import com.gamemasterx.server.ai.local.AiLocalOpenAiProvider;
import com.gamemasterx.server.ai.local.AiLocalProvider;
import com.gamemasterx.server.ai.health.AiProviderHealthIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Wires the internal AI provider layer.
 *
 * <p>The configuration is designed to run with <em>no</em> cloud AI provider:
 * whenever no external {@code game.master.x.ai.endpoint} is configured the
 * bundled offline {@link AiLocalProvider} is registered as the primary {@link
 * AiProvider}, so the application starts, builds and serves requests without any
 * credentials or network dependency.</p>
 *
 * <p>When {@code game.master.x.ai.endpoint} is set, a configurable
 * {@link AiLocalOpenAiProvider} (an OpenAI-compatible adapter for a local model
 * server) becomes the primary backend. It degrades gracefully to an offline
 * placeholder when the endpoint cannot be reached. When no endpoint is set the
 * fully offline {@link AiLocalProvider} is used instead.</p>
 *
 * <p>If the application supplies its own {@link AiProvider} bean (for example a
 * custom external provider) that bean is used verbatim; this auto-configuration
 * only contributes a provider when none is already present, so a user-defined
 * {@link Primary} {@link AiProvider} always wins and building the application
 * never fails just because the adapter is on the classpath.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProviderProperties.class)
public class AiProviderAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiProviderAutoConfiguration.class);

    /**
     * Registers the effective {@link AiProvider}. Only contributes a provider
     * when none is already defined in the context, so a user-supplied
     * {@link AiProvider} bean always takes precedence.
     *
     * <p>Selection policy:</p>
     * <ul>
     *   <li>An external {@code endpoint} configured -> the configurable
     *       OpenAI-compatible {@link AiLocalOpenAiProvider}, which degrades
     *       gracefully when the endpoint is unreachable.</li>
     *   <li>No endpoint, but {@code external-required=true} -> fail loudly so
     *       misconfiguration is obvious.</li>
     *   <li>No endpoint -> the fully offline {@link AiLocalProvider}.</li>
     * </ul>
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(AiProvider.class)
    public AiProvider aiProvider(AiProviderProperties properties) {
        String endpoint = properties.getEndpoint() == null ? "" : properties.getEndpoint().trim();
        if (!endpoint.isEmpty()) {
            log.info("AI endpoint '{}' configured; using OpenAI-compatible local model adapter (model={}).",
                    endpoint, properties.getModel());
            return new AiLocalOpenAiProvider(properties);
        }
        if (properties.isExternalRequired()) {
            throw new IllegalStateException(
                    "game.master.x.ai.external-required=true but no AI endpoint is configured "
                            + "(game.master.x.ai.endpoint). Set an endpoint or disable the requirement.");
        }
        log.info("No AI endpoint configured; using offline local AI provider (model={}).", properties.getModel());
        return new AiLocalProvider(properties);
    }

    /**
     * Applies the configured timeout and cancellation around every completion.
     */
    @Bean
    public AiGenerationService aiGenerationService(AiProvider provider, AiProviderProperties properties) {
        return new AiGenerationService(provider, properties);
    }

    /**
     * Registers the AI health indicator. The bean is always created so it can be
     * depended on unconditionally (for example by {@code HealthController}); the
     * {@code game.master.x.ai.health.enabled} flag is honoured inside the
     * indicator so disabling it does not break other components.
     */
    @Bean
    public AiProviderHealthIndicator aiProviderHealthIndicator(
            AiProvider provider, AiProviderProperties properties) {
        return new AiProviderHealthIndicator(provider, properties);
    }
}
