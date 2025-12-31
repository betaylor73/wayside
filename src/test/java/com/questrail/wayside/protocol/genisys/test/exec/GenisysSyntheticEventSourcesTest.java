package com.questrail.wayside.protocol.genisys.test.exec;

import com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysMessageEvent;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysTimeoutEvent;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysTransportEvent;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysControllerState;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysSlaveState;
import com.questrail.wayside.protocol.genisys.model.Acknowledge;
import com.questrail.wayside.protocol.genisys.model.GenisysStationAddress;
import com.questrail.wayside.protocol.genisys.model.IndicationData;
import com.questrail.wayside.api.IndicationId;
import com.questrail.wayside.api.IndicationSet;
import com.questrail.wayside.mapping.ArraySignalIndex;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GenisysSyntheticEventSourcesTest
 * --------------------------------
 *
 * Phase 2 tests: stress reducer+executor behavior using deterministic synthetic
 * event sequences.
 *
 * These tests do not introduce sockets, timers, or decoding.
 */
class GenisysSyntheticEventSourcesTest
{
    // Minimal IndicationId for tests (same style used elsewhere in the repo)
    record TestIndicationId(int number) implements IndicationId {
        @Override public java.util.Optional<String> label() { return java.util.Optional.empty(); }
    }

    @Test
    void duplicateMessageReceivedIsHandledDeterministically() {
        Instant now = Instant.EPOCH;
        GenisysControllerState initial = GenisysControllerState.initializing(List.of(1), now);
        GenisysSyntheticHarness h = new GenisysSyntheticHarness(initial);

        // TransportUp begins initialization.
        // Then two identical recall-completing messages arrive (duplicate IndicationData).
        // Phase 2 claim: duplicates must not violate invariants or cause nondeterminism.

        var idx = new ArraySignalIndex<IndicationId>(new TestIndicationId(1));
        IndicationSet img = IndicationSet.empty(idx);

        List<com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent> script = List.of(
                new GenisysTransportEvent.TransportUp(now),
                new GenisysMessageEvent.MessageReceived(now, 1, new IndicationData(GenisysStationAddress.of(1), img)),
                new GenisysMessageEvent.MessageReceived(now, 1, new IndicationData(GenisysStationAddress.of(1), img))
        );

        h.populateFrom(List.of(new ScriptedEventSource(script)));
        h.runToQuiescence();

        // After at least one successful recall, the single-slave controller must be RUNNING.
        assertEquals(GenisysControllerState.GlobalState.RUNNING, h.state().globalState());

        // The slave must not be left in RECALL.
        assertNotEquals(GenisysSlaveState.Phase.RECALL, h.state().slaves().get(1).phase());
    }

    @Test
    void messageAfterTimeoutDoesNotBreakDeterminism() {
        Instant now = Instant.EPOCH;
        GenisysControllerState initial = GenisysControllerState.initializing(List.of(1), now);
        GenisysSyntheticHarness h = new GenisysSyntheticHarness(initial);

        var idx = new ArraySignalIndex<IndicationId>(new TestIndicationId(1));
        IndicationSet img = IndicationSet.empty(idx);

        // Script:
        // - Recall completes
        // - Controls ack -> POLL
        // - Timeout occurs (retry)
        // - A late Acknowledge arrives (still a valid semantic message)
        //
        // Phase 2 intent: ordering may be odd, but reducer must remain coherent.

        List<com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent> script = List.of(
                new GenisysTransportEvent.TransportUp(now),
                new GenisysMessageEvent.MessageReceived(now, 1, new IndicationData(GenisysStationAddress.of(1), img)),
                new GenisysMessageEvent.MessageReceived(now, 1, new Acknowledge(null)),
                new GenisysTimeoutEvent.ResponseTimeout(now, 1),
                new GenisysMessageEvent.MessageReceived(now, 1, new Acknowledge(null))
        );

        h.populateFrom(List.of(new ScriptedEventSource(script)));
        h.runToQuiescence();

        // We remain in a valid per-slave phase; specifically, the reducer must not regress back to RECALL.
        assertNotNull(h.state().slaves().get(1));
        assertNotEquals(GenisysSlaveState.Phase.RECALL, h.state().slaves().get(1).phase());

        // Timeout handling is phase-dependent. A late valid response may legally
        // clear or mask a failure depending on current phase, so Phase 2 only
        // asserts *coherence*, not a specific failure count.
    }

