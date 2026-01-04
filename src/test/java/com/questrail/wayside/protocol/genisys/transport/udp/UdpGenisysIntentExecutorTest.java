package com.questrail.wayside.protocol.genisys.transport.udp;

import com.questrail.wayside.api.ControlId;
import com.questrail.wayside.api.ControlSet;
import com.questrail.wayside.mapping.ArraySignalIndex;
import com.questrail.wayside.mapping.SignalIndex;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysControllerState;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysIntents;
import com.questrail.wayside.protocol.genisys.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UdpGenisysIntentExecutorTest {

    private final class MockTransport extends UdpTransportAdapter {
        final List<Sent> sent = new ArrayList<>();

        record Sent(SocketAddress remote, GenisysMessage message) {}

        MockTransport() {
            super(
                new com.questrail.wayside.protocol.genisys.GenisysWaysideController(
                    GenisysControllerState.initializing(List.of(), Instant.EPOCH),
                    new com.questrail.wayside.protocol.genisys.internal.state.GenisysStateReducer(),
                    intents -> {}
                ),
                new com.questrail.wayside.protocol.genisys.transport.FakeDatagramEndpoint(),
                new com.questrail.wayside.protocol.genisys.codec.impl.DefaultGenisysFrameDecoder(),
                new com.questrail.wayside.protocol.genisys.codec.impl.DefaultGenisysFrameEncoder(),
                new com.questrail.wayside.protocol.genisys.internal.decode.GenisysMessageDecoder(p->null, p->null),
                new com.questrail.wayside.protocol.genisys.internal.encode.GenisysMessageEncoder(i->null, c->null)
            );
        }

        @Override
        public void send(SocketAddress remote, GenisysMessage message) {
            sent.add(new Sent(remote, message));
        }
    }

    private MockTransport transport;
    private GenisysControllerState state;
    private UdpGenisysIntentExecutor executor;
    private ControlSet controlSet;
    private SocketAddress remote1 = new InetSocketAddress("127.0.0.1", 9101);
    private SocketAddress remote2 = new InetSocketAddress("127.0.0.1", 9102);

    record TestControlId(int number) implements ControlId {
        @Override public Optional<String> label() { return Optional.empty(); }
    }

    @BeforeEach
    void setUp() {
        transport = new MockTransport();
        state = GenisysControllerState.initializing(List.of(1, 2), Instant.EPOCH);
        
        SignalIndex<ControlId> controlIndex = new ArraySignalIndex<>(new TestControlId(1));
        controlSet = ControlSet.empty(controlIndex);

        executor = new UdpGenisysIntentExecutor(
                transport,
                () -> state,
                station -> station == 1 ? remote1 : (station == 2 ? remote2 : null),
                station -> controlSet,
                false
        );
    }

    @Test
    void emptyIntentsProduceNoOutput() {
        executor.execute(GenisysIntents.none());
        assertTrue(transport.sent.isEmpty());
    }

    @Test
    void suspendAllIntentIsDominantAndSuppressesOthers() {
        GenisysIntents intents = GenisysIntents.suspendAll()
                .and(GenisysIntents.sendRecall(1));
        
        executor.execute(intents);
        assertTrue(transport.sent.isEmpty());
    }

    @Test
    void beginInitializationIntentIsDominantAndSuppressesOthers() {
        GenisysIntents intents = GenisysIntents.beginInitialization()
                .and(GenisysIntents.sendRecall(1));
        
        executor.execute(intents);
        assertTrue(transport.sent.isEmpty());
    }

    @Test
    void sendRecallIntentSendsRecallMessage() {
        executor.execute(GenisysIntents.sendRecall(1));
        
        assertEquals(1, transport.sent.size());
        assertEquals(remote1, transport.sent.get(0).remote);
        assertTrue(transport.sent.get(0).message instanceof Recall);
    }

    @Test
    void sendControlsIntentSendsControldataMessage() {
        executor.execute(GenisysIntents.sendControls(2));
        
        assertEquals(1, transport.sent.size());
        assertEquals(remote2, transport.sent.get(0).remote);
        assertTrue(transport.sent.get(0).message instanceof ControlData);
    }

    @Test
    void pollNextIntentSelectsFirstStationIfNoneSpecified() {
        executor.execute(GenisysIntents.pollNext(0)); // 0 is not 1 or 2, should pick first
        
        // selectNextStation for unknown current should return first station (1)
        assertEquals(1, transport.sent.size());
        assertEquals(remote1, transport.sent.get(0).remote);
        assertTrue(transport.sent.get(0).message instanceof Poll);
    }

    @Test
    void pollNextIntentCyclesThroughStations() {
        executor.execute(GenisysIntents.pollNext(1));
        assertEquals(1, transport.sent.size());
        assertEquals(remote2, transport.sent.get(0).remote);
        
        transport.sent.clear();
        executor.execute(GenisysIntents.pollNext(2));
        assertEquals(1, transport.sent.size());
        assertEquals(remote1, transport.sent.get(0).remote);
    }

    @Test
    void pollNextSendsAcknowledgeAndPollWhenAckPending() {
        // GIVEN: station 1 has acknowledgmentPending = true
        Instant now = Instant.EPOCH;
        state = state.withSlaveState(
                state.slaves().get(1).withAcknowledgmentPending(true, now),
                now
        );

        // WHEN: poll next after station 0 (unknown -> selects first station 1)
        executor.execute(GenisysIntents.pollNext(0));

        // THEN: it should send AcknowledgeAndPoll to station 1
        assertEquals(1, transport.sent.size());
        assertEquals(remote1, transport.sent.get(0).remote);
        assertTrue(transport.sent.get(0).message instanceof AcknowledgeAndPoll);
    }

    @Test
    void pollNextRespectsSecurePollsConfiguration() {
        // GIVEN: an executor configured with securePolls = true
        UdpGenisysIntentExecutor secureExecutor = new UdpGenisysIntentExecutor(
                transport,
                () -> state,
                station -> station == 1 ? remote1 : (station == 2 ? remote2 : null),
                station -> controlSet,
                true
        );

        // Ensure ack is not pending so we get Poll (not AcknowledgeAndPoll)
        Instant now = Instant.EPOCH;
        state = state.withSlaveState(
                state.slaves().get(1).withAcknowledgmentPending(false, now),
                now
        );

        // WHEN: poll next selecting station 1
        secureExecutor.execute(GenisysIntents.pollNext(0));

        // THEN: message is Poll with secure flag enabled
        assertEquals(1, transport.sent.size());
        assertEquals(remote1, transport.sent.get(0).remote);
        assertTrue(transport.sent.get(0).message instanceof Poll);
        assertTrue(((Poll) transport.sent.get(0).message).secure());
    }


    @Test
    void retryCurrentIntentResendsLastMessage() {
        // First send something
        executor.execute(GenisysIntents.sendRecall(1));
        assertEquals(1, transport.sent.size());
        
        // Then retry
        executor.execute(GenisysIntents.retryCurrent().and(GenisysIntents.none().builder().targetStation(1).build()));
        
        assertEquals(2, transport.sent.size());
        assertTrue(transport.sent.get(1).message instanceof Recall);
    }
}
