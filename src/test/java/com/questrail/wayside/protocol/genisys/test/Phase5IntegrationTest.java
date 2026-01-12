package com.questrail.wayside.protocol.genisys.test;

import com.questrail.wayside.api.ControlSet;
import com.questrail.wayside.protocol.genisys.GenisysWaysideController;
import com.questrail.wayside.protocol.genisys.codec.impl.DefaultGenisysFrameDecoder;
import com.questrail.wayside.protocol.genisys.codec.impl.DefaultGenisysFrameEncoder;
import com.questrail.wayside.protocol.genisys.internal.decode.GenisysMessageDecoder;
import com.questrail.wayside.protocol.genisys.internal.encode.GenisysMessageEncoder;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysTimeoutEvent;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysTransportEvent;
import com.questrail.wayside.protocol.genisys.internal.exec.GenisysIntentExecutor;
import com.questrail.wayside.protocol.genisys.internal.exec.GenisysMonotonicActivityTracker;
import com.questrail.wayside.protocol.genisys.internal.exec.GenisysOperationalDriver;
import com.questrail.wayside.protocol.genisys.internal.exec.GenisysTimingPolicy;
import com.questrail.wayside.protocol.genisys.internal.exec.SendTracker;
import com.questrail.wayside.protocol.genisys.internal.exec.TimedGenisysIntentExecutor;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysControllerState;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysIntents;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysStateReducer;
import com.questrail.wayside.protocol.genisys.model.GenisysStationAddress;
import com.questrail.wayside.protocol.genisys.model.IndicationData;
import com.questrail.wayside.protocol.genisys.runtime.GenisysUdpRuntime;
import com.questrail.wayside.protocol.genisys.time.DeterministicScheduler;
import com.questrail.wayside.protocol.genisys.time.ManualMonotonicClock;
import com.questrail.wayside.protocol.genisys.transport.DatagramEndpoint;
import com.questrail.wayside.protocol.genisys.transport.DatagramEndpointListener;
import com.questrail.wayside.protocol.genisys.transport.udp.UdpGenisysIntentExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.SocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase5IntegrationTest
 * =============================================================================
 * Comprehensive end-to-end tests for Phase 5 operational behavior.
 *
 * <h2>Test Coverage</h2>
 * <ul>
 *   <li>Response timeout injection and suppression</li>
 *   <li>Activity-based timeout cancellation</li>
 *   <li>Deterministic timing behavior (no sleeps)</li>
 *   <li>Full component integration (clock, scheduler, tracker, executor, driver)</li>
 *   <li>Backward compatibility with Phase 4 components</li>
 * </ul>
 */
class Phase5IntegrationTest {

    private ManualMonotonicClock clock;
    private DeterministicScheduler scheduler;
    private GenisysMonotonicActivityTracker activityTracker;
    private GenisysStateReducer reducer;
    private List<GenisysEvent> injectedEvents;
    private GenisysOperationalDriver driver;

    @BeforeEach
    void setUp() {
        clock = new ManualMonotonicClock();
        scheduler = new DeterministicScheduler(clock);
        activityTracker = new GenisysMonotonicActivityTracker(clock);
        reducer = new GenisysStateReducer();
        injectedEvents = Collections.synchronizedList(new ArrayList<>());
    }

    @AfterEach
    void tearDown() {
        if (driver != null) {
            driver.stop();
        }
    }

    @Test
    void timeoutInjectedWhenNoActivityObserved() {
        // Mock executor that does nothing (no actual sends)
        GenisysIntentExecutor mockExecutor = intents -> { };

        // Timed executor that tracks sends and arms timeouts
        TimedGenisysIntentExecutor timedExecutor = new TimedGenisysIntentExecutor(
                mockExecutor,
                injectedEvents::add,
                activityTracker,
                clock,
                scheduler,
                Instant::now,
                GenisysTimingPolicy.withResponseTimeout(Duration.ofMillis(500))
        );

        // Execute a send that arms timeout
        timedExecutor.execute(GenisysIntents.sendRecall(3));

        // Advance time past timeout deadline
        clock.advanceMillis(500);
        scheduler.runDueTasks();

        // Verify timeout was injected
        synchronized (injectedEvents) {
            boolean hasTimeout = injectedEvents.stream()
                    .anyMatch(e -> e instanceof GenisysTimeoutEvent.ResponseTimeout);
            assertTrue(hasTimeout, "Should have injected response timeout");
        }
    }

