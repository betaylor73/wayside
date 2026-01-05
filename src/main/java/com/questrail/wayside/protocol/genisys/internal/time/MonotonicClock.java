package com.questrail.wayside.protocol.genisys.internal.time;

/**
 * MonotonicClock
 * =============================================================================
 * Phase 5 time source for operational correctness.
 *
 * <h2>Binding invariant</h2>
 * All Phase 5 timing logic (timeouts, cadence gating, backoff/retry spacing)
 * MUST use a monotonic time source. Wall-clock time (e.g. {@code Instant.now()})
 * is permitted only for observability.
 *
 * <p>
 * Implementations should be backed by a monotonic clock such as
 * {@link System#nanoTime()} on the JVM or a hardware timer on embedded targets.
 * </p>
 */
public interface MonotonicClock
{
    /**
     * Returns a monotonically increasing tick value in nanoseconds.
     *
     * <p>
     * This should be backed by a monotonic source such as {@link System#nanoTime()}.
     * Values are only meaningful for elapsed time computations.
     * </p>
     */
    long nowNanos();
}