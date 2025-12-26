package com.questrail.wayside.core;

import com.questrail.wayside.api.IndicationId;
import com.questrail.wayside.api.IndicationSet;
import com.questrail.wayside.mapping.SignalIndex;

/**
 * IndicationBitSetSignalSet
 * ---------------------------------------------------------------------
 * Type-safe specialization of BitSetSignalSet for indications.
 */
public final class IndicationBitSetSignalSet extends BitSetSignalSet<IndicationId> implements IndicationSet
{
    public IndicationBitSetSignalSet(SignalIndex<IndicationId> index) {
        super(index);
    }
}
