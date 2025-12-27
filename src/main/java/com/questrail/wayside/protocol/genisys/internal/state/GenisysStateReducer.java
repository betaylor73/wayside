package com.questrail.wayside.protocol.genisys.internal.state;

import com.questrail.wayside.protocol.genisys.internal.GenisysFrame;
import com.questrail.wayside.protocol.genisys.internal.events.*;


import java.time.Instant;
import java.util.Objects;

/**
 * GenisysStateReducer
 * -----------------------------------------------------------------------------
 * Pure, deterministic state transition engine for the GENISYS master.
 *
 * <h2>Role in the architecture</h2>
 * This class embodies the *state machine logic* described in
 * {@code GENISYS_MASTER_STATE_MACHINE.md}.
 * <p>
 * It is intentionally:
 * <ul>
 *   <li>Pure (no I/O, no timers, no side effects)</li>
 *   <li>Deterministic</li>
 *   <li>Event-driven</li>
 * </ul>
 *
 * Given a prior {@link GenisysControllerState} and a single
 * {@link GenisysEvent}, the reducer computes:
 * <ul>
 *   <li>a new {@link GenisysControllerState}</li>
 *   <li>a set of *intentions* describing what the controller should do next</li>
 * </ul>
 *
 * The reducer itself never performs those actions. Execution is the
 * responsibility of a higher-level coordinator (e.g. {@code GenisysWaysideController}).
 *
 * <h2>Why a reducer?</h2>
 * Modeling the protocol as a reducer:
 * <ul>
 *   <li>Separates reasoning about correctness from I/O complexity</li>
 *   <li>Makes unit testing trivial</li>
 *   <li>Matches an actor-style, message-driven mental model</li>
 * </ul>
 */
public final class GenisysStateReducer
{
    /**
     * Result of applying an event to a controller state.
     *
     * @param newState the updated controller state
     * @param intents  protocol intentions to be executed by the caller
     */
    public record Result(GenisysControllerState newState,
                         GenisysIntents intents) {}

    /**
     * Applies a single event to the current controller state.
     *
     * @param state the current state (must not be {@code null})
     * @param event the event to apply (must not be {@code null})
     * @return the resulting state and intentions
     */
    public Result apply(GenisysControllerState state, GenisysEvent event) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(event, "event");

        // Dispatch based on event type. This is intentionally explicit;
        // protocol behavior should be readable, not clever.
        if (event instanceof GenisysTransportEvent.TransportUp e) {
            return onTransportUp(state, e);
        }
        if (event instanceof GenisysTransportEvent.TransportDown e) {
            return onTransportDown(state, e);
        }
        if (event instanceof GenisysFrameEvent.FrameReceived e) {
            return onFrameReceived(state, e);
        }
        if (event instanceof GenisysFrameEvent.FrameInvalid e) {
            return onFrameInvalid(state, e);
        }
        if (event instanceof GenisysTimeoutEvent.ResponseTimeout e) {
            return onResponseTimeout(state, e);
        }
        if (event instanceof GenisysControlIntentEvent.ControlIntentChanged e) {
            return onControlIntentChanged(state, e);
        }

