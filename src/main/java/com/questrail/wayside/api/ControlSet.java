package com.questrail.wayside.api;

import com.questrail.wayside.core.ControlBitSetSignalSet;
import com.questrail.wayside.mapping.SignalIndex;

/**
 * ControlSet
 * -----------------------------------------------------------------------------
 * A {@code ControlSet} is a {@link SignalSet} whose semantic meaning is INTENT.
 *
 * Controls express desired state toward a remote wayside logic controller.
 * They do not imply execution, success, or any corresponding indication.
 *
 * This interface exists solely for type safety and clarity. It introduces
 * no new behavior beyond {@link SignalSet} other than the ability to create
 * an empty {@code ControlSet}.
 */
public interface ControlSet extends SignalSet<ControlId>
{
    static ControlSet empty(SignalIndex<ControlId> index) {
        return new ControlBitSetSignalSet(index);
    }
}