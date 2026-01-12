package com.questrail.wayside.protocol.genisys.observability;

import com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysControllerState;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysIntents;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Record representing a state transition in the GENISYS controller.
 */
public record GenisysStateTransitionEvent(
    Instant timestamp,
    GenisysControllerState oldState,
    GenisysControllerState newState,
    GenisysEvent triggeringEvent,
    GenisysIntents resultingIntents
) {
    /**
     * Checks if the global state changed during this transition.
     */
    public boolean isGlobalStateChange() {
        return oldState.globalState() != newState.globalState();
    }

    /**
     * Returns the set of station addresses whose state changed during this transition.
     */
    public Set<Integer> affectedStations() {
        Set<Integer> affected = new HashSet<>();
        Set<Integer> allStations = new HashSet<>(oldState.slaves().keySet());
        allStations.addAll(newState.slaves().keySet());

        for (Integer station : allStations) {
            if (!oldState.slaves().containsKey(station) ||
                !newState.slaves().containsKey(station) ||
                !oldState.slaves().get(station).equals(newState.slaves().get(station))) {
                affected.add(station);
            }
        }
        return affected;
    }
}
