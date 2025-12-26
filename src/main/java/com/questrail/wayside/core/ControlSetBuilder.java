package com.questrail.wayside.core;

import com.questrail.wayside.api.ControlId;
import com.questrail.wayside.api.ControlSet;
import com.questrail.wayside.api.SignalState;
import com.questrail.wayside.mapping.SignalIndex;

import java.util.Objects;

/**
 * ControlSetBuilder
 * -----------------------------------------------------------------------------
 * Builder for constructing {@link ControlSet} instances incrementally and
 * ergonomically.
 *
 * <h2>Purpose</h2>
 * This class exists to:
 * <ul>
 *   <li>Separate construction concerns from {@link BitSetSignalSet}</li>
 *   <li>Provide readable, intention-revealing APIs</li>
 *   <li>Support construction from protocol decoders, configuration, or tests</li>
 * </ul>
 *
 * <h2>Design Notes</h2>
 * <ul>
 *   <li>The builder is mutable; the built {@code ControlSet} may be reused or copied</li>
 *   <li>{@link SignalState#DONT_CARE} is the default for all signals</li>
 *   <li>No validation of control/indication correspondence is performed</li>
 * </ul>
 */
public final class ControlSetBuilder
{
    private final ControlBitSetSignalSet working;

    public ControlSetBuilder(SignalIndex<ControlId> index) {
        this.working = new ControlBitSetSignalSet(index);
    }

    /**
     * Sets a control to TRUE.
     */
    public ControlSetBuilder set(ControlId id) {
        working.set(id, SignalState.TRUE);
        return this;
    }

    /**
     * Sets a control to FALSE.
     */
    public ControlSetBuilder clear(ControlId id) {
        working.set(id, SignalState.FALSE);
        return this;
    }

    /**
     * Explicitly marks a control as DONT_CARE.
     */
    public ControlSetBuilder dontCare(ControlId id) {
        working.set(id, SignalState.DONT_CARE);
        return this;
    }

    /**
     * Sets a control to the specified state.
     */
    public ControlSetBuilder set(ControlId id, SignalState state) {
        working.set(id, state);
        return this;
    }

    /**
     * Builds the {@link ControlSet}.
     * <p>
     * The returned instance is backed by the builder's internal state;
     * callers should not mutate it unless such behavior is explicitly desired.
     */
    public ControlSet build() {
        return working;
    }
}
