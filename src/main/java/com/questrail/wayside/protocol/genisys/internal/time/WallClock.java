package com.questrail.wayside.protocol.genisys.internal.time;

import java.time.Instant;

/**
 * WallClock
 * =============================================================================
 * Optional Phase 5 wall-clock source used strictly for observability.
 *
 * <p>
 * This clock may jump due to DST, NTP adjustments, or explicit time setting.
 * It MUST NOT be used for operational correctness (timeouts, cadence, backoff).
 * </p>
 */
public interface WallClock
{
    /**
     * Returns the current wall-clock time.
     */
    Instant now();
}