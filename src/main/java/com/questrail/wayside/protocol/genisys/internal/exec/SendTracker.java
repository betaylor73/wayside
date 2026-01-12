package com.questrail.wayside.protocol.genisys.internal.exec;

/**
 * SendTracker
 * =============================================================================
 * Minimal shared state for tracking which station was most recently sent to.
 *
 * <h2>Purpose</h2>
 * For {@code POLL_NEXT} intents, station selection is executor-owned (the delegate
 * executor decides which station to poll based on controller state). The
 * {@link TimedGenisysIntentExecutor} needs to know which station was actually
 * polled so it can arm a response timeout for that station.
 *
 * <p>This tracker provides a simple communication channel: the delegate executor
 * records the station it sent to, and the timed executor reads it after delegation.</p>
 *
 * <h2>Usage Pattern</h2>
 * <pre>
 *   1. TimedGenisysIntentExecutor owns the tracker and passes it to the delegate
 *   2. Delegate executor calls recordSend(station) when sending
 *   3. TimedGenisysIntentExecutor reads lastSentStation() after delegation
 *   4. TimedGenisysIntentExecutor clears the tracker before the next execute() call
 * </pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This class uses volatile for visibility but assumes single-threaded execution
 * per the actor-style model documented in {@link GenisysIntentExecutor}.</p>
 */
public final class SendTracker {

    private volatile int lastSentStation = -1;

    /**
     * Records that a message was sent to the given station.
     * Called by the delegate executor after each send.
     *
     * @param station the station address that was sent to
     */
    public void recordSend(int station) {
        this.lastSentStation = station;
    }

    /**
     * Returns the station that was most recently sent to, or -1 if none.
     */
    public int lastSentStation() {
        return lastSentStation;
    }

    /**
     * Clears the tracked station. Should be called before each execute() cycle.
     */
    public void clear() {
        this.lastSentStation = -1;
    }
}
