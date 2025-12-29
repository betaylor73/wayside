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

        /*
         * Transport gating policy (global):
         *
         * In a connection-less transport (e.g. UDP), "transport down" is not inferred
         * from slave silence; it is a *local* indication that the master cannot reliably
         * perform I/O (socket not bound, interface down, administrative inhibit, etc.).
         *
         * While TRANSPORT_DOWN:
         *   - the reducer must not allow protocol progress
         *   - no slave state may mutate (no phase changes, no failure increments/resets)
         *   - no protocol intents are emitted
         *   - only TransportUp is honored, which forces INITIALIZING (recall/sync)
         */
        if (state.globalState() == GenisysControllerState.GlobalState.TRANSPORT_DOWN) {
            if (event instanceof GenisysTransportEvent.TransportUp e) {
                return onTransportUp(state, e);
            }
            if (event instanceof GenisysTransportEvent.TransportDown e) {
                // Idempotent: already down; re-emit suspend to reinforce higher-layer gating.
                return onTransportDown(state, e);
            }
            return new Result(state, GenisysIntents.none());
        }

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
        /*
         * TransportDown is a *local* gate, not a per-slave protocol signal.
         *
         * We record the global state transition so that subsequent protocol events
         * (timeouts, messages, control intent changes) are ignored until TransportUp.
         *
         * IMPORTANT:
         * - We do not mutate any per-slave state here.
         * - We do not attempt to "fail" slaves. Slave failure is protocol-level and
         *   is modeled via ResponseTimeout while transport is RUNNING.
         */
        GenisysControllerState newState = state.withGlobalState(
                GenisysControllerState.GlobalState.TRANSPORT_DOWN,
                e.timestamp());

        // Higher layers should suspend protocol activity immediately.
        return new Result(newState, GenisysIntents.suspendAll());
    }

    private Result onMessageReceived(GenisysControllerState state,
                                     GenisysMessageEvent.MessageReceived e) {

        final int addr = e.stationAddress();     // event carries the semantic station identity
        final GenisysMessage message = e.message();

        GenisysSlaveState slave = state.slaves().get(addr);
        if (slave == null) {
            // Message for an unknown/unconfigured station is ignored.
            return new Result(state, GenisysIntents.none());
        }

        final Instant now = e.timestamp();

        /*
         * Any successfully decoded semantic message counts as "activity" and resets
         * the consecutive failure counter for the associated slave.
         *
         * This is an architectural boundary decision:
         *   - decode failures do not generate reducer events (so they never reach here)
         *   - only semantic message receipt can reset failure tracking
         */
        GenisysSlaveState updated = slave.withFailureReset(now);
        GenisysControllerState stateAfterReset = state.withSlaveState(updated, now);

        // Phase-specific message handling remains semantic-only.
        return switch (updated.phase()) {
            case RECALL -> handleRecallResponse(stateAfterReset, updated, message, now);
            case SEND_CONTROLS -> handleControlResponse(stateAfterReset, updated, message, now);
            case POLL -> handlePollResponse(stateAfterReset, updated, message, now);
            case FAILED -> handleFailedResponse(stateAfterReset, updated, message, now);
        };
    }

    private Result onResponseTimeout(GenisysControllerState state,
                                     GenisysTimeoutEvent.ResponseTimeout e) {
        /*
         * ResponseTimeout semantics (semantic-only, reducer-visible):
         *
         * A ResponseTimeout indicates that a specific slave did not produce an expected
         * semantic response within the allowed time window.
         *
         * The reducer does not infer timeouts from I/O. Timeouts are modeled by higher
         * layers (scheduler/driver) and injected as reducer events.
         */

        final int addr = e.stationAddress();
        final Instant now = e.timestamp();

        GenisysSlaveState slave = state.slaves().get(addr);
        if (slave == null) {
            // Timeout for an unknown/unconfigured station is ignored.
            return new Result(state, GenisysIntents.none());
        }

        return switch (slave.phase()) {

            case POLL -> {
                /*
                 * POLL timeout semantics (GENISYS):
                 *
                 * A timeout during POLL is treated equivalently to an invalid/missing
                 * response. The master:
                 *   - increments consecutiveFailures
                 *   - retries up to a fixed threshold
                 *   - transitions to FAILED once the threshold is reached
                 */
                GenisysSlaveState incremented = slave.withFailureIncremented(now);

                // Protocol rule: FAILED after three consecutive failures.
                final int failureThreshold = 3;

                if (incremented.consecutiveFailures() < failureThreshold) {
                    GenisysControllerState newState = state.withSlaveState(incremented, now);
                    yield new Result(newState, GenisysIntents.retryCurrent());
                }

                // Threshold reached: enter FAILED and begin recovery via RECALL.
                GenisysSlaveState failed = incremented
                        .withPhase(GenisysSlaveState.Phase.FAILED, now)
                        .withAcknowledgmentPending(false, now);

                GenisysControllerState newState = state.withSlaveState(failed, now);
                yield new Result(newState, GenisysIntents.sendRecall(addr));
            }

            case RECALL -> {
                /*
                 * RECALL timeout semantics (GENISYS):
                 *
                 * RECALL is entered as part of recovery after a slave is declared FAILED.
                 * A timeout here means the slave did not respond to the recall attempt.
                 *
                 * We intentionally:
                 *   - do NOT increment consecutiveFailures (already failed)
                 *   - do NOT change phase (remain in RECALL)
                 *   - DO attempt recall again
                 */
                yield new Result(state, GenisysIntents.sendRecall(addr));
            }

            case SEND_CONTROLS -> {
                /*
                 * SEND_CONTROLS timeout semantics (GENISYS):
                 *
                 * SEND_CONTROLS represents an attempt to deliver control updates
                 * to a slave that has already successfully responded to recall.
                 *
                 * A timeout here indicates that control delivery failed.
                 * The protocol semantics are:
                 *   - increment consecutiveFailures
                 *   - if below threshold: retry SEND_CONTROLS
                 *   - if threshold reached: transition to FAILED and begin RECALL
                 *
                 * This mirrors POLL failure semantics, but is scoped specifically
                 * to the control-delivery phase.
                 */
                GenisysSlaveState incremented = slave.withFailureIncremented(now);

                final int failureThreshold = 3;

                if (incremented.consecutiveFailures() < failureThreshold) {
                    GenisysControllerState newState = state.withSlaveState(incremented, now);
                    yield new Result(newState, GenisysIntents.sendControls(addr));
                }

                // Threshold reached: control delivery failed repeatedly; re-enter recovery.
                GenisysSlaveState failed = incremented
                        .withPhase(GenisysSlaveState.Phase.FAILED, now)
                        .withAcknowledgmentPending(false, now)
                        .withControlPending(true, now);

                GenisysControllerState newState = state.withSlaveState(failed, now);
                yield new Result(newState, GenisysIntents.sendRecall(addr));
            }

            default -> {
                /*
                 * Timeout handling for other phases (SEND_CONTROLS, FAILED) will be
                 * made exhaustive next. Until then we preserve conservative behavior:
                 *   - no state mutation
                 *   - request retry of the current protocol step
                 */
                yield new Result(state, GenisysIntents.retryCurrent());
            }
        };
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
        // Per GENISYS: A full IndicationData image is the only valid response to Recall.
        // If anything else is received, do not advance phase; remain in RECALL and emit no intents.
        if (!(msg instanceof IndicationData)) {
            return new Result(state, GenisysIntents.none());
        }

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

        return new Result(newState, GenisysIntents.sendRecall(updated.stationAddress()));
    }
}
