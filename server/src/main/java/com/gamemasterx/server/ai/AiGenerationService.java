package com.gamemasterx.server.ai;

import com.gamemasterx.server.ai.config.AiProviderProperties;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

/**
 * Orchestrates completion requests against the {@link AiProvider}.
 *
 * <p>This is the single entry point the rest of the application uses. It adds
 * the two lifecycle controls the abstraction cannot enforce on its own: a hard
 * {@link AiProviderProperties#getTimeout() timeout} and cooperative
 * {@link AiCancellation cancellation}.</p>
 */
@Service
public class AiGenerationService {

    private final AiProvider provider;
    private final AiProviderProperties properties;
    private final ExecutorService executor;

    public AiGenerationService(AiProvider provider, AiProviderProperties properties) {
        this.provider = provider;
        this.properties = properties;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ai-generation");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Runs a completion, bounded by the configured timeout and cancellation
     * window.
     *
     * @param request the completion request
     * @return the generation result
     * @throws AiGenerationTimeoutException if the completion does not finish
     *         within the configured timeout
     * @throws AiProviderException if the completion fails or is cancelled
     */
    public AiGenerationResult generate(AiGenerationRequest request) {
        if (!properties.isEnabled()) {
            throw new AiProviderException("AI generation is disabled by configuration");
        }

        AiCancellation cancellation = new AiCancellation();
        Future<AiGenerationResult> future = executor.submit(() -> provider.generate(request, cancellation));

        long timeoutMillis = properties.getTimeout().toMillis();
        try {
            return future.get(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            cancellation.cancel();
            throw new AiGenerationTimeoutException(timeoutMillis);
        } catch (ExecutionException e) {
            cancellation.cancel();
            Throwable cause = e.getCause();
            if (cause instanceof AiProviderException aiProviderException) {
                throw aiProviderException;
            }
            throw new AiProviderException("AI generation failed: " + cause.getMessage(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancellation.cancel();
            throw new AiProviderException("AI generation interrupted", e);
        }
    }

    /**
     * Shuts down the background executor. Invoked when the application context
     * closes.
     */
    public void shutdown() {
        executor.shutdownNow();
    }
}
