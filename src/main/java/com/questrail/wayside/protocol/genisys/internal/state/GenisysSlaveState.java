package com.questrail.wayside.protocol.genisys.internal.state;

import java.time.Instant;
import java.util.Objects;

/**
 * GenisysSlaveState
 * -----------------------------------------------------------------------------
 * Immutable per-slave protocol state for a GENISYS master implementation.
 *
 * <h2>Intent</h2>
 * This class captures *everything the master needs to know* about a single
 * slave to correctly drive recall, polling, retries, and control delivery.
 *
 * It deliberately contains no behavior; transitions are driven externally
 * by the state machine in response to events.
 */
public final class GenisysSlaveState
{
    /**
     * Protocol-level state for a single slave.
     */
    public enum Phase {
        RECALL,
        SEND_CONTROLS,
        POLL,
        FAILED
    }

    private final int stationAddress;
    private final Phase phase;
    private final int consecutiveFailures;
    private final boolean acknowledgmentPending;
    private final boolean controlPending;
    private final Instant lastActivity;

    private GenisysSlaveState(int stationAddress,
                              Phase phase,
                              int consecutiveFailures,
                              boolean acknowledgmentPending,
                              boolean controlPending,
                              Instant lastActivity) {
        this.stationAddress = stationAddress;
        this.phase = Objects.requireNonNull(phase, "phase");
        this.consecutiveFailures = consecutiveFailures;
        this.acknowledgmentPending = acknowledgmentPending;
        this.controlPending = controlPending;
        this.lastActivity = Objects.requireNonNull(lastActivity, "lastActivity");
    }

    public int stationAddress() {
        return stationAddress;
    }

    public Phase phase() {
        return phase;
    }

    public int consecutiveFailures() {
        return consecutiveFailures;
    }

    public boolean acknowledgmentPending() {
        return acknowledgmentPending;
    }

    public boolean controlPending() {
        return controlPending;
    }

    public Instant lastActivity() {
        return lastActivity;
    }

    // ---------------------------------------------------------------------
    // Factory helpers
    // ---------------------------------------------------------------------

    /**
     * Initial state for a slave at startup.
     */
    public static GenisysSlaveState initial(int stationAddress) {
        return new GenisysSlaveState(
                stationAddress,
                Phase.RECALL,
                0,
                false,
                false,
                Instant.EPOCH
        );
    }

    public static GenisysSlaveState failed(int stationAddress,
                                           int failureCount,
                                           Instant now) {
        return new GenisysSlaveState(
                stationAddress,
                Phase.FAILED,
                failureCount,
                false,
                false,
                now
        );
    }

    // TODO: add factory helpers for other states

    // ---------------------------------------------------------------------
    // State transition helpers
    // ---------------------------------------------------------------------

    public GenisysSlaveState withPhase(Phase newPhase, Instant now) {
        return new GenisysSlaveState(
                stationAddress,
                newPhase,
                consecutiveFailures,
                acknowledgmentPending,
                controlPending,
                now
        );
    }

    public GenisysSlaveState withFailureIncremented(Instant now) {
        return new GenisysSlaveState(
                stationAddress,
                phase,
                consecutiveFailures + 1,
                acknowledgmentPending,
                controlPending,
                now
        );
    }

    public GenisysSlaveState withFailureReset(Instant now) {
        return new GenisysSlaveState(
                stationAddress,
                phase,
                0,
                acknowledgmentPending,
                controlPending,
                now
        );
    }

    public GenisysSlaveState withAcknowledgmentPending(boolean pending, Instant now) {
        return new GenisysSlaveState(
                stationAddress,
                phase,
                consecutiveFailures,
                pending,
                controlPending,
                now
        );
    }

    public GenisysSlaveState withControlPending(boolean pending, Instant now) {
        return new GenisysSlaveState(
                stationAddress,
                phase,
                consecutiveFailures,
                acknowledgmentPending,
                pending,
                now
        );
    }
}
