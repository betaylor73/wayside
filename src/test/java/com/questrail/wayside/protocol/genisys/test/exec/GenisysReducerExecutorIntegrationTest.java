package com.questrail.wayside.protocol.genisys.test.exec;

import com.questrail.wayside.protocol.genisys.internal.events.GenisysMessageEvent;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysTimeoutEvent;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysTransportEvent;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysControllerState;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysSlaveState;
import com.questrail.wayside.protocol.genisys.model.Acknowledge;
import com.questrail.wayside.protocol.genisys.model.IndicationData;
import com.questrail.wayside.protocol.genisys.model.GenisysStationAddress;
import com.questrail.wayside.api.IndicationId;
import com.questrail.wayside.api.IndicationSet;
import com.questrail.wayside.mapping.ArraySignalIndex;
import com.questrail.wayside.core.IndicationSetBuilder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GenisysReducerExecutorIntegrationTest
 * ------------------------------------
 *
 * Phase 1 semantic integration tests for reducer → executor behavior.
 *
 * These tests validate *protocol flows*, not isolated reducer branches
 * and not transport behavior.
 */
class GenisysReducerExecutorIntegrationTest {

    @Test
    void initializationRecallThenPollFlow() {
        // ------------------------------------------------------------------
        // GIVEN: a controller in its initial state with two slaves
        // ------------------------------------------------------------------
        Instant now = Instant.EPOCH;
        List<Integer> slaveAddresses = List.of(1, 2);
        GenisysControllerState initialState = GenisysControllerState.initializing(slaveAddresses, now);
        GenisysReducerExecutorHarness harness = new GenisysReducerExecutorHarness(initialState);

        // WHEN: the transport becomes available
        harness.apply(new GenisysTransportEvent.TransportUp(now));

        // THEN: initialization should be initiated (dominant intent)
        assertTrue(
                harness.executor().actions().stream()
                        .anyMatch(a -> a.kind() == RecordedAction.Kind.INITIALIZATION_STARTED),
                () -> "Initialization should begin on TransportUp; got: " + harness.executor().actions()
        );
        harness.executor().clear();

        // ------------------------------------------------------------------
        // Station 1 recall completes (semantic MessageReceived for station 1)
        // Reducer in RECALL → SEND_CONTROLS, executor sends controls
        // ------------------------------------------------------------------
        var s1 = 1;
        // Build a minimal IndicationData payload (full image acceptable as empty for test)
        // Use the real IndicationId type in this test by defining a local implementation
        record TestIndicationId(int number) implements IndicationId {
            public java.util.Optional<String> label() { return java.util.Optional.empty(); }
        }
        com.questrail.wayside.mapping.SignalIndex<IndicationId> indicationIndex =
                new ArraySignalIndex<>(new TestIndicationId(1));
        IndicationSet s1Image = com.questrail.wayside.api.IndicationSet.empty(indicationIndex);
        harness.apply(new GenisysMessageEvent.MessageReceived(now, s1,
                new IndicationData(GenisysStationAddress.of(s1), s1Image)));

        assertEquals(2, harness.executor().actions().size());
        assertEquals(RecordedAction.sentControls(s1), harness.executor().actions().get(0));
        assertEquals(RecordedAction.timerArmed(s1), harness.executor().actions().get(1));

        // State should have moved to SEND_CONTROLS for station 1
        GenisysSlaveState s1State = harness.state().slaves().get(s1);
        assertEquals(GenisysSlaveState.Phase.SEND_CONTROLS, s1State.phase());
        // make sure that we do not transition to RUNNING early
        assertEquals(GenisysControllerState.GlobalState.INITIALIZING, harness.state().globalState());
        harness.executor().clear();

        // ------------------------------------------------------------------
        // Station 1 acknowledges control delivery (any message advances SEND_CONTROLS → POLL)
        // Reducer emits POLL_NEXT, executor records SENT_POLL
        // ------------------------------------------------------------------
        harness.apply(new GenisysMessageEvent.MessageReceived(now, s1, new Acknowledge(null)));

        assertEquals(2, harness.executor().actions().size());
        assertEquals(RecordedAction.sentPoll(), harness.executor().actions().get(0));
        assertEquals(RecordedAction.Kind.TIMER_ARMED, harness.executor().actions().get(1).kind());

        s1State = harness.state().slaves().get(s1);
        assertEquals(GenisysSlaveState.Phase.POLL, s1State.phase());
        harness.executor().clear();

        // ------------------------------------------------------------------
        // Station 1 poll response with no data (Acknowledge)
        // Reducer maintains POLL, ackPending=false, emits POLL_NEXT
        // ------------------------------------------------------------------
        harness.apply(new GenisysMessageEvent.MessageReceived(now, s1, new Acknowledge(null)));

        assertEquals(2, harness.executor().actions().size());
        assertEquals(RecordedAction.sentPoll(), harness.executor().actions().get(0));
        assertEquals(RecordedAction.Kind.TIMER_ARMED, harness.executor().actions().get(1).kind());

        s1State = harness.state().slaves().get(s1);
        assertEquals(GenisysSlaveState.Phase.POLL, s1State.phase());
        assertFalse(s1State.acknowledgmentPending(), "Ack should not be pending after Acknowledge");
        harness.executor().clear();

        // ------------------------------------------------------------------
        // Station 2 recall completes (semantic MessageReceived for station 2)
        // Reducer in RECALL → SEND_CONTROLS, executor sends controls
        // ------------------------------------------------------------------
        var s2 = 2;
        // For recall completion, only IndicationData is valid per GENISYS
        record TestIndicationIdB(int number) implements IndicationId {
            public java.util.Optional<String> label() { return java.util.Optional.empty(); }
        }
        var idxB = new ArraySignalIndex<IndicationId>(new TestIndicationIdB(1));
        IndicationSet s2Image = IndicationSet.empty(idxB);
        harness.apply(new GenisysMessageEvent.MessageReceived(now, s2,
                new IndicationData(GenisysStationAddress.of(s2), s2Image)));

        assertEquals(2, harness.executor().actions().size());
        assertEquals(RecordedAction.sentControls(s2), harness.executor().actions().get(0));
        assertEquals(RecordedAction.timerArmed(s2), harness.executor().actions().get(1));
        harness.executor().clear();

        // Station 2 acknowledges control delivery → POLL_NEXT
        harness.apply(new GenisysMessageEvent.MessageReceived(now, s2, new Acknowledge(null)));

        assertEquals(2, harness.executor().actions().size());
        assertEquals(RecordedAction.sentPoll(), harness.executor().actions().get(0));
        assertEquals(RecordedAction.Kind.TIMER_ARMED, harness.executor().actions().get(1).kind());

        GenisysSlaveState s2State = harness.state().slaves().get(s2);
        assertEquals(GenisysSlaveState.Phase.POLL, s2State.phase());

        // At this point both stations have completed initial recall at least once,
        // so the controller must be in global RUNNING.
        assertEquals(GenisysControllerState.GlobalState.RUNNING, harness.state().globalState());
    }

