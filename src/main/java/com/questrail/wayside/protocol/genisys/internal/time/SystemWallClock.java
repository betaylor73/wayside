package com.questrail.wayside.protocol.genisys.internal.time;

import java.time.Instant;

/**
 * SystemWallClock
 * =============================================================================
 * Production {@link WallClock} implementation backed by {@link Instant#now()}.
 *
 * <h2>Properties</h2>
 * <ul>
 *   <li>Returns the current wall-clock time as an {@link Instant}</li>
 *   <li>May jump forward or backward due to NTP, DST, or manual adjustments</li>
 *   <li>Suitable only for observability, logging, and human-readable timestamps</li>
 * </ul>
 *
 * <h2>Warning</h2>
 * <p><strong>Do not use for operational correctness.</strong> Phase 5 timing logic
 * (timeouts, cadence, backoff) must use {@link MonotonicClock} exclusively.
 * This clock is provided only for observability sinks that need real timestamps.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>This implementation is thread-safe. {@link Instant#now()} is inherently
 * safe for concurrent access.</p>
 */
public enum SystemWallClock implements WallClock {
    /**
     * Singleton instance.
     */
    INSTANCE;

    @Override
    public Instant now() {
        return Instant.now();
    }
}
