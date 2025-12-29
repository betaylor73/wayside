package com.questrail.wayside.protocol.genisys.internal.state;

import com.questrail.wayside.api.ControlId;
import com.questrail.wayside.api.IndicationId;
import com.questrail.wayside.api.IndicationSet;
import com.questrail.wayside.protocol.genisys.internal.events.*;
import com.questrail.wayside.protocol.genisys.model.Acknowledge;
import com.questrail.wayside.protocol.genisys.model.GenisysMessage;
import com.questrail.wayside.mapping.ArraySignalIndex;
import com.questrail.wayside.mapping.SignalIndex;
import com.questrail.wayside.api.ControlSet;
import com.questrail.wayside.protocol.genisys.model.GenisysStationAddress;
import com.questrail.wayside.protocol.genisys.model.IndicationData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GenisysStateReducerTest
 * -----------------------------------------------------------------------------
 * Unit tests for the pure GENISYS state reducer.
 *
 * These tests deliberately:
 * <ul>
 *   <li>do not involve transports</li>
 *   <li>do not involve timers</li>
 *   <li>do not involve threading</li>
 * </ul>
 *
 * They validate that, given a prior state and an event, the reducer produces
 * the correct next state and protocol intents.
 */
public class GenisysStateReducerTest {

    private GenisysStateReducer reducer;
    private Instant now;
    private SignalIndex<ControlId> controlIndex;
    private SignalIndex<IndicationId> indicationIndex;

    record TestControlId(int number) implements ControlId {
        @Override
        public Optional<String> label() {
            return Optional.empty();
        }
        }

    record TestIndicationId(int number) implements IndicationId {
        @Override
        public Optional<String> label() {
            return Optional.empty();
        }
        }

    @BeforeEach
    void setUp() {
        reducer = new GenisysStateReducer();
        now = Instant.now();

        controlIndex = new ArraySignalIndex<>(
                new TestControlId(1),
                new TestControlId(2),
                new TestControlId(3),
                new TestControlId(4)
        );

        indicationIndex = new ArraySignalIndex<>(
                new TestIndicationId(10),
                new TestIndicationId(11)
        );
    }

    private GenisysControllerState initialStateWithSlaves(int... stations) {
        return GenisysControllerState.initializing(
                Arrays.stream(stations).boxed().toList(),
                now
        );
    }

    // ---------------------------------------------------------------------
    // Transport events
    // ---------------------------------------------------------------------

    @Test
    void transportUpForcesInitialization() {
        GenisysControllerState state = initialStateWithSlaves(1);

        GenisysEvent event = new GenisysTransportEvent.TransportUp(now);
        GenisysStateReducer.Result result = reducer.apply(state, event);

        assertEquals(GenisysControllerState.GlobalState.INITIALIZING,
                result.newState().globalState());

        assertTrue(result.intents().kinds()
                .contains(GenisysIntents.Kind.BEGIN_INITIALIZATION));
    }

    @Test
    void transportDownSuspendsProtocol() {
        GenisysControllerState state = initialStateWithSlaves(1);

        GenisysEvent event = new GenisysTransportEvent.TransportDown(now);
        GenisysStateReducer.Result result = reducer.apply(state, event);

        assertTrue(result.intents().kinds()
                .contains(GenisysIntents.Kind.SUSPEND_ALL));
    }

    // ---------------------------------------------------------------------
    // Recall phase
    // ---------------------------------------------------------------------

    @Test
    void recallResponseTransitionsToSendControls() {
        GenisysControllerState state = initialStateWithSlaves(1);

        // The reducer now consumes semantic messages (decode-before-event).
        // During RECALL, a valid slave->master response is typically IndicationData ($F2).
        IndicationSet indications = IndicationSet.empty(indicationIndex);

        GenisysMessage msg = new IndicationData(
                GenisysStationAddress.of(1),
                indications
        );

        // IMPORTANT: existing event signature is (timestamp, stationAddress, message).
        GenisysEvent event = new GenisysMessageEvent.MessageReceived(now, 1, msg);

        GenisysStateReducer.Result result = reducer.apply(state, event);

        GenisysSlaveState slave = result.newState().slaves().get(1);
        assertEquals(GenisysSlaveState.Phase.SEND_CONTROLS, slave.phase());

        assertTrue(result.intents().kinds()
                .contains(GenisysIntents.Kind.SEND_CONTROLS));
        assertEquals(1, result.intents().targetStation());
    }

