package com.questrail.wayside.protocol.genisys.observability;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Test sink that records events for assertions.
 */
public final class RecordingObservabilitySink implements GenisysObservabilitySink {
    private final List<Object> events = new ArrayList<>();

    @Override
    public synchronized void onStateTransition(GenisysStateTransitionEvent event) {
        events.add(event);
    }

    @Override
    public synchronized void onProtocolEvent(GenisysProtocolObservabilityEvent event) {
        events.add(event);
    }

    @Override
    public synchronized void onTransportEvent(GenisysTransportObservabilityEvent event) {
        events.add(event);
    }

    @Override
    public synchronized void onError(GenisysErrorEvent event) {
        events.add(event);
    }

    public synchronized List<Object> getAllEvents() {
        return new ArrayList<>(events);
    }

    public synchronized List<GenisysStateTransitionEvent> getStateTransitions() {
        return events.stream()
            .filter(e -> e instanceof GenisysStateTransitionEvent)
            .map(e -> (GenisysStateTransitionEvent) e)
            .collect(Collectors.toList());
    }

    public synchronized <T> boolean hasEventOfType(Class<T> type) {
        return events.stream().anyMatch(type::isInstance);
    }
}
