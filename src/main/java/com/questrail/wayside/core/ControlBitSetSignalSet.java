package com.questrail.wayside.core;

import com.questrail.wayside.api.ControlId;
import com.questrail.wayside.api.ControlSet;
import com.questrail.wayside.mapping.SignalIndex;

/**
 * ControlBitSetSignalSet
 * ---------------------------------------------------------------------
 * Type-safe specialization of BitSetSignalSet for controls.
 *
 * This class exists purely to satisfy the Java type system while
 * preserving semantic clarity.
 */
public final class ControlBitSetSignalSet extends BitSetSignalSet<ControlId> implements ControlSet
{
    public ControlBitSetSignalSet(SignalIndex<ControlId> index) {
        super(index);
    }
}
