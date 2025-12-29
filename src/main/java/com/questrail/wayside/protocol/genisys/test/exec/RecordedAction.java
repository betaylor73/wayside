// Test-only semantic action record used by RecordingIntentExecutor

package com.questrail.wayside.protocol.genisys.test.exec;

import java.util.Objects;

/**
 * RecordedAction
 * --------------
 *
 * Represents a semantic action that a concrete GenisysIntentExecutor
 * would have performed.
 *
 * This is a test-only value type.
 */
public final class RecordedAction {

    public enum Kind {
        SENT_RECALL,
        SENT_CONTROLS,
        SENT_POLL,
        RETRIED_CURRENT,
        TIMER_ARMED,
        TIMER_REARMED,
        ALL_TIMERS_CANCELLED,
        PROTOCOL_SUSPENDED,
        INITIALIZATION_STARTED
    }

    private final Kind kind;
    private final Integer station;

    private RecordedAction(Kind kind, Integer station) {
        this.kind = kind;
        this.station = station;
    }

    public static RecordedAction sentRecall(int station) {
        return new RecordedAction(Kind.SENT_RECALL, station);
    }

    public static RecordedAction sentControls(int station) {
        return new RecordedAction(Kind.SENT_CONTROLS, station);
    }

    public static RecordedAction sentPoll() {
        return new RecordedAction(Kind.SENT_POLL, null);
    }

    public static RecordedAction retriedCurrent(int station) {
        return new RecordedAction(Kind.RETRIED_CURRENT, station);
    }

    public static RecordedAction timerArmed(Integer station) {
        return new RecordedAction(Kind.TIMER_ARMED, station);
    }

    public static RecordedAction timerRearmed(Integer station) {
        return new RecordedAction(Kind.TIMER_REARMED, station);
    }

    public static RecordedAction allTimersCancelled() {
        return new RecordedAction(Kind.ALL_TIMERS_CANCELLED, null);
    }

    public static RecordedAction protocolSuspended() {
        return new RecordedAction(Kind.PROTOCOL_SUSPENDED, null);
    }

    public static RecordedAction initializationStarted() {
        return new RecordedAction(Kind.INITIALIZATION_STARTED, null);
    }

    public Kind kind() {
        return kind;
    }

    public Integer station() {
        return station;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecordedAction that)) return false;
        return kind == that.kind && Objects.equals(station, that.station);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, station);
    }

    @Override
    public String toString() {
        return kind + (station != null ? "(" + station + ")" : "");
    }
}
