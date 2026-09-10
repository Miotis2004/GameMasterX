package com.gamemasterx.server.ai;

/**
 * Internal AI provider abstraction.
 *
 * <p>This interface is the single contract every concrete AI backend must
 * satisfy. It intentionally describes <em>what</em> a provider offers
 * (endpoint, model identity, context size, generation limit, availability and
 * completion) without exposing <em>how</em> it authenticates or talks to a
 * network. Concrete implementations own all provider-specific transport and
 * credential handling; the rest of the application only ever depends on this
 * interface.</p>
 *
 * <p>Requirements every implementation must uphold:</p>
 * <ul>
 *   <li>Never read or require API keys, bearer tokens or other credentials at
 *       construction time. Credentials, if any, come from the environment and
 *       stay on the server.</li>
 *   <li>{@link #isAvailable()} must not block or open a socket; it should report
 *       configuration-based readiness so health checks stay cheap.</li>
 *   <li>{@link #generate(AiGenerationRequest, AiCancellation)} must honour the
 *       {@link AiCancellation} token and abort promptly when cancellation is
 *       requested.</li>
 * </ul>
 */
public interface AiProvider {

    /**
     * @return stable short identifier for this provider implementation, e.g.
     *         {@code "local"} or {@code "openai"}
     */
    String providerName();

    /**
     * @return the model identity this provider is bound to (never a secret).
     */
    String model();

    /**
     * @return the maximum number of context tokens the model accepts.
     */
    int contextSize();

    /**
     * @return the hard upper bound on output tokens per completion.
     */
    int maxTokens();

    /**
     * @return {@code true} when the provider is configured and usable without an
     *         external AI service (for example the bundled local provider);
     *         {@code false} when it depends on a configured external endpoint.
     */
    boolean isAvailable();

    /**
     * Runs a single completion.
     *
     * @param request the completion request
     * @param cancellation cooperative cancellation token
     * @return the generated result
     * @throws AiProviderException if the completion fails for a reason other
     *         than a timeout or cancellation
     */
    AiGenerationResult generate(AiGenerationRequest request, AiCancellation cancellation);
}