    @Test
    void pollAcknowledgeClearsAckPending() {
        GenisysControllerState state = initialStateWithSlaves(1);

        // 1) Drive RECALL -> SEND_CONTROLS by providing the canonical recall response (IndicationData).
        IndicationSet indications = IndicationSet.empty(indicationIndex);
        GenisysMessage recallResponse = new IndicationData(
                GenisysStationAddress.of(1),
                indications
        );
        GenisysEvent recallEvent = new GenisysMessageEvent.MessageReceived(now, 1, recallResponse);
        GenisysStateReducer.Result afterRecall = reducer.apply(state, recallEvent);

        assertEquals(GenisysSlaveState.Phase.SEND_CONTROLS,
                afterRecall.newState().slaves().get(1).phase());

        // 2) Drive SEND_CONTROLS -> POLL.
        //    The current reducer implementation advances phases based on phase, not message type,
        //    so any valid semantic message will do. Acknowledge is the simplest.
        GenisysMessage controlResponse = new Acknowledge(GenisysStationAddress.of(1));
        GenisysEvent controlEvent = new GenisysMessageEvent.MessageReceived(now, 1, controlResponse);
        GenisysStateReducer.Result afterControl = reducer.apply(afterRecall.newState(), controlEvent);

        assertEquals(GenisysSlaveState.Phase.POLL,
                afterControl.newState().slaves().get(1).phase());

        // 3) In POLL, receiving $F1 Acknowledge means "no data"; no acknowledgment is required.
        GenisysMessage pollAck = new Acknowledge(GenisysStationAddress.of(1));
        GenisysEvent pollAckEvent = new GenisysMessageEvent.MessageReceived(now, 1, pollAck);
        GenisysStateReducer.Result result = reducer.apply(afterControl.newState(), pollAckEvent);

        assertFalse(result.newState().slaves().get(1).acknowledgmentPending());
    }

    @Test
    void pollIndicationDataSetsAckPending() {
        GenisysControllerState state = initialStateWithSlaves(1);

        // 1) Drive RECALL -> SEND_CONTROLS.
        IndicationSet indications = IndicationSet.empty(indicationIndex);
        GenisysMessage recallResponse = new IndicationData(
                GenisysStationAddress.of(1),
                indications
        );
        GenisysEvent recallEvent = new GenisysMessageEvent.MessageReceived(now, 1, recallResponse);
        GenisysStateReducer.Result afterRecall = reducer.apply(state, recallEvent);

        // 2) Drive SEND_CONTROLS -> POLL.
        GenisysMessage controlResponse = new Acknowledge(GenisysStationAddress.of(1));
        GenisysEvent controlEvent = new GenisysMessageEvent.MessageReceived(now, 1, controlResponse);
        GenisysStateReducer.Result afterControl = reducer.apply(afterRecall.newState(), controlEvent);

        assertEquals(GenisysSlaveState.Phase.POLL,
                afterControl.newState().slaves().get(1).phase());

        // 3) In POLL, receiving IndicationData means data was returned; master must acknowledge it.
        GenisysMessage pollData = new IndicationData(
                GenisysStationAddress.of(1),
                IndicationSet.empty(indicationIndex)
        );
        GenisysEvent pollDataEvent = new GenisysMessageEvent.MessageReceived(now, 1, pollData);
        GenisysStateReducer.Result result = reducer.apply(afterControl.newState(), pollDataEvent);

        assertTrue(result.newState().slaves().get(1).acknowledgmentPending());
    }

    // ---------------------------------------------------------------------
    // POLL timeout handling
    // ---------------------------------------------------------------------