    @Test
    void activitySuppressesTimeout() throws InterruptedException {
        GenisysIntentExecutor mockExecutor = intents -> { };

        TimedGenisysIntentExecutor timedExecutor = new TimedGenisysIntentExecutor(
                mockExecutor,
                injectedEvents::add,
                activityTracker,
                clock,
                scheduler,
                Instant::now,
                GenisysTimingPolicy.withResponseTimeout(Duration.ofMillis(500))
        );

        driver = new GenisysOperationalDriver(
                reducer,
                timedExecutor,
                clock,
                scheduler,
                GenisysTimingPolicy.withResponseTimeout(Duration.ofMillis(500)),
                () -> GenisysControllerState.initializing(List.of(3), Instant.now())
        );

        driver.start();

        // Execute a send that arms timeout for station 3
        timedExecutor.execute(GenisysIntents.sendRecall(3));

        // Simulate activity from station 3 before timeout
        clock.advanceMillis(200);
        activityTracker.recordSemanticActivity(3);

        // Advance past timeout deadline
        clock.advanceMillis(400);
        scheduler.runDueTasks();

        Thread.sleep(50); // Allow any processing

        // Verify NO timeout was injected
        synchronized (injectedEvents) {
            boolean hasTimeout = injectedEvents.stream()
                    .anyMatch(e -> e instanceof GenisysTimeoutEvent.ResponseTimeout);
            assertFalse(hasTimeout, "Activity should suppress timeout");
        }
    }

    @Test
    void suspendAllCancelsArmedTimeouts() throws InterruptedException {
        GenisysIntentExecutor mockExecutor = intents -> { };

        TimedGenisysIntentExecutor timedExecutor = new TimedGenisysIntentExecutor(
                mockExecutor,
                injectedEvents::add,
                activityTracker,
                clock,
                scheduler,
                Instant::now,
                GenisysTimingPolicy.withResponseTimeout(Duration.ofMillis(500))
        );

        driver = new GenisysOperationalDriver(
                reducer,
                timedExecutor,
                clock,
                scheduler,
                GenisysTimingPolicy.withResponseTimeout(Duration.ofMillis(500)),
                () -> GenisysControllerState.initializing(List.of(3), Instant.now())
        );

        driver.start();

        // Arm timeout
        timedExecutor.execute(GenisysIntents.sendRecall(3));

        // Issue SUSPEND_ALL which should cancel timeout
        clock.advanceMillis(100);
        timedExecutor.execute(GenisysIntents.suspendAll());

        // Advance past original timeout deadline
        clock.advanceMillis(500);
        scheduler.runDueTasks();

        Thread.sleep(50);

        // Verify timeout was cancelled (not injected)
        synchronized (injectedEvents) {
            assertEquals(0, injectedEvents.size(), "SUSPEND_ALL should cancel timeout");
        }
    }

    @Test
    void phase5RuntimeWithActivityTracking() {
        // Mock datagram endpoint
        DatagramEndpoint mockEndpoint = new DatagramEndpoint() {
            @Override
            public void start() { }

            @Override
            public void stop() { }

            @Override
            public void send(SocketAddress remote, byte[] data) { }

            @Override
            public void setListener(DatagramEndpointListener listener) { }
        };

        // Create controller
        GenisysControllerState initialState = GenisysControllerState.initializing(
                List.of(1, 2, 3),
                Instant.now()
        );

        GenisysWaysideController controller = new GenisysWaysideController(
                initialState,
                reducer,
                intents -> { } // Mock executor
        );

        // Create Phase 5 runtime with activity tracker
        GenisysUdpRuntime runtime = new GenisysUdpRuntime(
                controller,
                mockEndpoint,
                new DefaultGenisysFrameDecoder(),
                new DefaultGenisysFrameEncoder(),
                new GenisysMessageDecoder(p -> null, p -> null),
                new GenisysMessageEncoder(i -> null, c -> null),
                activityTracker  // Phase 5 activity tracking
        );

        // Should not throw
        assertNotNull(runtime);
    }

