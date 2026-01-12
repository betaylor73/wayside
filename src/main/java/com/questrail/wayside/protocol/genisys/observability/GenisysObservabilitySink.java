package com.questrail.wayside.protocol.genisys.observability;

/**
 * Main interface for receiving GENISYS protocol observability events.
 * Implementations can provide logging, metrics, or tracing.
 */
public interface GenisysObservabilitySink {
    /**
     * Called when a controller state transition occurs.
     * @param event the transition event details
     */
    void onStateTransition(GenisysStateTransitionEvent event);

    /**
     * Called when a protocol-level observability event occurs (e.g., timeout, retry).
     * @param event the protocol event
     */
    void onProtocolEvent(GenisysProtocolObservabilityEvent event);

    /**
     * Called when a transport-level event occurs (e.g., connection up/down).
     * @param event the transport event
     */
    void onTransportEvent(GenisysTransportObservabilityEvent event);

    /**
     * Called when an error or anomaly occurs in the protocol stack.
     * @param event the error event
     */
    void onError(GenisysErrorEvent event);
}