    @Test
    void pollTimeoutIncrementsFailureAndRetries() {
        // Drive slave into POLL phase
        GenisysControllerState state = initialStateWithSlaves(1);

        IndicationSet indications = IndicationSet.empty(indicationIndex);
        GenisysEvent recallEvent = new GenisysMessageEvent.MessageReceived(
                now, 1, new IndicationData(GenisysStationAddress.of(1), indications));
        state = reducer.apply(state, recallEvent).newState();

        GenisysEvent controlEvent = new GenisysMessageEvent.MessageReceived(
                now, 1, new Acknowledge(GenisysStationAddress.of(1)));
        state = reducer.apply(state, controlEvent).newState();

        assertEquals(GenisysSlaveState.Phase.POLL,
                state.slaves().get(1).phase());

        // Inject a POLL timeout
        GenisysEvent timeout = new GenisysTimeoutEvent.ResponseTimeout(now, 1);
        GenisysStateReducer.Result result = reducer.apply(state, timeout);

        GenisysSlaveState updated = result.newState().slaves().get(1);

        // Failure count increments, phase remains POLL
        assertEquals(1, updated.consecutiveFailures());
        assertEquals(GenisysSlaveState.Phase.POLL, updated.phase());

        // Reducer requests retry of current protocol step
        assertTrue(result.intents().kinds()
                .contains(GenisysIntents.Kind.RETRY_CURRENT));
    }

    @Test
    void pollTimeoutTransitionsToFailedAfterThreshold() {
        // Drive slave into POLL phase
        GenisysControllerState state = initialStateWithSlaves(1);

        IndicationSet indications = IndicationSet.empty(indicationIndex);
        state = reducer.apply(state,
                        new GenisysMessageEvent.MessageReceived(
                                now, 1, new IndicationData(GenisysStationAddress.of(1), indications)))
                .newState();

        state = reducer.apply(state,
                        new GenisysMessageEvent.MessageReceived(
                                now, 1, new Acknowledge(GenisysStationAddress.of(1))))
                .newState();

        assertEquals(GenisysSlaveState.Phase.POLL,
                state.slaves().get(1).phase());

        // Apply three consecutive POLL timeouts
        state = reducer.apply(state, new GenisysTimeoutEvent.ResponseTimeout(now, 1))
                .newState();
        state = reducer.apply(state, new GenisysTimeoutEvent.ResponseTimeout(now, 1))
                .newState();

        GenisysStateReducer.Result result = reducer.apply(
                state, new GenisysTimeoutEvent.ResponseTimeout(now, 1));

        GenisysSlaveState failed = result.newState().slaves().get(1);

        assertEquals(GenisysSlaveState.Phase.FAILED, failed.phase());
        assertEquals(3, failed.consecutiveFailures());

        assertTrue(result.intents().kinds()
                .contains(GenisysIntents.Kind.SEND_RECALL));
        assertEquals(1, result.intents().targetStation());
    }

    // ---------------------------------------------------------------------
    // SEND_CONTROLS timeout handling
    // ---------------------------------------------------------------------

    @Test
    void sendControlsTimeoutRetriesControlDelivery() {
        // Start with a slave already in SEND_CONTROLS phase
        GenisysSlaveState sending = GenisysSlaveState.initial(1)
                .withPhase(GenisysSlaveState.Phase.SEND_CONTROLS, now)
                .withControlPending(true, now);

        GenisysControllerState state = GenisysControllerState.of(
                GenisysControllerState.GlobalState.RUNNING,
                Map.of(1, sending),
                now
        );

        // Inject a SEND_CONTROLS timeout
        GenisysEvent timeout = new GenisysTimeoutEvent.ResponseTimeout(now, 1);
        GenisysStateReducer.Result result = reducer.apply(state, timeout);

        GenisysSlaveState updated = result.newState().slaves().get(1);

        // Failure count increments, phase remains SEND_CONTROLS
        assertEquals(1, updated.consecutiveFailures());
        assertEquals(GenisysSlaveState.Phase.SEND_CONTROLS, updated.phase());

        // Control delivery should be retried
        assertTrue(result.intents().kinds()
                .contains(GenisysIntents.Kind.SEND_CONTROLS));
        assertEquals(1, result.intents().targetStation());
    }

