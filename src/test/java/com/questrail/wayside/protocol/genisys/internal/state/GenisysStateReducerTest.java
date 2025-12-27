package com.questrail.wayside.protocol.genisys.internal.state;

import com.questrail.wayside.api.ControlId;
import com.questrail.wayside.api.SignalId;
import com.questrail.wayside.mapping.ArraySignalIndex;
import com.questrail.wayside.mapping.SignalIndex;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent;
import com.questrail.wayside.protocol.genisys.internal.GenisysFrame;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysFrameEvent;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysTransportEvent;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysControlIntentEvent;
import com.questrail.wayside.api.ControlSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
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

    final class TestControlId implements ControlId {
        private final int number;
        TestControlId(int number) { this.number = number; }
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

        GenisysFrame frame = new GenisysFrame((byte) 0xFD, 1, List.of(), false);
        GenisysEvent event = new GenisysFrameEvent.FrameReceived(now, frame);

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

    @Test
    void failedSlaveRecoversOnValidFrame() {
        GenisysSlaveState failed = GenisysSlaveState.failed(1, 3, now);

        GenisysControllerState state = GenisysControllerState.of(
                GenisysControllerState.GlobalState.RUNNING,
                Map.of(1, failed),
                now
        );

        GenisysFrame frame = new GenisysFrame((byte) 0xFB, 1, List.of(), false);
        GenisysEvent event = new GenisysFrameEvent.FrameReceived(now, frame);

        GenisysStateReducer.Result result = reducer.apply(state, event);

        GenisysSlaveState updated = result.newState().slaves().get(1);
        assertEquals(GenisysSlaveState.Phase.RECALL, updated.phase());
        assertEquals(0, updated.consecutiveFailures());

        assertTrue(result.intents().kinds()
                .contains(GenisysIntents.Kind.SEND_RECALL));
    }
}
