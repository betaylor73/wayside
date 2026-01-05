package com.questrail.wayside.protocol.genisys.internal.exec;

import com.questrail.wayside.protocol.genisys.internal.time.MonotonicClock;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GenisysMonotonicActivityTracker
 * -----------------------------------------------------------------------------
 * Tracks "semantic activity" per station in monotonic time.
 *
 * <p>Phase 5 timeouts must be robust against wall-clock jumps. The GENISYS
 * event model carries {@link java.time.Instant} timestamps for observability,
 * but correctness decisions must use a monotonic clock.
 *
 * <p>This tracker is the minimal bridge: transport runtimes or higher-level
 * drivers may call {@link #recordSemanticActivity(int)} when a valid semantic
 * message is accepted at the decode boundary (e.g., on MessageReceived).
 */
public final class GenisysMonotonicActivityTracker {

    private final MonotonicClock clock;
    private final Map<Integer, Long> lastActivityNanosByStation = new ConcurrentHashMap<>();

    public GenisysMonotonicActivityTracker(MonotonicClock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Record that semantic activity was observed for the given station.
     */
    public void recordSemanticActivity(int stationAddress) {
        lastActivityNanosByStation.put(stationAddress, clock.nowNanos());
    }

    /**
     * Returns the last known semantic activity time for the station, or 0 if none.
     */
    public long lastActivityNanos(int stationAddress) {
        return lastActivityNanosByStation.getOrDefault(stationAddress, 0L);
    }
}