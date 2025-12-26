package com.questrail.wayside.api;

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
 * no new behavior beyond {@link SignalSet}.
 */
public interface IndicationSet extends SignalSet<IndicationId>
{
    // Marker specialization only
}
