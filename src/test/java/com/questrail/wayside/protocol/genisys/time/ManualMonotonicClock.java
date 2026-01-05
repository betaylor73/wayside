package com.questrail.wayside.protocol.genisys.time;

import com.questrail.wayside.protocol.genisys.internal.time.MonotonicClock;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic monotonic clock for tests.
 *
 * - Starts at 0
 * - Advances only when explicitly instructed
 * - Never goes backwards
 */
public final class ManualMonotonicClock implements MonotonicClock {

    private final AtomicLong nowNanos = new AtomicLong(0);

    @Override
    public long nowNanos() {
        return nowNanos.get();
    }

    public void advanceNanos(long deltaNanos) {
        if (deltaNanos < 0) {
            throw new IllegalArgumentException("Cannot advance monotonic clock backwards");
        }
        nowNanos.addAndGet(deltaNanos);
    }

    public void advanceMillis(long millis) {
        advanceNanos(millis * 1_000_000L);
    }
}