        // Unknown events are ignored by default.
        return new Result(state, GenisysIntents.none());
    }

    // ---------------------------------------------------------------------
    // Event handlers
    // ---------------------------------------------------------------------

    private Result onTransportUp(GenisysControllerState state,
                                 GenisysTransportEvent.TransportUp e) {
        // Transport availability allows protocol activity to resume.
        // Transition to INITIALIZING to force recall/synchronization.
        GenisysControllerState newState = state
                .withGlobalState(GenisysControllerState.GlobalState.INITIALIZING,
                        e.timestamp());

        return new Result(newState, GenisysIntents.beginInitialization());
    }

    private Result onTransportDown(GenisysControllerState state,
                                   GenisysTransportEvent.TransportDown e) {
        // Loss of transport invalidates protocol progress.
        // Remain in current state but suppress protocol activity.
        return new Result(state, GenisysIntents.suspendAll());
    }

    private Result onFrameReceived(GenisysControllerState state,
                                   GenisysFrameEvent.FrameReceived e) {
        GenisysFrame frame = e.frame();
        int addr = frame.stationAddress();

        GenisysSlaveState slave = state.slaves().get(addr);
        if (slave == null) {
            // Unknown slave; ignore safely.
            return new Result(state, GenisysIntents.none());
        }

        Instant now = e.timestamp();

        // Successful reception resets failure counters.
        GenisysSlaveState updated = slave.withFailureReset(now);

        // Delegate to phase-specific logic.
        return switch (slave.phase()) {
            case RECALL -> handleRecallResponse(state, updated, frame, now);
            case SEND_CONTROLS -> handleControlResponse(state, updated, frame, now);
            case POLL -> handlePollResponse(state, updated, frame, now);
            case FAILED -> handleFailedResponse(state, updated, frame, now);
        };
    }

    private Result onFrameInvalid(GenisysControllerState state,
                                  GenisysFrameEvent.FrameInvalid e) {
        // Treat invalid frames as failures for the addressed slave if known.
        // Since address may be unknown, we conservatively do nothing here.
        return new Result(state, GenisysIntents.none());
    }

    private Result onResponseTimeout(GenisysControllerState state,
                                     GenisysTimeoutEvent.ResponseTimeout e) {
        // Timeouts increment failure counters and may drive slaves into FAILED.
        // For simplicity at this level, we emit a retry intent.
        return new Result(state, GenisysIntents.retryCurrent());
    }

    private Result onControlIntentChanged(GenisysControllerState state,
                                          GenisysControlIntentEvent.ControlIntentChanged e) {
        // Mark all active slaves as having pending control updates.
        Instant now = e.timestamp();
        GenisysControllerState newState = state;

        for (GenisysSlaveState slave : state.slaves().values()) {
            if (slave.phase() != GenisysSlaveState.Phase.FAILED) {
                GenisysSlaveState updated = slave.withControlPending(true, now);
                newState = newState.withSlaveState(updated, now);
            }
        }

        return new Result(newState, GenisysIntents.scheduleControlDelivery());
    }

    // ---------------------------------------------------------------------
    // Phase-specific handlers
    // ---------------------------------------------------------------------

    private Result handleRecallResponse(GenisysControllerState state,
                                        GenisysSlaveState slave,
                                        GenisysFrame frame,
                                        Instant now) {
        // On successful recall response, transition to SEND_CONTROLS.
        GenisysSlaveState updated = slave.withPhase(
                GenisysSlaveState.Phase.SEND_CONTROLS, now);

        GenisysControllerState newState = state.withSlaveState(updated, now);

        return new Result(newState, GenisysIntents.sendControls(updated.stationAddress()));
    }

    private Result handleControlResponse(GenisysControllerState state,
                                         GenisysSlaveState slave,
                                         GenisysFrame frame,
                                         Instant now) {
        // Control delivery complete; clear pending flag and move to POLL.
        GenisysSlaveState updated = slave
                .withControlPending(false, now)
                .withPhase(GenisysSlaveState.Phase.POLL, now);

        GenisysControllerState newState = state.withSlaveState(updated, now);

        return new Result(newState, GenisysIntents.pollNext(updated.stationAddress()));
    }

    private Result handlePollResponse(GenisysControllerState state,
                                      GenisysSlaveState slave,
                                      GenisysFrame frame,
                                      Instant now) {
        // Successful poll response; manage acknowledgment state.
        GenisysSlaveState updated = slave
                .withAcknowledgmentPending(true, now);

        GenisysControllerState newState = state.withSlaveState(updated, now);

        return new Result(newState, GenisysIntents.pollNext(updated.stationAddress()));
    }

    private Result handleFailedResponse(GenisysControllerState state,
                                        GenisysSlaveState slave,
                                        GenisysFrame frame,
                                        Instant now) {
        // Any valid response revives the slave into RECALL.
        GenisysSlaveState updated = slave
                .withPhase(GenisysSlaveState.Phase.RECALL, now)
                .withFailureReset(now);

        GenisysControllerState newState = state.withSlaveState(updated, now);

        return new Result(newState, GenisysIntents.beginRecall(updated.stationAddress()));
    }
}