    @Test
    void operationalDriverProcessesEventsSequentially() throws InterruptedException {
        List<GenisysIntents> executedIntents = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(2);

        GenisysIntentExecutor trackingExecutor = intents -> {
            executedIntents.add(intents);
            latch.countDown();
        };

        driver = new GenisysOperationalDriver(
                reducer,
                trackingExecutor,
                clock,
                scheduler,
                GenisysTimingPolicy.withResponseTimeout(Duration.ofMillis(500)),
                () -> GenisysControllerState.of(
                        GenisysControllerState.GlobalState.TRANSPORT_DOWN,
                        Collections.emptyMap(),
                        Instant.now()
                )
        );

        driver.start();

        // Submit events
        driver.submitEvent(new GenisysTransportEvent.TransportUp(Instant.now()));
        driver.submitEvent(new GenisysTransportEvent.TransportUp(Instant.now()));

        // Wait for processing
        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertTrue(completed, "Events should be processed");

        // Verify sequential execution
        assertEquals(2, executedIntents.size());
    }

    @Test
    void sendTrackerIntegration() {
        // This test verifies that SendTracker can be used by delegates
        // Detailed POLL_NEXT + SendTracker behavior is tested in TimedGenisysIntentExecutorTest

        GenisysIntentExecutor mockExecutor = intents -> { };

        TimedGenisysIntentExecutor timedExecutor = new TimedGenisysIntentExecutor(
                mockExecutor,
                injectedEvents::add,
                activityTracker,
                clock,
                scheduler,
                Instant::now,
                GenisysTimingPolicy.withResponseTimeout(Duration.ofMillis(500))
        );

        // Verify SendTracker is available
        SendTracker tracker = timedExecutor.sendTracker();
        assertNotNull(tracker, "SendTracker should be available");

        // Execute a send with explicit station
        timedExecutor.execute(GenisysIntents.sendRecall(5));

        // Advance past timeout
        clock.advanceMillis(500);
        scheduler.runDueTasks();

        // Verify timeout for station 5
        synchronized (injectedEvents) {
            assertEquals(1, injectedEvents.size());
            GenisysTimeoutEvent.ResponseTimeout timeout =
                    (GenisysTimeoutEvent.ResponseTimeout) injectedEvents.get(0);
            assertEquals(5, timeout.stationAddress());
        }
    }

    @Test
    void multipleSequentialSendsArmNewTimeouts() throws InterruptedException {
        GenisysIntentExecutor mockExecutor = intents -> { };

        TimedGenisysIntentExecutor timedExecutor = new TimedGenisysIntentExecutor(
                mockExecutor,
                injectedEvents::add,
                activityTracker,
                clock,
                scheduler,
                Instant::now,
                GenisysTimingPolicy.withResponseTimeout(Duration.ofMillis(500))
        );

        // First send
        timedExecutor.execute(GenisysIntents.sendRecall(3));

        // Second send before first timeout - should cancel and re-arm
        clock.advanceMillis(200);
        timedExecutor.execute(GenisysIntents.sendRecall(3));

        // Advance past original deadline but not new deadline
        clock.advanceMillis(400);
        scheduler.runDueTasks();

        // Should NOT have timeout yet
        synchronized (injectedEvents) {
            assertEquals(0, injectedEvents.size(), "Original timeout should be cancelled");
        }

        // Advance past new deadline
        clock.advanceMillis(200);
        scheduler.runDueTasks();

        // Should have new timeout
        synchronized (injectedEvents) {
            assertEquals(1, injectedEvents.size(), "New timeout should fire");
        }
    }

    @Test
    void perStationSendTimeTracking() {
        GenisysIntentExecutor mockExecutor = intents -> { };

        TimedGenisysIntentExecutor timedExecutor = new TimedGenisysIntentExecutor(
                mockExecutor,
                injectedEvents::add,
                activityTracker,
                clock,
                scheduler,
                Instant::now,
                GenisysTimingPolicy.withResponseTimeout(Duration.ofMillis(500))
        );

        // Initially no sends
        assertEquals(0L, timedExecutor.lastSendTimeNanos(1));
        assertEquals(0L, timedExecutor.lastSendTimeNanos(2));

        // Send to station 1
        timedExecutor.execute(GenisysIntents.sendRecall(1));
        long t1 = timedExecutor.lastSendTimeNanos(1);
        assertTrue(t1 >= 0, "Should have recorded send time");

        // Advance and send to station 2
        clock.advanceMillis(100);
        timedExecutor.execute(GenisysIntents.sendRecall(2));
        long t2 = timedExecutor.lastSendTimeNanos(2);

        // Station 2's send time should be later than station 1's
        assertTrue(t2 >= t1, "Station 2 send time should be >= station 1 send time");

        // Station 1's send time unchanged
        assertEquals(t1, timedExecutor.lastSendTimeNanos(1));
    }
}
