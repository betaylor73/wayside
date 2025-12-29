package com.questrail.wayside.protocol.genisys.internal.state;

import com.questrail.wayside.api.ControlId;
import com.questrail.wayside.api.IndicationId;
import com.questrail.wayside.api.IndicationSet;
import com.questrail.wayside.api.SignalState;
import com.questrail.wayside.protocol.genisys.model.GenisysMessage;
import com.questrail.wayside.mapping.ArraySignalIndex;
import com.questrail.wayside.mapping.SignalIndex;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysTransportEvent;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysControlIntentEvent;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysMessageEvent;
import com.questrail.wayside.api.ControlSet;
import com.questrail.wayside.protocol.genisys.model.GenisysStationAddress;
import com.questrail.wayside.protocol.genisys.model.IndicationData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    final class TestControlId implements ControlId {
        private final int number;
        TestControlId(int number) { this.number = number; }
        @Override public int number() { return number; }
        @Override public Optional<String> label() { return Optional.empty(); }
    }

    final class TestIndicationId implements IndicationId {
        private final int number;
        TestIndicationId(int number) { this.number = number; }
        @Override public int number() { return number; }
        @Override public Optional<String> label() { return Optional.empty(); }
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
