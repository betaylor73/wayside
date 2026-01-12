package com.questrail.wayside.protocol.genisys.test;

import com.questrail.wayside.api.ControlId;
import com.questrail.wayside.api.IndicationId;
import com.questrail.wayside.core.ControlBitSetSignalSet;
import com.questrail.wayside.mapping.ArraySignalIndex;
import com.questrail.wayside.mapping.SignalIndex;
import com.questrail.wayside.protocol.genisys.config.GenisysStationConfig;
import com.questrail.wayside.protocol.genisys.runtime.GenisysProductionRuntime;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class GenisysProductionRuntimeSmokeTest {

    private record TestControl(int number, String labelStr) implements ControlId {
        @Override public int number() { return number; }
        @Override public Optional<String> label() { return Optional.ofNullable(labelStr); }
    }

    private record TestIndication(int number, String labelStr) implements IndicationId {
        @Override public int number() { return number; }
        @Override public Optional<String> label() { return Optional.ofNullable(labelStr); }
    }

    @Test
    void fullStackLifecycle() throws InterruptedException {
        SignalIndex<ControlId> controlIndex = new ArraySignalIndex<>(new TestControl(1, "CTL1"));
        SignalIndex<IndicationId> indicationIndex = new ArraySignalIndex<>(new TestIndication(1, "IND1"));

        GenisysStationConfig stations = GenisysStationConfig.builder()
            .addStation(1, new InetSocketAddress("127.0.0.1", 5005))
            .build();

        GenisysProductionRuntime runtime = GenisysProductionRuntime.builder()
            .withStations(stations)
            .withControlIndex(controlIndex)
            .withIndicationIndex(indicationIndex)
            .withControlSetProvider(station -> new ControlBitSetSignalSet(controlIndex))
            .withBindAddress(new InetSocketAddress("127.0.0.1", 0)) // ephemeral
            .build();

        assertNotNull(runtime);
        runtime.start();
        Thread.sleep(200);
        runtime.stop();
    }
}
