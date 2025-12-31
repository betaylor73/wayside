package com.questrail.wayside.protocol.genisys.test.exec;

import com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * DeterministicShuffleEventSource
 * -------------------------------
 *
 * Takes a fixed list of events and returns them in a deterministic shuffled order.
 *
 * This provides Phase 2's "adversarial ordering" without introducing nondeterminism.
 */
final class DeterministicShuffleEventSource implements SyntheticEventSource
{
    private final List<GenisysEvent> shuffled;

    DeterministicShuffleEventSource(List<? extends GenisysEvent> events, long seed) {
        Objects.requireNonNull(events);
        List<GenisysEvent> copy = new ArrayList<>(events);
        Collections.shuffle(copy, new Random(seed));
        this.shuffled = Collections.unmodifiableList(copy);
    }

    @Override
    public List<GenisysEvent> events() {
        return shuffled;
    }
}
