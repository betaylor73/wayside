package com.questrail.wayside.protocol.genisys.test;

import com.questrail.wayside.api.ControlId;
import com.questrail.wayside.api.ControlSet;
import com.questrail.wayside.api.ControllerStatus;
import com.questrail.wayside.api.IndicationId;
import com.questrail.wayside.api.SignalState;
import com.questrail.wayside.core.ControlBitSetSignalSet;
import com.questrail.wayside.mapping.ArraySignalIndex;
import com.questrail.wayside.mapping.SignalIndex;
import com.questrail.wayside.protocol.genisys.GenisysProductionController;
import com.questrail.wayside.protocol.genisys.config.GenisysStationConfig;
import com.questrail.wayside.protocol.genisys.internal.exec.GenisysTimingPolicy;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysControllerState;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysSlaveState;
import com.questrail.wayside.protocol.genisys.observability.NullObservabilitySink;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenisysProductionControllerTest {

    private record TestControl(int number, String labelStr) implements ControlId {
        @Override public int number() { return number; }
        @Override public Optional<String> label() { return Optional.ofNullable(labelStr); }
    }

    private record TestIndication(int number, String labelStr) implements IndicationId {
        @Override public int number() { return number; }
        @Override public Optional<String> label() { return Optional.ofNullable(labelStr); }
    }

    @Test
    void statusMappingCorrect() {
        SignalIndex<ControlId> controlIndex = new ArraySignalIndex<>(new TestControl(1, "CTL1"));
        SignalIndex<IndicationId> indicationIndex = new ArraySignalIndex<>(new TestIndication(1, "IND1"));
        GenisysStationConfig stations = GenisysStationConfig.builder()
            .addStation(1, new InetSocketAddress("localhost", 5001))
            .build();

        GenisysProductionController controller = new GenisysProductionController(
            controlIndex,
            indicationIndex,
            stations,
            GenisysTimingPolicy.defaults(),
            NullObservabilitySink.INSTANCE
        );

        // Test TRANSPORT_DOWN
        GenisysControllerState transportDown = GenisysControllerState.of(
            GenisysControllerState.GlobalState.TRANSPORT_DOWN,
            Map.of(),
            Instant.now()
        );
        controller.updateStatusFromState(transportDown);
        assertEquals(ControllerStatus.DISCONNECTED, controller.getStatus());

        // Test INITIALIZING
        GenisysControllerState initializing = GenisysControllerState.initializing(List.of(1), Instant.now());
        controller.updateStatusFromState(initializing);
        assertEquals(ControllerStatus.DISCONNECTED, controller.getStatus());

        // Test RUNNING -> CONNECTED
        GenisysSlaveState slave1 = GenisysSlaveState.initial(1).withPhase(GenisysSlaveState.Phase.POLL, Instant.now());
        GenisysControllerState running = GenisysControllerState.of(
            GenisysControllerState.GlobalState.RUNNING,
            Map.of(1, slave1),
            Instant.now()
        );
        controller.updateStatusFromState(running);
        assertEquals(ControllerStatus.CONNECTED, controller.getStatus());

        // Test RUNNING -> DEGRADED
        GenisysStationConfig twoStations = GenisysStationConfig.builder()
            .addStation(1, new InetSocketAddress("localhost", 5001))
            .addStation(2, new InetSocketAddress("localhost", 5002))
            .build();
        GenisysProductionController controller2 = new GenisysProductionController(
            controlIndex, indicationIndex, twoStations, GenisysTimingPolicy.defaults(), NullObservabilitySink.INSTANCE);
        
        GenisysSlaveState slave2Failed = GenisysSlaveState.failed(2, 5, Instant.now());
        GenisysControllerState degraded = GenisysControllerState.of(
            GenisysControllerState.GlobalState.RUNNING,
            Map.of(1, slave1, 2, slave2Failed),
            Instant.now()
        );
        controller2.updateStatusFromState(degraded);
        assertEquals(ControllerStatus.DEGRADED, controller2.getStatus());

        // Test RUNNING -> DISCONNECTED (all failed)
        GenisysSlaveState slave1Failed = GenisysSlaveState.failed(1, 5, Instant.now());
        GenisysControllerState allFailed = GenisysControllerState.of(
            GenisysControllerState.GlobalState.RUNNING,
            Map.of(1, slave1Failed, 2, slave2Failed),
            Instant.now()
        );
        controller2.updateStatusFromState(allFailed);
        assertEquals(ControllerStatus.DISCONNECTED, controller2.getStatus());
    }
}
