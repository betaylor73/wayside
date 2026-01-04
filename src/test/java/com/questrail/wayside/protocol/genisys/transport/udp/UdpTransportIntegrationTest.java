package com.questrail.wayside.protocol.genisys.transport.udp;

import com.questrail.wayside.api.ControlId;
import com.questrail.wayside.api.ControlSet;
import com.questrail.wayside.api.IndicationId;
import com.questrail.wayside.api.IndicationSet;
import com.questrail.wayside.mapping.ArraySignalIndex;
import com.questrail.wayside.mapping.SignalIndex;
import com.questrail.wayside.protocol.genisys.GenisysWaysideController;
import com.questrail.wayside.protocol.genisys.codec.impl.DefaultGenisysFrameDecoder;
import com.questrail.wayside.protocol.genisys.codec.impl.DefaultGenisysFrameEncoder;
import com.questrail.wayside.protocol.genisys.internal.decode.GenisysMessageDecoder;
import com.questrail.wayside.protocol.genisys.internal.encode.GenisysMessageEncoder;
import com.questrail.wayside.protocol.genisys.internal.exec.GenisysIntentExecutor;
import com.questrail.wayside.protocol.genisys.internal.frame.GenisysFrame;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysControllerState;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysIntents;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysStateReducer;
import com.questrail.wayside.protocol.genisys.model.ControlData;
import com.questrail.wayside.protocol.genisys.model.GenisysMessage;
import com.questrail.wayside.protocol.genisys.model.GenisysStationAddress;
import com.questrail.wayside.protocol.genisys.model.IndicationData;
import com.questrail.wayside.protocol.genisys.transport.FakeDatagramEndpoint;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

final class UdpTransportIntegrationTest {

    /**
     * Delegating executor used only to break the construction cycle:
     *
     *   controller -> executor -> adapter -> controller
     *
     * The production runtime achieves this by composing in a single place.
     * In tests we keep the same architecture but make the cycle explicit.
     */
    private static final class DelegatingIntentExecutor implements GenisysIntentExecutor {
        private volatile GenisysIntentExecutor delegate;

        void setDelegate(GenisysIntentExecutor delegate) {
            this.delegate = delegate;
        }

        @Override
        public void execute(GenisysIntents intents) {
            GenisysIntentExecutor d = delegate;
            if (d == null) {
                throw new IllegalStateException("delegate not set");
            }
            d.execute(intents);
        }
    }

    record TestControlId(int number) implements ControlId {
        @Override public Optional<String> label() { return Optional.empty(); }
    }

    record TestIndicationId(int number) implements IndicationId {
        @Override public Optional<String> label() { return Optional.empty(); }
    }

    @Test
    void inboundDatagramIsDecodedAndDrivesOutboundSendViaIntents() {
        Instant now = Instant.EPOCH;

        // Station routing (integration-only wiring)
        SocketAddress remote = new InetSocketAddress("127.0.0.1", 9100);
        Map<Integer, SocketAddress> routing = Map.of(1, remote);

        // ControlSet provider (integration-only wiring)
        SignalIndex<ControlId> controlIndex = new ArraySignalIndex<>(
                new TestControlId(1),
                new TestControlId(2),
                new TestControlId(3)
        );
        ControlSet controls = ControlSet.empty(controlIndex);

        SignalIndex<IndicationId> indicationIndex = new ArraySignalIndex<>(
                new TestIndicationId(10),
                new TestIndicationId(11)
        );
        IndicationSet image = IndicationSet.empty(indicationIndex);

        // GIVEN: controller state where station 1 is in initialization RECALL phase.
        GenisysControllerState initial = GenisysControllerState.initializing(List.of(1), now);

        DelegatingIntentExecutor delegating = new DelegatingIntentExecutor();
        GenisysWaysideController controller = new GenisysWaysideController(
                initial,
                new GenisysStateReducer(),
                delegating
        );

        FakeDatagramEndpoint endpoint = new FakeDatagramEndpoint();

        // Real codec pipeline (frozen boundary)
        DefaultGenisysFrameDecoder frameDecoder = new DefaultGenisysFrameDecoder();
        DefaultGenisysFrameEncoder frameEncoder = new DefaultGenisysFrameEncoder();
        GenisysMessageDecoder messageDecoder = new GenisysMessageDecoder(
                p -> image,
                p -> controls
        );
        GenisysMessageEncoder messageEncoder = new GenisysMessageEncoder(
                i -> new byte[image.allSignals().size() / 8 + 1],
                c -> new byte[controls.allSignals().size() / 8 + 1]
        );

        // UDP adapter under test
        UdpTransportAdapter adapter = new UdpTransportAdapter(
                controller,
                endpoint,
                frameDecoder,
                frameEncoder,
                messageDecoder,
                messageEncoder
        );

        UdpGenisysIntentExecutor udpExecutor = new UdpGenisysIntentExecutor(
                adapter,
                controller::state,
                routing::get,
                station -> controls,
                false
        );
        delegating.setDelegate(udpExecutor);

        // WHEN: we inject a syntactically valid IndicationData datagram for station 1
        GenisysMessage inbound = new IndicationData(GenisysStationAddress.of(1), image);

        byte[] inboundDatagram = frameEncoder.encode(messageEncoder.encode(inbound));
        endpoint.injectDatagram(remote, inboundDatagram);

        // THEN: reducer should emit SEND_CONTROLS intent which the UDP executor realizes as an outbound datagram
        assertEquals(1, endpoint.sent().size(), "Expected exactly one outbound UDP datagram");

        byte[] outboundDatagram = endpoint.sent().get(0).payload();
        Optional<GenisysFrame> frameOpt = frameDecoder.decode(outboundDatagram);
        assertTrue(frameOpt.isPresent(), "Outbound datagram should decode as a frame");

        GenisysMessage outboundMsg = messageDecoder.decode(frameOpt.get());
        assertTrue(outboundMsg instanceof ControlData, "Outbound message should be ControlData");
        assertEquals(1, outboundMsg.station().value());
    }

