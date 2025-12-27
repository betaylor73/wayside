package com.questrail.wayside.api;

import com.questrail.wayside.core.IndicationBitSetSignalSet;
import com.questrail.wayside.mapping.SignalIndex;

/**
 * IndicationSet
 * -----------------------------------------------------------------------------
 * An {@code IndicationSet} is a {@link SignalSet} whose semantic meaning is
 * OBSERVATION.
 *
 * Indications represent reported or observed state from a remote wayside
 * logic controller. They may be partial, stale, or delayed.
 *
 * This interface exists solely for type safety and clarity. It introduces
 * no new behavior beyond {@link SignalSet} other than the ability to create
 * an empty {@code IndicationSet}.
 */
public interface IndicationSet extends SignalSet<IndicationId>
{
    static IndicationSet empty(SignalIndex<IndicationId> index) {
        return new IndicationBitSetSignalSet(index);
    }
}
