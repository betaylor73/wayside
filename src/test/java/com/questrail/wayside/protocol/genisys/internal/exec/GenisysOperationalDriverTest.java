package com.questrail.wayside.protocol.genisys.internal.exec;

import com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysTransportEvent;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysControllerState;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysIntents;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysStateReducer;
import com.questrail.wayside.protocol.genisys.time.DeterministicScheduler;
import com.questrail.wayside.protocol.genisys.time.ManualMonotonicClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GenisysOperationalDriverTest
 * -----------------------------------------------------------------------------
 * Tests the Phase 5 operational driver's event loop and coordination behavior.
 */
class GenisysOperationalDriverTest {

    private ManualMonotonicClock clock;
    private DeterministicScheduler scheduler;
    private GenisysStateReducer reducer;
    private List<GenisysIntents> executedIntents;
    private GenisysIntentExecutor mockExecutor;
    private GenisysOperationalDriver driver;

    @BeforeEach
    void setUp() {
        clock = new ManualMonotonicClock();
        scheduler = new DeterministicScheduler(clock);
        reducer = new GenisysStateReducer();
        executedIntents = new ArrayList<>();

        mockExecutor = intents -> {
            synchronized (executedIntents) {
                executedIntents.add(intents);
            }
        };

        driver = new GenisysOperationalDriver(
                reducer,
                mockExecutor,
                clock,
                scheduler,
                GenisysTimingPolicy.withResponseTimeout(java.time.Duration.ofMillis(500)),
                () -> GenisysControllerState.of(
                    GenisysControllerState.GlobalState.TRANSPORT_DOWN,
                    Collections.emptyMap(),
                    Instant.now()
            )
        );
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.stop();
        }
    }

    @Test
    void startsAndStopsCleanly() {
        driver.start();
        assertTrue(driver.currentState() != null);
        driver.stop();
        // Should not throw or hang
    }

    @Test
    void processesTransportUpEvent() throws InterruptedException {
        driver.start();

        CountDownLatch latch = new CountDownLatch(1);
        GenisysIntentExecutor countingExecutor = intents -> {
            synchronized (executedIntents) {
                executedIntents.add(intents);
                latch.countDown();
            }
        };

        // Recreate driver with counting executor
        driver.stop();
        driver = new GenisysOperationalDriver(
                reducer,
                countingExecutor,
                clock,
                scheduler,
                GenisysTimingPolicy.withResponseTimeout(java.time.Duration.ofMillis(500)),
                () -> GenisysControllerState.of(
                    GenisysControllerState.GlobalState.TRANSPORT_DOWN,
                    Collections.emptyMap(),
                    Instant.now()
            )
        );
        driver.start();

        // Submit TransportUp event
        driver.submitEvent(new GenisysTransportEvent.TransportUp(Instant.now()));

        // Wait for processing
        boolean processed = latch.await(1, TimeUnit.SECONDS);
        assertTrue(processed, "Event should be processed");

        // Verify intents were executed
        synchronized (executedIntents) {
            assertFalse(executedIntents.isEmpty(), "Intents should have been executed");
        }
    }

    @Test
    void maintainsStateAcrossEvents() throws InterruptedException {
        driver.start();

        // Submit TransportUp to move out of initial TRANSPORT_DOWN state
        CountDownLatch latch = new CountDownLatch(1);
        GenisysIntentExecutor latchExecutor = intents -> latch.countDown();

        driver.stop();
        driver = new GenisysOperationalDriver(
                reducer,
                latchExecutor,
                clock,
                scheduler,
                GenisysTimingPolicy.withResponseTimeout(java.time.Duration.ofMillis(500)),
                () -> GenisysControllerState.of(
                    GenisysControllerState.GlobalState.TRANSPORT_DOWN,
                    Collections.emptyMap(),
                    Instant.now()
            )
        );
        driver.start();

        GenisysControllerState initialState = driver.currentState();
        assertEquals(GenisysControllerState.GlobalState.TRANSPORT_DOWN, initialState.globalState());

        driver.submitEvent(new GenisysTransportEvent.TransportUp(Instant.now()));
        latch.await(1, TimeUnit.SECONDS);

        GenisysControllerState afterUp = driver.currentState();
        assertEquals(GenisysControllerState.GlobalState.INITIALIZING, afterUp.globalState());
    }

    @Test
    void processesEventsSequentially() throws InterruptedException {
        List<GenisysIntents> sequentialIntents = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(3); // All transport events emit intents

        // Executor that tracks intents sequentially
        GenisysIntentExecutor trackingExecutor = intents -> {
            synchronized (sequentialIntents) {
                sequentialIntents.add(intents);
            }
            latch.countDown();
        };

        driver.stop();
        driver = new GenisysOperationalDriver(
                reducer,
                trackingExecutor,
                clock,
                scheduler,
                GenisysTimingPolicy.withResponseTimeout(java.time.Duration.ofMillis(500)),
                () -> GenisysControllerState.of(
                    GenisysControllerState.GlobalState.TRANSPORT_DOWN,
                    Collections.emptyMap(),
                    Instant.now()
            )
        );
        driver.start();

        // Submit multiple events
        driver.submitEvent(new GenisysTransportEvent.TransportUp(Instant.now()));
        driver.submitEvent(new GenisysTransportEvent.TransportDown(Instant.now()));
        driver.submitEvent(new GenisysTransportEvent.TransportUp(Instant.now()));

        // Wait for all events to be processed
        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue(completed, "All events should be processed");

        // Should have executed intents for all three events
        synchronized (sequentialIntents) {
            assertEquals(3, sequentialIntents.size());
        }
    }

    @Test
    void ignoresEventsAfterStop() throws InterruptedException {
        driver.start();
        driver.stop();

        // Submit event after stop
        driver.submitEvent(new GenisysTransportEvent.TransportUp(Instant.now()));

        // Give it time to process (it shouldn't)
        Thread.sleep(100);

        // Should not have executed any intents
        synchronized (executedIntents) {
            assertTrue(executedIntents.isEmpty());
        }
    }

    @Test
    void doubleStartIsIdempotent() {
        driver.start();
        GenisysControllerState firstState = driver.currentState();

        driver.start(); // Second start should be no-op
        GenisysControllerState secondState = driver.currentState();

        // State should not be reset
        assertSame(firstState, secondState, "State should not be reset on double start");
    }

    @Test
    void doubleStopIsIdempotent() {
        driver.start();
        driver.stop();
        driver.stop(); // Should not throw or block
    }
}
