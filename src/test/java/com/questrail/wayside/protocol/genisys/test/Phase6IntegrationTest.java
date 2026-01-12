package com.questrail.wayside.protocol.genisys.test;

import com.questrail.wayside.api.ControlId;
import com.questrail.wayside.api.IndicationId;
import com.questrail.wayside.api.SignalId;
import com.questrail.wayside.core.ControlBitSetSignalSet;
import com.questrail.wayside.mapping.ArraySignalIndex;
import com.questrail.wayside.mapping.SignalIndex;
import com.questrail.wayside.protocol.genisys.config.GenisysStationConfig;
import com.questrail.wayside.protocol.genisys.observability.RecordingObservabilitySink;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysTransportEvent;
import com.questrail.wayside.protocol.genisys.internal.exec.TimedGenisysIntentExecutor;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysControllerState;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysIntents;
import com.questrail.wayside.protocol.genisys.observability.RecordingObservabilitySink;
import com.questrail.wayside.protocol.genisys.runtime.GenisysProductionRuntime;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase6IntegrationTest {

    private record TestControl(int number, String labelStr) implements ControlId {
        @Override public int number() { return number; }
        @Override public Optional<String> label() { return Optional.ofNullable(labelStr); }
    }

    private record TestIndication(int number, String labelStr) implements IndicationId {
        @Override public int number() { return number; }
        @Override public Optional<String> label() { return Optional.ofNullable(labelStr); }
    }

    @Test
    void productionRuntimeBuildsAndStarts() throws InterruptedException {
        SignalIndex<ControlId> controlIndex = new ArraySignalIndex<>(new TestControl(1, "CTL1"));
        SignalIndex<IndicationId> indicationIndex = new ArraySignalIndex<>(new TestIndication(1, "IND1"));

        GenisysStationConfig stations = GenisysStationConfig.builder()
            .addStation(1, new InetSocketAddress("localhost", 5001))
            .build();

        RecordingObservabilitySink sink = new RecordingObservabilitySink();

        GenisysProductionRuntime runtime = GenisysProductionRuntime.builder()
            .withStations(stations)
            .withControlIndex(controlIndex)
            .withIndicationIndex(indicationIndex)
            .withControlSetProvider(station -> new ControlBitSetSignalSet(controlIndex))
            .withObservabilitySink(sink)
            .build();

        assertNotNull(runtime);
        runtime.start();
        
        // Submit an event to trigger a state transition
        runtime.submitEvent(new GenisysTransportEvent.TransportUp(Instant.now()));
        
        Thread.sleep(100); // Allow some time for event processing
        runtime.stop();

        // Verify that we captured at least one state transition
        assertTrue(sink.getStateTransitions().size() >= 1, "Should have captured at least one state transition");
    }
}
