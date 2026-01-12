package com.questrail.wayside.protocol.genisys.internal.time;

/**
 * SystemMonotonicClock
 * =============================================================================
 * Production {@link MonotonicClock} implementation backed by {@link System#nanoTime()}.
 *
 * <h2>Properties</h2>
 * <ul>
 *   <li>Monotonically non-decreasing (never goes backward)</li>
 *   <li>Not affected by wall-clock adjustments (NTP, DST, manual changes)</li>
 *   <li>Only meaningful for elapsed time calculations, not absolute timestamps</li>
 *   <li>Resolution is typically sub-microsecond on modern systems</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <p>This implementation is suitable for production deployments where Phase 5
 * operational timing (timeouts, cadence gating, backoff) must be robust against
 * wall-clock discontinuities.</p>
 *
 * <p>For deterministic testing, use {@code ManualMonotonicClock} instead.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>This implementation is thread-safe. {@link System#nanoTime()} is inherently
 * safe for concurrent access.</p>
 */
public enum SystemMonotonicClock implements MonotonicClock {
    /**
     * Singleton instance.
     */
    INSTANCE;

    @Override
    public long nowNanos() {
        return System.nanoTime();
    }
}
