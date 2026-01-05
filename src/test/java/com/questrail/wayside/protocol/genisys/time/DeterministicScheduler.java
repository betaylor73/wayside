package com.questrail.wayside.protocol.genisys.time;

import com.questrail.wayside.protocol.genisys.internal.time.Cancellable;
import com.questrail.wayside.protocol.genisys.internal.time.MonotonicClock;
import com.questrail.wayside.protocol.genisys.internal.time.MonotonicScheduler;

import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Deterministic scheduler driven by a ManualMonotonicClock.
 *
 * Tasks execute ONLY when {@link #runDueTasks()} is called.
 */
public final class DeterministicScheduler implements MonotonicScheduler {

    private final MonotonicClock clock;
    private final PriorityQueue<Scheduled> queue = new PriorityQueue<>();

    public DeterministicScheduler(MonotonicClock clock) {
        this.clock = clock;
    }

    @Override
    public Cancellable scheduleAtNanos(long deadlineNanos, Runnable task) {
        Scheduled scheduled = new Scheduled(deadlineNanos, task);
        queue.add(scheduled);
        return scheduled;
    }

    /**
     * Run all tasks whose deadlines are <= current clock time.
     */
    public void runDueTasks() {
        while (!queue.isEmpty() && queue.peek().deadlineNanos <= clock.nowNanos()) {
            Scheduled next = queue.poll();
            if (!next.cancelled.get()) {
                next.task.run();
            }
        }
    }

    private static final class Scheduled implements Comparable<Scheduled>, Cancellable {
        private final long deadlineNanos;
        private final Runnable task;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        private Scheduled(long deadlineNanos, Runnable task) {
            this.deadlineNanos = deadlineNanos;
            this.task = task;
        }

        @Override
        public boolean cancel() {
            return cancelled.compareAndSet(false, true);
        }

        @Override
        public int compareTo(Scheduled o) {
            return Long.compare(this.deadlineNanos, o.deadlineNanos);
        }
    }
}