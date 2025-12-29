package com.questrail.wayside.protocol.genisys.internal.state;

import com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysMessageEvent;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysTimeoutEvent;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysTransportEvent;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysControlIntentEvent;
import com.questrail.wayside.protocol.genisys.model.GenisysMessage;
import com.questrail.wayside.protocol.genisys.model.IndicationData;

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
        if (event instanceof GenisysMessageEvent.MessageReceived e) {
            return onMessageReceived(state, e);
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

    private Result onMessageReceived(GenisysControllerState state,
                                     GenisysMessageEvent.MessageReceived e) {

        final int addr = e.stationAddress();     // <- no dependency on message API
        final GenisysMessage message = e.message();

        GenisysSlaveState slave = state.slaves().get(addr);
        if (slave == null) {
            return new Result(state, GenisysIntents.none());
        }

        Instant now = e.timestamp();

        GenisysSlaveState updated = slave.withFailureReset(now);

        return switch (slave.phase()) {
            case RECALL -> handleRecallResponse(state, updated, message, now);
            case SEND_CONTROLS -> handleControlResponse(state, updated, message, now);
            case POLL -> handlePollResponse(state, updated, message, now);
            case FAILED -> handleFailedResponse(state, updated, message, now);
        };
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
                                        GenisysMessage msg,
                                        Instant now) {
        GenisysSlaveState updated = slave.withPhase(
                GenisysSlaveState.Phase.SEND_CONTROLS, now);

        GenisysControllerState newState = state.withSlaveState(updated, now);

        return new Result(newState, GenisysIntents.sendControls(updated.stationAddress()));
    }

    private Result handleControlResponse(GenisysControllerState state,
                                         GenisysSlaveState slave,
                                         GenisysMessage msg,
                                         Instant now) {
        GenisysSlaveState updated = slave
                .withControlPending(false, now)
                .withPhase(GenisysSlaveState.Phase.POLL, now);

        GenisysControllerState newState = state.withSlaveState(updated, now);

        return new Result(newState, GenisysIntents.pollNext(updated.stationAddress()));
    }

    private Result handlePollResponse(GenisysControllerState state,
                                      GenisysSlaveState slave,
                                      GenisysMessage msg,
                                      Instant now)
    {
        /*
         * POLL-phase response semantics (GENISYS):
         *
         * In POLL phase, the slave is being asked “do you have any new/changed indication data?”
         * The slave replies with one of two semantic message types:
         *
         *   1) Acknowledge ($F1):
         *        - Means “no data to report”
         *        - The master does NOT need to acknowledge the acknowledge
         *        - Therefore: ackPending = false
         *
         *   2) IndicationData ($F2):
         *        - Means “here is data”
         *        - GENISYS requires that slave->master data be acknowledged by the master
         *        - Therefore: ackPending = true
         *
         * IMPORTANT ARCHITECTURAL NOTE:
         * This decision is made purely at the semantic level:
         *   - We inspect only the decoded message type (GenisysMessage subtype)
         *   - We do not inspect frames, bytes, CRCs, or wire format artifacts
         * This is consistent with the “reducers are semantic-only” and “decode-before-event” rules.
         */
        final boolean ackPending = (msg instanceof IndicationData);

        GenisysSlaveState updated = slave
                .withAcknowledgmentPending(ackPending, now);

        GenisysControllerState newState =
                state.withSlaveState(updated, now);

        /*
         * We continue the polling loop regardless of ackPending.
         *
         * ackPending influences WHAT the next outbound message should be (e.g., ACK+POLL vs POLL),
         * but not WHETHER polling continues.
         *
         * That outbound selection remains the responsibility of the scheduler/driver that
         * interprets slave state + intents. The reducer’s job is only to maintain correct
         * semantic state.
         */
        return new Result(newState,
                GenisysIntents.pollNext(updated.stationAddress()));
    }

    private Result handleFailedResponse(GenisysControllerState state,
                                        GenisysSlaveState slave,
                                        GenisysMessage msg,
                                        Instant now) {
        GenisysSlaveState updated = slave
                .withPhase(GenisysSlaveState.Phase.RECALL, now)
                .withFailureReset(now);

        GenisysControllerState newState = state.withSlaveState(updated, now);

        return new Result(newState, GenisysIntents.beginRecall(updated.stationAddress()));
    }
}