    @Test
    void transportFlapInjectedMidCycleSuppressesNonTransportEvents() {
        Instant now = Instant.EPOCH;
        GenisysControllerState initial = GenisysControllerState.initializing(List.of(1), now);
        GenisysSyntheticHarness h = new GenisysSyntheticHarness(initial);

        // Phase 2 stress:
        // - TransportUp
        // - TransportDown
        // - Then non-transport events arrive; they must be ignored while TRANSPORT_DOWN.
        List<com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent> script = List.of(
                new GenisysTransportEvent.TransportUp(now),
                new GenisysTransportEvent.TransportDown(now),
                new GenisysTimeoutEvent.ResponseTimeout(now, 1),
                new GenisysMessageEvent.MessageReceived(now, 1, new Acknowledge(null))
        );

        h.populateFrom(List.of(new ScriptedEventSource(script)));
        h.runToQuiescence();

        assertEquals(GenisysControllerState.GlobalState.TRANSPORT_DOWN, h.state().globalState());

        // While transport is down, per normative gating, per-slave state must not mutate.
        // In the initial state the slave is in RECALL; it must still be RECALL.
        assertEquals(GenisysSlaveState.Phase.RECALL, h.state().slaves().get(1).phase());
    }

    @Test
    void deterministicShuffleProducesStableEndStateAcrossRuns() {
        Instant now = Instant.EPOCH;
        GenisysControllerState initialA = GenisysControllerState.initializing(List.of(1), now);
        GenisysControllerState initialB = GenisysControllerState.initializing(List.of(1), now);

        var idx = new ArraySignalIndex<IndicationId>(new TestIndicationId(1));
        IndicationSet img = IndicationSet.empty(idx);

        List<com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent> base = List.of(
                new GenisysTransportEvent.TransportUp(now),
                new GenisysMessageEvent.MessageReceived(now, 1, new IndicationData(GenisysStationAddress.of(1), img)),
                new GenisysMessageEvent.MessageReceived(now, 1, new Acknowledge(null)),
                new GenisysTimeoutEvent.ResponseTimeout(now, 1)
        );

        // Same seed => same order => same end state.
        long seed = 0xC0FFEE;

        GenisysSyntheticHarness h1 = new GenisysSyntheticHarness(initialA);
        h1.populateFrom(List.of(new DeterministicShuffleEventSource(base, seed)));
        h1.runToQuiescence();

        GenisysSyntheticHarness h2 = new GenisysSyntheticHarness(initialB);
        h2.populateFrom(List.of(new DeterministicShuffleEventSource(base, seed)));
        h2.runToQuiescence();

        assertEquals(h1.state().globalState(), h2.state().globalState());
        assertEquals(h1.state().slaves().get(1).phase(), h2.state().slaves().get(1).phase());
        assertEquals(h1.state().slaves().get(1).consecutiveFailures(), h2.state().slaves().get(1).consecutiveFailures());
    }

    @Test
    void failureCounterIncrementsMonotonicallyUnderRepeatedTimeouts() {
        Instant now = Instant.EPOCH;
        GenisysControllerState initial = GenisysControllerState.initializing(List.of(1), now);
        GenisysSyntheticHarness h = new GenisysSyntheticHarness(initial);

        // This test exercises a true invariant:
        // while a slave remains in POLL (no valid responses), each ResponseTimeout
        // must monotonically increment the consecutive failure counter.

        var idx = new ArraySignalIndex<IndicationId>(new TestIndicationId(1));
        IndicationSet img = IndicationSet.empty(idx);

        List<GenisysEvent> script = List.of(
                new GenisysTransportEvent.TransportUp(now),
                // Complete recall
                new GenisysMessageEvent.MessageReceived(now, 1,
                        new IndicationData(GenisysStationAddress.of(1), img)),
                // First poll timeout
                new GenisysTimeoutEvent.ResponseTimeout(now, 1),
                // Second poll timeout
                new GenisysTimeoutEvent.ResponseTimeout(now, 1)
        );

        h.populateFrom(List.of(new ScriptedEventSource(script)));
        h.runToQuiescence();

        GenisysSlaveState slave = h.state().slaves().get(1);
        assertNotNull(slave);

        // Invariant: without intervening valid responses, failures must increase monotonically
        assertEquals(2, slave.consecutiveFailures());
    }

