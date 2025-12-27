package com.questrail.wayside.protocol.genisys.internal.state;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * GenisysControllerState
 * -----------------------------------------------------------------------------
 * Immutable snapshot of the GENISYS master controller's *logical state*.
 *
 * <h2>Role in the architecture</h2>
 * This class represents the state consumed and produced by the GENISYS
 * state machine. It is deliberately:
 * <ul>
 *   <li>Pure data (no behavior)</li>
 *   <li>Immutable</li>
 *   <li>Explicit about protocol-relevant facts</li>
 * </ul>
 *
 * State transitions are performed by event handlers which take a
 * {@code GenisysControllerState} + {@code GenisysEvent} and produce a new
 * {@code GenisysControllerState}.
 *
 * <h2>Global vs per-slave state</h2>
 * GENISYS behavior naturally decomposes into:
 * <ul>
 *   <li>Global master state (initializing vs running)</li>
 *   <li>Per-slave protocol state (recall, poll, failed, etc.)</li>
 * </ul>
 *
 * This class contains both, but does not impose any execution model.
 */
public final class GenisysControllerState
{
    /**
     * Global lifecycle state of the GENISYS master.
     */
    public enum GlobalState {
        INITIALIZING,
        RUNNING
    }

    private final GlobalState globalState;
    private final Map<Integer, GenisysSlaveState> slaves;
    private final Instant lastTransition;

    private GenisysControllerState(GlobalState globalState,
                                   Map<Integer, GenisysSlaveState> slaves,
                                   Instant lastTransition) {
        this.globalState = Objects.requireNonNull(globalState, "globalState");
        this.slaves = Map.copyOf(slaves);
        this.lastTransition = Objects.requireNonNull(lastTransition, "lastTransition");
    }

    public GlobalState globalState() {
        return globalState;
    }

    /**
     * Returns an immutable view of per-slave state keyed by station address.
     */
    public Map<Integer, GenisysSlaveState> slaves() {
        return slaves;
    }

    public Instant lastTransition() {
        return lastTransition;
    }

    // ---------------------------------------------------------------------
    // Factory helpers
    // ---------------------------------------------------------------------

    /**
     * Creates an initial controller state for a given set of slave addresses.
     */
    public static GenisysControllerState initializing(Iterable<Integer> slaveAddresses,
                                                      Instant now) {
        Map<Integer, GenisysSlaveState> map = new HashMap<>();
        for (Integer addr : slaveAddresses) {
            map.put(addr, GenisysSlaveState.initial(addr));
        }
        return new GenisysControllerState(GlobalState.INITIALIZING, map, now);
    }

    /**
     * Returns a new state with updated global state.
     */
    public GenisysControllerState withGlobalState(GlobalState newState, Instant now) {
        return new GenisysControllerState(newState, this.slaves, now);
    }

    /**
     * Returns a new state with one slave state replaced.
     */
    public GenisysControllerState withSlaveState(GenisysSlaveState slaveState, Instant now) {
        Map<Integer, GenisysSlaveState> updated = new HashMap<>(this.slaves);
        updated.put(slaveState.stationAddress(), slaveState);
        return new GenisysControllerState(this.globalState, updated, now);
    }

    public static GenisysControllerState of(
            GlobalState globalState,
            Map<Integer, GenisysSlaveState> slaves,
            Instant now) {
        return new GenisysControllerState(globalState, slaves, now);
    }
}
