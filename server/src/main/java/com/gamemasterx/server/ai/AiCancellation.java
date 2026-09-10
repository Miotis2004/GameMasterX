package com.gamemasterx.server.ai;

/**
 * A lightweight cooperative cancellation token passed to long-running
 * completions.
 *
 * <p>A provider implementation is expected to poll {@link
 * #isCancellationRequested()} while working and abort promptly when it returns
 * {@code true}. This backs the cancellation half of the generation lifecycle
 * alongside the timeout enforced by {@link AiGenerationService}.</p>
 */
public final class AiCancellation {

    private volatile boolean cancelled = false;

    /**
     * Requests cancellation of the associated completion. Safe to call more than
     * once and from another thread.
     */
    public void cancel() {
        cancelled = true;
    }

    /**
     * @return {@code true} once {@link #cancel()} has been called
     */
    public boolean isCancellationRequested() {
        return cancelled;
    }
}
