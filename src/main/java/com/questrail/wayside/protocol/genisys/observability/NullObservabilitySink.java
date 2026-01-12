package com.questrail.wayside.protocol.genisys.observability;

/**
 * No-op implementation of GenisysObservabilitySink.
 */
public final class NullObservabilitySink implements GenisysObservabilitySink {
    public static final NullObservabilitySink INSTANCE = new NullObservabilitySink();

    private NullObservabilitySink() {}

    @Override
    public void onStateTransition(GenisysStateTransitionEvent event) {}

    @Override
    public void onProtocolEvent(GenisysProtocolObservabilityEvent event) {}

    @Override
    public void onTransportEvent(GenisysTransportObservabilityEvent event) {}

    @Override
    public void onError(GenisysErrorEvent event) {}
}