    @Test
    void multiSlaveIsolationUnderAdversarialOrdering() {
        Instant now = Instant.EPOCH;

        // Two slaves, independent contexts
        GenisysControllerState initial = GenisysControllerState.initializing(List.of(1, 2), now);
        GenisysSyntheticHarness h = new GenisysSyntheticHarness(initial);

        var idx1 = new ArraySignalIndex<IndicationId>(new TestIndicationId(1));
        var idx2 = new ArraySignalIndex<IndicationId>(new TestIndicationId(2));
        IndicationSet img1 = IndicationSet.empty(idx1);
        IndicationSet img2 = IndicationSet.empty(idx2);

        // Adversarial, interleaved sequence:
        //  - Slave 1 completes recall
        //  - Slave 2 times out repeatedly
        //  - Slave 1 receives a valid acknowledge
        //  - No events for slave 2 that would clear its failures
        List<com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent> script = List.of(
                new GenisysTransportEvent.TransportUp(now),

                // Slave 1 recall completes
                new GenisysMessageEvent.MessageReceived(now, 1,
                        new IndicationData(GenisysStationAddress.of(1), img1)),

                // Slave 2 completes recall first (so that subsequent timeouts are counted)
                new GenisysMessageEvent.MessageReceived(now, 2,
                        new IndicationData(GenisysStationAddress.of(2), img2)),

                // Slave 2 experiences repeated timeouts while in POLL
                new GenisysTimeoutEvent.ResponseTimeout(now, 2),
                new GenisysTimeoutEvent.ResponseTimeout(now, 2),

                // Slave 1 progresses normally
                new GenisysMessageEvent.MessageReceived(now, 1, new Acknowledge(null))
        );

        h.populateFrom(List.of(new ScriptedEventSource(script)));
        h.runToQuiescence();

        GenisysSlaveState slave1 = h.state().slaves().get(1);
        GenisysSlaveState slave2 = h.state().slaves().get(2);

        assertNotNull(slave1);
        assertNotNull(slave2);

        // Slave 1 must not be affected by slave 2 failures
        assertNotEquals(GenisysSlaveState.Phase.RECALL, slave1.phase());

        // Slave 2 failures must accumulate independently
        assertEquals(2, slave2.consecutiveFailures());

        // Global state must remain coherent (initializing or running depending on recall completeness)
        assertNotEquals(GenisysControllerState.GlobalState.TRANSPORT_DOWN,
                h.state().globalState());
    }

    @Test
    void transportDownMidCycleForcesGlobalReinitialization() {
        Instant now = Instant.EPOCH;

        // Two slaves, both recalled and operational
        GenisysControllerState initial = GenisysControllerState.initializing(List.of(1, 2), now);
        GenisysSyntheticHarness h = new GenisysSyntheticHarness(initial);

        var idx1 = new ArraySignalIndex<IndicationId>(new TestIndicationId(1));
        var idx2 = new ArraySignalIndex<IndicationId>(new TestIndicationId(2));
        IndicationSet img1 = IndicationSet.empty(idx1);
        IndicationSet img2 = IndicationSet.empty(idx2);

        // Phase 2 lifecycle stress:
        //  - TransportUp
        //  - Both slaves complete recall
        //  - TransportDown occurs mid-cycle
        //  - Stale timeouts/messages arrive (must be ignored)
        //  - TransportUp forces full re-initialization
        List<GenisysEvent> script = List.of(
                new GenisysTransportEvent.TransportUp(now),

                new GenisysMessageEvent.MessageReceived(now, 1,
                        new IndicationData(GenisysStationAddress.of(1), img1)),
                new GenisysMessageEvent.MessageReceived(now, 2,
                        new IndicationData(GenisysStationAddress.of(2), img2)),

                // Transport drops mid-cycle
                new GenisysTransportEvent.TransportDown(now),

                // Stale events that must be ignored
                new GenisysTimeoutEvent.ResponseTimeout(now, 1),
                new GenisysMessageEvent.MessageReceived(now, 2, new Acknowledge(null)),

                // Transport recovers
                new GenisysTransportEvent.TransportUp(now)
        );

        h.populateFrom(List.of(new ScriptedEventSource(script)));
        h.runToQuiescence();

        // After TransportDown, global state must have been TRANSPORT_DOWN
        // After TransportUp, system must re-enter INITIALIZING
        assertEquals(GenisysControllerState.GlobalState.INITIALIZING,
                h.state().globalState());

        // After TransportUp, slaves must be re-initialized for recall.
        // Note: per reducer semantics, immediate executor-driven intents may
        // advance slaves out of RECALL into SEND_CONTROLS without waiting
        // for another external event. Phase 2 therefore asserts *non-operational* state,
        // not a specific transient phase.
        assertNotEquals(GenisysSlaveState.Phase.POLL, h.state().slaves().get(1).phase());
        assertNotEquals(GenisysSlaveState.Phase.POLL, h.state().slaves().get(2).phase());
    }
}
