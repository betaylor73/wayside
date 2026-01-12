package com.questrail.wayside.protocol.genisys.internal.time;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * ScheduledExecutorScheduler
 * =============================================================================
 * Production {@link MonotonicScheduler} implementation backed by a
 * {@link ScheduledExecutorService}.
 *
 * <h2>Design</h2>
 * <p>This scheduler converts monotonic deadlines into relative delays and delegates
 * to the underlying executor. The conversion uses the provided {@link MonotonicClock}
 * to compute the delay at scheduling time.</p>
 *
 * <h2>Clock Consistency</h2>
 * <p>For correct behavior, the same {@link MonotonicClock} instance must be used
 * both for computing deadlines (by callers) and for delay conversion (by this class).
 * Typically this is {@link SystemMonotonicClock#INSTANCE}.</p>
 *
 * <h2>Executor Ownership</h2>
 * <p>This class does <strong>not</strong> own or manage the lifecycle of the
 * provided executor. Callers are responsible for shutdown.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>This implementation is thread-safe if the underlying executor is thread-safe
 * (which is the case for standard JDK implementations).</p>
 *
 * <h2>Precision</h2>
 * <p>The actual execution time depends on the executor's scheduling precision and
 * system load. Tasks may execute slightly after their deadline, but never before.</p>
 */
public final class ScheduledExecutorScheduler implements MonotonicScheduler {

    private final ScheduledExecutorService executor;
    private final MonotonicClock clock;

    /**
     * Creates a scheduler backed by the given executor.
     *
     * @param executor the underlying scheduled executor service
     * @param clock    the monotonic clock used for delay calculations
     */
    public ScheduledExecutorScheduler(ScheduledExecutorService executor, MonotonicClock clock) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Cancellable scheduleAtNanos(long deadlineNanos, Runnable task) {
        Objects.requireNonNull(task, "task");

        // Convert absolute monotonic deadline to relative delay.
        // If deadline is in the past, schedule with zero delay (immediate execution).
        long nowNanos = clock.nowNanos();
        long delayNanos = Math.max(0, deadlineNanos - nowNanos);

        ScheduledFuture<?> future = executor.schedule(task, delayNanos, TimeUnit.NANOSECONDS);

        return new ScheduledFutureCancellable(future);
    }

    /**
     * Adapter from {@link ScheduledFuture} to {@link Cancellable}.
     */
    private static final class ScheduledFutureCancellable implements Cancellable {
        private final ScheduledFuture<?> future;

        private ScheduledFutureCancellable(ScheduledFuture<?> future) {
            this.future = future;
        }

        @Override
        public boolean cancel() {
            // mayInterruptIfRunning=false: don't interrupt if already executing
            return future.cancel(false);
        }
    }
}