    @Test
    void pollTimeoutRetryThenFailedAndRecallRecovery() {
        Instant now = Instant.EPOCH;
        GenisysControllerState initial = GenisysControllerState.initializing(List.of(1), now);
        GenisysReducerExecutorHarness h = new GenisysReducerExecutorHarness(initial);

        // Drive RECALL -> SEND_CONTROLS with IndicationData (full image acceptable as empty for test)
        record TestIndicationId2(int number) implements IndicationId {
            public java.util.Optional<String> label() { return java.util.Optional.empty(); }
        }
        var idx = new ArraySignalIndex<IndicationId>(new TestIndicationId2(1));
        IndicationSet img = IndicationSet.empty(idx);
        h.apply(new GenisysMessageEvent.MessageReceived(now, 1,
                new IndicationData(GenisysStationAddress.of(1), img)));
        assertTrue(h.executor().actions().contains(RecordedAction.sentControls(1)),
                () -> "Expected SENT_CONTROLS(1); got: " + h.executor().actions());
        h.executor().clear();

        // Drive SEND_CONTROLS -> POLL with any valid message (Acknowledge simplest)
        h.apply(new GenisysMessageEvent.MessageReceived(now, 1, new Acknowledge(null)));
        assertEquals(GenisysSlaveState.Phase.POLL, h.state().slaves().get(1).phase());
        h.executor().clear();

        // Two consecutive POLL timeouts -> RETRY_CURRENT each time, failure increments
        h.apply(new GenisysTimeoutEvent.ResponseTimeout(now, 1));
        assertTrue(h.executor().actions().contains(RecordedAction.retriedCurrent(1)),
                () -> "Expected RETRIED_CURRENT(1); got: " + h.executor().actions());
        assertEquals(1, h.state().slaves().get(1).consecutiveFailures());
        h.executor().clear();

        h.apply(new GenisysTimeoutEvent.ResponseTimeout(now, 1));
        assertTrue(h.executor().actions().contains(RecordedAction.retriedCurrent(1)));
        assertEquals(2, h.state().slaves().get(1).consecutiveFailures());
        h.executor().clear();

        // Third timeout -> FAILED and SEND_RECALL
        h.apply(new GenisysTimeoutEvent.ResponseTimeout(now, 1));
        assertEquals(GenisysSlaveState.Phase.FAILED, h.state().slaves().get(1).phase());
        assertEquals(3, h.state().slaves().get(1).consecutiveFailures());
        assertTrue(h.executor().actions().contains(RecordedAction.sentRecall(1)),
                () -> "Expected SENT_RECALL(1); got: " + h.executor().actions());
        h.executor().clear();

        // Recovery: receipt of a valid message while FAILED triggers RECALL + SEND_RECALL
        h.apply(new GenisysMessageEvent.MessageReceived(now, 1,
                new IndicationData(GenisysStationAddress.of(1), img)));
        assertEquals(GenisysSlaveState.Phase.RECALL, h.state().slaves().get(1).phase());
        assertTrue(h.executor().actions().contains(RecordedAction.sentRecall(1)));
    }

