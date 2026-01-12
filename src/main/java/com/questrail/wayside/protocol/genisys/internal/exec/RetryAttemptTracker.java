package com.questrail.wayside.protocol.genisys.internal.exec;

import com.questrail.wayside.protocol.genisys.model.GenisysStationAddress;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Operational retry-attempt tracker.
 *
 * - Tracks consecutive send attempts per station
 * - Resets on semantic activity
 * - Does not encode retry policy
 */
public final class RetryAttemptTracker {

    private final ConcurrentMap<GenisysStationAddress, Integer> attempts =
            new ConcurrentHashMap<>();

    /**
     * Record that an outbound attempt was made.
     *
     * @return the updated attempt count
     */
    public int recordAttempt(GenisysStationAddress station) {
        return attempts.merge(station, 1, Integer::sum);
    }

    /**
     * Reset attempt count due to successful semantic activity.
     */
    public void reset(GenisysStationAddress station) {
        attempts.remove(station);
    }

    /**
     * Current attempt count (0 if none).
     */
    public int attemptsFor(GenisysStationAddress station) {
        return attempts.getOrDefault(station, 0);
    }
}
