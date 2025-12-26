package com.questrail.wayside.api;

/**
 * ControlSet
 * -----------------------------------------------------------------------------
 * A {@code ControlSet} is a {@link SignalSet} whose semantic meaning is INTENT.
 *
 * Controls express desired state toward a remote wayside logic controller.
 * They do not imply execution, success, or any corresponding indication.
 *
 * This interface exists solely for type safety and clarity. It introduces
 * no new behavior beyond {@link SignalSet}.
 */
public interface ControlSet extends SignalSet<ControlId>
{
    // Marker specialization only
}