package com.questrail.wayside.core;

import com.questrail.wayside.api.IndicationId;
import com.questrail.wayside.api.IndicationSet;
import com.questrail.wayside.api.SignalState;
import com.questrail.wayside.mapping.SignalIndex;

import java.util.Objects;

/**
 * IndicationSetBuilder
 * -----------------------------------------------------------------------------
 * Builder for constructing {@link IndicationSet} instances incrementally.
 *
 * <h2>Purpose</h2>
 * This builder is typically used by protocol decoders and test harnesses
 * to materialize or partially update indication state based on received data.
 *
 * <h2>Key Semantics</h2>
 * <ul>
 *   <li>{@link SignalState#DONT_CARE} means "not reported"</li>
 *   <li>No assumption of completeness is made</li>
 *   <li>No causal interpretation is performed</li>
 * </ul>
 */
public final class IndicationSetBuilder
{
    private final IndicationBitSetSignalSet working;

    public IndicationSetBuilder(SignalIndex<IndicationId> index) {
        this.working = new IndicationBitSetSignalSet(index);
    }

    /**
     * Records an indication as TRUE.
     */
    public IndicationSetBuilder set(IndicationId id) {
        working.set(id, SignalState.TRUE);
        return this;
    }

    /**
     * Records an indication as FALSE.
     */
    public IndicationSetBuilder clear(IndicationId id) {
        working.set(id, SignalState.FALSE);
        return this;
    }

    /**
     * Marks an indication as not reported.
     */
    public IndicationSetBuilder dontCare(IndicationId id) {
        working.set(id, SignalState.DONT_CARE);
        return this;
    }

    /**
     * Sets an indication to the specified state.
     */
    public IndicationSetBuilder set(IndicationId id, SignalState state) {
        working.set(id, state);
        return this;
    }

    /**
     * Builds the {@link IndicationSet}.
     */
    public IndicationSet build() {
        return working;
    }
}
