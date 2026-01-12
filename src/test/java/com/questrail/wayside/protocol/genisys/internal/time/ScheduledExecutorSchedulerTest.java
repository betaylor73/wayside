package com.questrail.wayside.protocol.genisys.internal.time;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ScheduledExecutorSchedulerTest
 * -----------------------------------------------------------------------------
 * Tests for the production scheduler implementation.
 *
 * Note: These tests use real time and may be slightly flaky on heavily loaded
 * systems. Tolerances are set generously to avoid false failures.
 */
class ScheduledExecutorSchedulerTest {

    private ScheduledExecutorService executor;
    private ScheduledExecutorScheduler scheduler;

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadScheduledExecutor();
        scheduler = new ScheduledExecutorScheduler(executor, SystemMonotonicClock.INSTANCE);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void taskExecutesAfterDeadline() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean executed = new AtomicBoolean(false);

        long deadline = SystemMonotonicClock.INSTANCE.nowNanos() + TimeUnit.MILLISECONDS.toNanos(50);

        scheduler.scheduleAtNanos(deadline, () -> {
            executed.set(true);
            latch.countDown();
        });

        // Wait for execution (with generous timeout)
        assertTrue(latch.await(500, TimeUnit.MILLISECONDS), "Task should execute");
        assertTrue(executed.get());
    }

    @Test
    void pastDeadlineExecutesImmediately() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        // Deadline in the past
        long deadline = SystemMonotonicClock.INSTANCE.nowNanos() - TimeUnit.SECONDS.toNanos(1);

        long before = System.nanoTime();
        scheduler.scheduleAtNanos(deadline, latch::countDown);

        assertTrue(latch.await(100, TimeUnit.MILLISECONDS), "Task should execute immediately");
        long elapsed = System.nanoTime() - before;

        // Should execute very quickly (within 100ms)
        assertTrue(elapsed < TimeUnit.MILLISECONDS.toNanos(100));
    }

    @Test
    void cancelPreventsExecution() throws InterruptedException {
        AtomicBoolean executed = new AtomicBoolean(false);

        long deadline = SystemMonotonicClock.INSTANCE.nowNanos() + TimeUnit.MILLISECONDS.toNanos(100);

        Cancellable handle = scheduler.scheduleAtNanos(deadline, () -> executed.set(true));

        // Cancel before deadline
        assertTrue(handle.cancel(), "Cancel should succeed");

        // Wait past the deadline
        Thread.sleep(150);

        assertFalse(executed.get(), "Cancelled task should not execute");
    }

    @Test
    void cancelAfterExecutionReturnsFalse() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        // Schedule for immediate execution
        long deadline = SystemMonotonicClock.INSTANCE.nowNanos();

        Cancellable handle = scheduler.scheduleAtNanos(deadline, latch::countDown);

        // Wait for execution
        assertTrue(latch.await(100, TimeUnit.MILLISECONDS));

        // Cancel after execution should return false
        assertFalse(handle.cancel(), "Cancel after execution should return false");
    }

    @Test
    void multipleTasksExecuteInOrder() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(3);

        int[] executionOrder = new int[3];

        long now = SystemMonotonicClock.INSTANCE.nowNanos();

        // Schedule in reverse order to verify deadline-based ordering
        scheduler.scheduleAtNanos(now + TimeUnit.MILLISECONDS.toNanos(30), () -> {
            executionOrder[2] = counter.incrementAndGet();
            latch.countDown();
        });
        scheduler.scheduleAtNanos(now + TimeUnit.MILLISECONDS.toNanos(20), () -> {
            executionOrder[1] = counter.incrementAndGet();
            latch.countDown();
        });
        scheduler.scheduleAtNanos(now + TimeUnit.MILLISECONDS.toNanos(10), () -> {
            executionOrder[0] = counter.incrementAndGet();
            latch.countDown();
        });

        assertTrue(latch.await(500, TimeUnit.MILLISECONDS));

        // Should execute in deadline order, not submission order
        assertEquals(1, executionOrder[0]);
        assertEquals(2, executionOrder[1]);
        assertEquals(3, executionOrder[2]);
    }
}