    @Test
    void invalidDatagramIsDroppedAndProducesNoOutboundSend() {
        Instant now = Instant.EPOCH;

        GenisysControllerState initial = GenisysControllerState.initializing(List.of(1), now);

        DelegatingIntentExecutor delegating = new DelegatingIntentExecutor();
        GenisysWaysideController controller = new GenisysWaysideController(
                initial,
                new GenisysStateReducer(),
                delegating
        );

        FakeDatagramEndpoint endpoint = new FakeDatagramEndpoint();

        DefaultGenisysFrameDecoder frameDecoder = new DefaultGenisysFrameDecoder();
        DefaultGenisysFrameEncoder frameEncoder = new DefaultGenisysFrameEncoder();
        GenisysMessageDecoder messageDecoder = new GenisysMessageDecoder(
                p -> IndicationSet.empty(new ArraySignalIndex<>()),
                p -> ControlSet.empty(new ArraySignalIndex<>())
        );
        GenisysMessageEncoder messageEncoder = new GenisysMessageEncoder(
                i -> new byte[0],
                c -> new byte[0]
        );

        UdpTransportAdapter adapter = new UdpTransportAdapter(
                controller,
                endpoint,
                frameDecoder,
                frameEncoder,
                messageDecoder,
                messageEncoder
        );

        SocketAddress remote = new InetSocketAddress("127.0.0.1", 9100);
        Map<Integer, SocketAddress> routing = Map.of(1, remote);

        SignalIndex<ControlId> controlIndex = new ArraySignalIndex<>(new TestControlId(1));
        ControlSet controls = ControlSet.empty(controlIndex);

        UdpGenisysIntentExecutor udpExecutor = new UdpGenisysIntentExecutor(
                adapter,
                controller::state,
                routing::get,
                station -> controls,
                false
        );
        delegating.setDelegate(udpExecutor);

        // WHEN: inject garbage bytes that cannot possibly decode as a GENISYS frame
        endpoint.injectDatagram(remote, new byte[] { 0x01, 0x02, 0x03, 0x04 });

        // THEN: adapter drops it and nothing is sent
        assertEquals(0, endpoint.sent().size());
    }

    @Test
    void transportUpEventIsSubmittedToController() {
        MockController controller = new MockController();
        FakeDatagramEndpoint endpoint = new FakeDatagramEndpoint();

        new UdpTransportAdapter(
                controller, endpoint,
                new DefaultGenisysFrameDecoder(), new DefaultGenisysFrameEncoder(),
                new GenisysMessageDecoder(p -> null, p -> null),
                new GenisysMessageEncoder(i -> null, c -> null)
        );

        // WHEN: start endpoint (should call listener.onTransportUp)
        endpoint.start();

        // THEN
        assertEquals(1, controller.events.size());
        assertTrue(controller.events.get(0)
                instanceof com.questrail.wayside.protocol.genisys.internal.events.GenisysTransportEvent.TransportUp);
        assertEquals(1, controller.drainCalls);
    }

