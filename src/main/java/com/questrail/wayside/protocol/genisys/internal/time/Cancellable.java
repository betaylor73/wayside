package com.questrail.wayside.protocol.genisys.internal.time;

/**
 * Cancellable
 * =============================================================================
 * Minimal cancellation handle for scheduled tasks.
 *
 * <p>
 * Phase 5 scheduling must remain transport-neutral and small. This interface is
 * intentionally tiny so it can be implemented by:
 * <ul>
 *   <li>a deterministic test scheduler</li>
 *   <li>a JVM {@code ScheduledExecutorService}-backed scheduler</li>
 *   <li>an embedded timer wheel implementation</li>
 * </ul>
 * </p>
 */
public interface Cancellable
{
    /**
     * Attempt to cancel the scheduled task.
     *
     * @return {@code true} if cancellation succeeded; {@code false} if the task
     *         was already executed or previously cancelled.
     */
    boolean cancel();
}