    @Test
    void transportDownSuppressesProtocolThenTransportUpReinitializes() {
        Instant now = Instant.EPOCH;
        GenisysControllerState initial = GenisysControllerState.initializing(List.of(1), now);
        GenisysReducerExecutorHarness h = new GenisysReducerExecutorHarness(initial);

        // Transport goes down -> dominant suspend intent
        h.apply(new GenisysTransportEvent.TransportDown(now));
        assertTrue(h.executor().actions().contains(RecordedAction.protocolSuspended()));
        assertTrue(h.executor().actions().contains(RecordedAction.allTimersCancelled()));
        h.executor().clear();

        // While down, even valid semantic messages produce no actions
        record TestIndicationId3(int number) implements IndicationId {
            public java.util.Optional<String> label() { return java.util.Optional.empty(); }
        }
        var idx2 = new ArraySignalIndex<IndicationId>(new TestIndicationId3(1));
        IndicationSet img2 = IndicationSet.empty(idx2);
        h.apply(new GenisysMessageEvent.MessageReceived(now, 1,
                new IndicationData(GenisysStationAddress.of(1), img2)));
        assertTrue(h.executor().actions().isEmpty(), "No protocol actions while TRANSPORT_DOWN");

        // Transport recovers -> begin initialization
        h.apply(new GenisysTransportEvent.TransportUp(now));
        assertTrue(h.executor().actions().contains(RecordedAction.initializationStarted()));
    }
}
