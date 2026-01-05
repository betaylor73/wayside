package com.questrail.wayside.protocol.genisys.internal.time;

import java.time.Duration;
import java.util.Objects;

/**
 * MonotonicScheduler
 * =============================================================================
 * Phase 5 scheduler surface for operational timing (timeouts, cadence, backoff).
 *
 * <h2>Binding invariant</h2>
 * Scheduling MUST be expressed in monotonic ticks or durations.
 * It MUST NOT be expressed in wall-clock instants ({@code Instant}, local time,
 * time zones, DST-aware types, etc.).
 */
public interface MonotonicScheduler
{
    /**
     * Schedule a task to run at or after the given monotonic deadline.
     *
     * @param deadlineNanos monotonic deadline in nanoseconds (from {@link MonotonicClock#nowNanos()})
     * @param task         runnable task
     * @return cancellation handle
     */
    Cancellable scheduleAtNanos(long deadlineNanos, Runnable task);

    /**
     * Convenience method: schedule after a duration using a provided monotonic clock.
     *
     * <p>
     * This helper intentionally lives on the interface so all implementations
     * inherit a consistent conversion behavior.
     * </p>
     */
    default Cancellable scheduleAfter(Duration delay, MonotonicClock clock, Runnable task)
    {
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(task, "task");

        if (delay.isNegative()) {
            throw new IllegalArgumentException("delay must be >= 0");
        }

        // All Phase 5 scheduling is expressed in monotonic nanoseconds.
        long deadline = clock.nowNanos() + delay.toNanos();
        return scheduleAtNanos(deadline, task);
    }
}