package com.questrail.wayside.protocol.genisys;

import com.questrail.wayside.api.ControlId;
import com.questrail.wayside.api.ControlSet;
import com.questrail.wayside.api.IndicationId;
import com.questrail.wayside.api.IndicationSet;
import com.questrail.wayside.api.ControllerStatus;
import com.questrail.wayside.core.AbstractWaysideController;
import com.questrail.wayside.mapping.SignalIndex;
import com.questrail.wayside.protocol.genisys.config.GenisysStationConfig;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysControlIntentEvent;
import com.questrail.wayside.protocol.genisys.internal.exec.GenisysTimingPolicy;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysControllerState;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysSlaveState;
import com.questrail.wayside.protocol.genisys.observability.GenisysObservabilitySink;
import com.questrail.wayside.protocol.genisys.runtime.GenisysProductionRuntime;

import java.time.Instant;
import java.util.Objects;

/**
 * GenisysProductionController
 * =============================================================================
 * Production implementation of the WaysideController API for the GENISYS protocol.
 * Extends AbstractWaysideController to leverage shared control/indication logic.
 */
public final class GenisysProductionController extends AbstractWaysideController {

    private final GenisysProductionRuntime runtime;
    private final GenisysStationConfig stationConfig;

    public GenisysProductionController(
            SignalIndex<ControlId> controlIndex,
            SignalIndex<IndicationId> indicationIndex,
            GenisysStationConfig stationConfig,
            GenisysTimingPolicy timingPolicy,
            GenisysObservabilitySink observabilitySink) {
        super(controlIndex, indicationIndex);

        this.stationConfig = Objects.requireNonNull(stationConfig, "stationConfig");

        this.runtime = GenisysProductionRuntime.builder()
            .withStations(stationConfig)
            .withTimingPolicy(timingPolicy)
            .withControlSetProvider(station -> this.getControls())
            .withObservabilitySink(observabilitySink)
            .withControlIndex(controlIndex)
            .withIndicationIndex(indicationIndex)
            .withIndicationCallback(this::onIndicationReceived)
            .build();
    }

    public void start() {
        runtime.start();
    }

    public void stop() {
        runtime.stop();
    }

    @Override
    protected void onControlsUpdated(ControlSet appliedDelta, ControlSet currentMaterialized) {
        // Submit control intent change event to the protocol engine.
        // The reducer uses this to decide if it needs to transition to a SEND phase.
        runtime.submitEvent(new GenisysControlIntentEvent.ControlIntentChanged(
            Instant.now(),
            appliedDelta,
            currentMaterialized
        ));
    }

    private void onIndicationReceived(int station, IndicationSet indications) {
        applyIndicationUpdate(indications);
    }

    /**
     * Map the internal GENISYS controller state to the public ControllerStatus.
     */
    public void updateStatusFromState(GenisysControllerState state) {
        setStatus(mapToControllerStatus(state));
    }

    private ControllerStatus mapToControllerStatus(GenisysControllerState state) {
        if (state.globalState() == GenisysControllerState.GlobalState.TRANSPORT_DOWN) {
            return ControllerStatus.DISCONNECTED;
        }
        if (state.globalState() == GenisysControllerState.GlobalState.INITIALIZING) {
            return ControllerStatus.DISCONNECTED;
        }

        // RUNNING: evaluate slave health
        long totalSlaves = state.slaves().size();
        long failedSlaves = state.slaves().values().stream()
            .filter(s -> s.phase() == GenisysSlaveState.Phase.FAILED)
            .count();

        if (failedSlaves == 0) {
            return ControllerStatus.CONNECTED;
        } else if (failedSlaves < totalSlaves) {
            return ControllerStatus.DEGRADED;
        } else {
            return ControllerStatus.DISCONNECTED; // All slaves failed
        }
    }
}