    @Test
    void transportDownEventIsSubmittedToController() {
        MockController controller = new MockController();
        FakeDatagramEndpoint endpoint = new FakeDatagramEndpoint();

        new UdpTransportAdapter(
                controller, endpoint,
                new DefaultGenisysFrameDecoder(), new DefaultGenisysFrameEncoder(),
                new GenisysMessageDecoder(p -> null, p -> null),
                new GenisysMessageEncoder(i -> null, c -> null)
        );

        // WHEN: stop endpoint (should call listener.onTransportDown)
        endpoint.stop();

        // THEN
        assertEquals(1, controller.events.size());
        assertTrue(controller.events.get(0)
                instanceof com.questrail.wayside.protocol.genisys.internal.events.GenisysTransportEvent.TransportDown);
        assertEquals(1, controller.drainCalls);
    }

    @Test
    void outboundOnlySendRecallIntentProducesUdpDatagram() {
        Instant now = Instant.EPOCH;

        // Station routing (integration-only wiring)
        SocketAddress remote = new InetSocketAddress("127.0.0.1", 9100);
        Map<Integer, SocketAddress> routing = Map.of(1, remote);

        // ControlSet provider (integration-only wiring)
        SignalIndex<ControlId> controlIndex = new ArraySignalIndex<>(
                new TestControlId(1),
                new TestControlId(2),
                new TestControlId(3)
        );
        ControlSet controls = ControlSet.empty(controlIndex);

        SignalIndex<IndicationId> indicationIndex = new ArraySignalIndex<>(
                new TestIndicationId(10),
                new TestIndicationId(11)
        );
        IndicationSet image = IndicationSet.empty(indicationIndex);

        GenisysControllerState initial = GenisysControllerState.initializing(List.of(1), now);

        DelegatingIntentExecutor delegating = new DelegatingIntentExecutor();
        GenisysWaysideController controller = new GenisysWaysideController(
                initial,
                new GenisysStateReducer(),
                delegating
        );

        FakeDatagramEndpoint endpoint = new FakeDatagramEndpoint();

        // Real codec pipeline (frozen boundary)
        DefaultGenisysFrameDecoder frameDecoder = new DefaultGenisysFrameDecoder();
        DefaultGenisysFrameEncoder frameEncoder = new DefaultGenisysFrameEncoder();
        GenisysMessageDecoder messageDecoder = new GenisysMessageDecoder(
                p -> image,
                p -> controls
        );
        GenisysMessageEncoder messageEncoder = new GenisysMessageEncoder(
                i -> new byte[image.allSignals().size() / 8 + 1],
                c -> new byte[controls.allSignals().size() / 8 + 1]
        );

        UdpTransportAdapter adapter = new UdpTransportAdapter(
                controller,
                endpoint,
                frameDecoder,
                frameEncoder,
                messageDecoder,
                messageEncoder
        );

        UdpGenisysIntentExecutor udpExecutor = new UdpGenisysIntentExecutor(
                adapter,
                controller::state,
                routing::get,
                station -> controls,
                false
        );
        delegating.setDelegate(udpExecutor);

        // WHEN: execute a SEND_RECALL intent directly (no inbound stimulus)
        udpExecutor.execute(GenisysIntents.sendRecall(1));

        // THEN: exactly one outbound datagram should be sent, decoding to Recall
        assertEquals(1, endpoint.sent().size(), "Expected exactly one outbound UDP datagram");

        byte[] outboundDatagram = endpoint.sent().get(0).payload();
        Optional<GenisysFrame> frameOpt = frameDecoder.decode(outboundDatagram);
        assertTrue(frameOpt.isPresent(), "Outbound datagram should decode as a frame");

        GenisysMessage outboundMsg = messageDecoder.decode(frameOpt.get());
        assertTrue(outboundMsg instanceof com.questrail.wayside.protocol.genisys.model.Recall,
                "Outbound message should be Recall");
        assertEquals(1, outboundMsg.station().value());
    }

    // Helper for manual verification
    private static final class MockController extends GenisysWaysideController {
        public List<com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent> events = new java.util.ArrayList<>();
        public int drainCalls = 0;
        MockController() {
            super(GenisysControllerState.initializing(List.of(), Instant.EPOCH), new GenisysStateReducer(), intents -> {});
        }
        @Override public void submit(com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent event) { events.add(event); }
        @Override public void drain() { drainCalls++; }
    }
}