    @Test
    void sendControlsTimeoutTransitionsToFailedAfterThreshold() {
        // Start with a slave already in SEND_CONTROLS phase
        GenisysSlaveState sending = GenisysSlaveState.initial(1)
                .withPhase(GenisysSlaveState.Phase.SEND_CONTROLS, now)
                .withControlPending(true, now);

        GenisysControllerState state = GenisysControllerState.of(
                GenisysControllerState.GlobalState.RUNNING,
                Map.of(1, sending),
                now
        );

        // Apply three consecutive SEND_CONTROLS timeouts
        state = reducer.apply(state, new GenisysTimeoutEvent.ResponseTimeout(now, 1))
                .newState();
        state = reducer.apply(state, new GenisysTimeoutEvent.ResponseTimeout(now, 1))
                .newState();

        GenisysStateReducer.Result result = reducer.apply(
                state, new GenisysTimeoutEvent.ResponseTimeout(now, 1));

        GenisysSlaveState failed = result.newState().slaves().get(1);

        assertEquals(GenisysSlaveState.Phase.FAILED, failed.phase());
        assertEquals(3, failed.consecutiveFailures());
        assertTrue(failed.controlPending());

        // Recovery via recall should begin
        assertTrue(result.intents().kinds()
                .contains(GenisysIntents.Kind.SEND_RECALL));
        assertEquals(1, result.intents().targetStation());
    }

    // ---------------------------------------------------------------------
    // RECALL timeout handling
    // ---------------------------------------------------------------------

    @Test
    void recallTimeoutRetriesRecallWithoutEscalation() {
        // Start with a slave already in RECALL phase
        GenisysSlaveState recalling = GenisysSlaveState.initial(1)
                .withPhase(GenisysSlaveState.Phase.RECALL, now);

        GenisysControllerState state = GenisysControllerState.of(
                GenisysControllerState.GlobalState.RUNNING,
                Map.of(1, recalling),
                now
        );

        // Inject a RECALL timeout
        GenisysEvent timeout = new GenisysTimeoutEvent.ResponseTimeout(now, 1);
        GenisysStateReducer.Result result = reducer.apply(state, timeout);

        GenisysSlaveState updated = result.newState().slaves().get(1);

        // Remain in RECALL and do not increment failure count
        assertEquals(GenisysSlaveState.Phase.RECALL, updated.phase());
        assertEquals(recalling.consecutiveFailures(), updated.consecutiveFailures());

        // Another recall attempt should be issued
        assertTrue(result.intents().kinds()
                .contains(GenisysIntents.Kind.SEND_RECALL));
        assertEquals(1, result.intents().targetStation());
    }

    // ---------------------------------------------------------------------
    // Control intent
    // ---------------------------------------------------------------------

    @Test
    void controlIntentMarksSlavesPending() {
        GenisysControllerState state = initialStateWithSlaves(1);

        ControlSet delta = ControlSet.empty(controlIndex);
        ControlSet full = ControlSet.empty(controlIndex);

        GenisysEvent event = new GenisysControlIntentEvent.ControlIntentChanged(
                now, delta, full);

        GenisysStateReducer.Result result = reducer.apply(state, event);

        GenisysSlaveState slave = result.newState().slaves().get(1);
        assertTrue(slave.controlPending());

        assertTrue(result.intents().kinds()
                .contains(GenisysIntents.Kind.SCHEDULE_CONTROL_DELIVERY));
    }

    // ---------------------------------------------------------------------
    // Failed slave recovery
    // ---------------------------------------------------------------------

    // Recovery from FAILED depends only on receipt of a valid message,
    // not on message type or content.
    @Test
    void failedSlaveRecoversOnValidMessage() {
        GenisysSlaveState failed = GenisysSlaveState.failed(1, 3, now);

        GenisysControllerState state = GenisysControllerState.of(
                GenisysControllerState.GlobalState.RUNNING,
                Map.of(1, failed),
                now
        );

        // Any valid semantic slave->master message should trigger recovery.
        // IndicationData is a canonical, always-valid slave response.
        IndicationSet indications = IndicationSet.empty(indicationIndex);

        GenisysMessage message = new IndicationData(
                GenisysStationAddress.of(1),
                indications
        );

        GenisysEvent event =
                new GenisysMessageEvent.MessageReceived(now, 1, message);

        GenisysStateReducer.Result result = reducer.apply(state, event);

        GenisysSlaveState updated = result.newState().slaves().get(1);

        assertEquals(GenisysSlaveState.Phase.RECALL, updated.phase());
        assertEquals(0, updated.consecutiveFailures());

        assertTrue(result.intents().kinds()
                .contains(GenisysIntents.Kind.SEND_RECALL));
    }
}
