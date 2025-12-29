package com.questrail.wayside.protocol.genisys.internal.state;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * GenisysIntents
 * -----------------------------------------------------------------------------
 * Immutable collection of *protocol execution intentions* emitted by the
 * {@link GenisysStateReducer}.
 *
 * <h2>Role in the architecture</h2>
 * {@code GenisysIntents} is the bridge between:
 * <ul>
 *   <li>pure, deterministic state transition logic</li>
 *   <li>impure, side-effecting protocol execution</li>
 * </ul>
 *
 * The state reducer determines <b>what should happen next</b>; the surrounding
 * controller determines <b>how and when</b> those actions are carried out.
 *
 * <h2>Design principles</h2>
 * <ul>
 *   <li>Intents are <b>immutable</b></li>
 *   <li>Intents are <b>composable</b></li>
 *   <li>Intents express <b>desired actions</b>, not execution details</li>
 *   <li>No intent performs I/O directly</li>
 * </ul>
 *
 * This design keeps protocol behavior testable and easy to reason about while
 * allowing flexible execution strategies.
 */
public final class GenisysIntents
{
    /**
     * Enumerates the kinds of actions the GENISYS controller may need to perform.
     */
    public enum Kind {
        /** Begin (or restart) initialization / recall processing. */
        BEGIN_INITIALIZATION,

        /** Suspend all protocol activity (e.g. transport down). */
        SUSPEND_ALL,

        /** Retry the current protocol step. */
        RETRY_CURRENT,

        /** Schedule delivery of pending control updates. */
        SCHEDULE_CONTROL_DELIVERY,

        /** Send a recall message to a specific slave. */
        SEND_RECALL,

        /** Send control data to a specific slave. */
        SEND_CONTROLS,

        /** Poll (or acknowledge+poll) the next slave. */
        POLL_NEXT
    }

    private final Set<Kind> kinds;
    private final Integer targetStation;

    private GenisysIntents(Set<Kind> kinds, Integer targetStation) {
        this.kinds = Collections.unmodifiableSet(EnumSet.copyOf(kinds));
        this.targetStation = targetStation;
    }

    /**
     * Returns the set of intent kinds represented.
     */
    public Set<Kind> kinds() {
        return kinds;
    }

    /**
     * Returns the station address targeted by this intent, if applicable.
     */
    public Integer targetStation() {
        return targetStation;
    }

    // ---------------------------------------------------------------------
    // Factory methods
    // ---------------------------------------------------------------------

    /**
     * No-op intent (do nothing).
     */
    public static GenisysIntents none() {
        return new GenisysIntents(EnumSet.noneOf(Kind.class), null);
    }

    /**
     * Begin or restart protocol initialization.
     */
    public static GenisysIntents beginInitialization() {
        return new GenisysIntents(EnumSet.of(Kind.BEGIN_INITIALIZATION), null);
    }

    /**
     * Suspend all protocol activity.
     */
    public static GenisysIntents suspendAll() {
        return new GenisysIntents(EnumSet.of(Kind.SUSPEND_ALL), null);
    }

    /**
     * Retry the current protocol step.
     */
    public static GenisysIntents retryCurrent() {
        return new GenisysIntents(EnumSet.of(Kind.RETRY_CURRENT), null);
    }

    /**
     * Schedule delivery of pending control updates.
     */
    public static GenisysIntents scheduleControlDelivery() {
        return new GenisysIntents(EnumSet.of(Kind.SCHEDULE_CONTROL_DELIVERY), null);
    }

    /**
     * Send a recall message to the specified slave.
     */
    public static GenisysIntents sendRecall(int stationAddress) {
        return new GenisysIntents(EnumSet.of(Kind.SEND_RECALL), stationAddress);
    }

    /**
     * Send control data to the specified slave.
     */
    public static GenisysIntents sendControls(int stationAddress) {
        return new GenisysIntents(EnumSet.of(Kind.SEND_CONTROLS), stationAddress);
    }

    /**
     * Poll the next slave after the specified one.
     */
    public static GenisysIntents pollNext(int stationAddress) {
        return new GenisysIntents(EnumSet.of(Kind.POLL_NEXT), stationAddress);
    }

    // ---------------------------------------------------------------------
    // Composition
    // ---------------------------------------------------------------------

    /**
     * Combines this set of intents with another, producing a new immutable
     * {@code GenisysIntents} instance.
     *
     * <p>Station-specific intents must agree on target station.</p>
     */
    public GenisysIntents and(GenisysIntents other) {
        Objects.requireNonNull(other, "other");

        Integer target = this.targetStation;
        if (target != null && other.targetStation != null
                && !target.equals(other.targetStation)) {
            throw new IllegalArgumentException(
                    "Cannot combine intents targeting different stations");
        }

        if (target == null) {
            target = other.targetStation;
        }

        EnumSet<Kind> merged = EnumSet.copyOf(this.kinds);
        merged.addAll(other.kinds);

        return new GenisysIntents(merged, target);
    }
}
