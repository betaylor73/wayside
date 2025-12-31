package com.questrail.wayside.protocol.genisys.test.exec;

import com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * ScriptedEventSource
 * -------------------
 *
 * Simple Phase 2 source: returns exactly the events provided.
 *
 * Useful for building adversarial sequences (duplicate events,
 * message-after-timeout, transport flapping) in a declarative way.
 */
final class ScriptedEventSource implements SyntheticEventSource
{
    private final List<GenisysEvent> events;

    ScriptedEventSource(List<? extends GenisysEvent> events) {
        Objects.requireNonNull(events);
        this.events = Collections.unmodifiableList(new ArrayList<>(events));
    }

    @Override
    public List<GenisysEvent> events() {
        return events;
    }
}
