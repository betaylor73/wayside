package com.questrail.wayside.protocol.genisys.test.exec;

import com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysTimeoutEvent;
import com.questrail.wayside.protocol.genisys.internal.exec.GenisysIntentExecutor;
import com.questrail.wayside.protocol.genisys.internal.exec.GenisysMonotonicActivityTracker;
import com.questrail.wayside.protocol.genisys.internal.exec.GenisysTimingPolicy;
import com.questrail.wayside.protocol.genisys.internal.exec.SendTracker;
import com.questrail.wayside.protocol.genisys.internal.exec.TimedGenisysIntentExecutor;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysIntents;
import com.questrail.wayside.protocol.genisys.time.DeterministicScheduler;
import com.questrail.wayside.protocol.genisys.time.ManualMonotonicClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TimedGenisysIntentExecutorTest
 * -----------------------------------------------------------------------------
 * Locks down the minimal Phase 5 behavior:
 *
 *   send intent → no semantic activity → ResponseTimeout injected
 *
 * This test is fully deterministic:
 * - No sleeps
 * - Manual monotonic clock
 * - Deterministic scheduler
 */
class TimedGenisysIntentExecutorTest {

    @Test
    void sendRecallWithoutActivityInjectsResponseTimeout() {
        ManualMonotonicClock clock = new ManualMonotonicClock();
        DeterministicScheduler scheduler = new DeterministicScheduler(clock);

        // Delegate does nothing; we are only testing timeout scheduling + injection.
        GenisysIntentExecutor delegate = intents -> { };

        List<GenisysEvent> injected = new ArrayList<>();

        GenisysTimingPolicy policy = GenisysTimingPolicy.withResponseTimeout(Duration.ofMillis(500));
        GenisysMonotonicActivityTracker activity = new GenisysMonotonicActivityTracker(clock);

        TimedGenisysIntentExecutor timed = new TimedGenisysIntentExecutor(
                delegate,
                injected::add,
                activity,
                clock,
                scheduler,
                Instant::now, // observational only
                policy
        );

        // Arm timeout by executing a station-targeted send intent.
        timed.execute(GenisysIntents.sendRecall(3));

        // Advance time to the deadline and run due tasks.
        clock.advanceMillis(500);
        scheduler.runDueTasks();

        assertEquals(1, injected.size());
        GenisysTimeoutEvent.ResponseTimeout timeout =
                assertInstanceOf(GenisysTimeoutEvent.ResponseTimeout.class, injected.get(0));
        assertEquals(3, timeout.stationAddress());
    }

    @Test
    void semanticActivitySuppressesTimeout() {
        ManualMonotonicClock clock = new ManualMonotonicClock();
        DeterministicScheduler scheduler = new DeterministicScheduler(clock);
        GenisysIntentExecutor delegate = intents -> { };
        List<GenisysEvent> injected = new ArrayList<>();

        GenisysTimingPolicy policy = GenisysTimingPolicy.withResponseTimeout(Duration.ofMillis(500));
        GenisysMonotonicActivityTracker activity = new GenisysMonotonicActivityTracker(clock);

        TimedGenisysIntentExecutor timed = new TimedGenisysIntentExecutor(
                delegate,
                injected::add,
                activity,
                clock,
                scheduler,
                Instant::now,
                policy
        );

        // Arm timeout.
        timed.execute(GenisysIntents.sendRecall(3));

        // Simulate activity from station 3 before timeout fires.
        clock.advanceMillis(200);
        activity.recordSemanticActivity(3);

        // Advance past deadline.
        clock.advanceMillis(400);
        scheduler.runDueTasks();

        // Activity should suppress the timeout.
        assertEquals(0, injected.size());
    }

    @Test
    void newSendCancelsArmedTimeout() {
        ManualMonotonicClock clock = new ManualMonotonicClock();
        DeterministicScheduler scheduler = new DeterministicScheduler(clock);
        GenisysIntentExecutor delegate = intents -> { };
        List<GenisysEvent> injected = new ArrayList<>();

        GenisysTimingPolicy policy = GenisysTimingPolicy.withResponseTimeout(Duration.ofMillis(500));
        GenisysMonotonicActivityTracker activity = new GenisysMonotonicActivityTracker(clock);

        TimedGenisysIntentExecutor timed = new TimedGenisysIntentExecutor(
                delegate,
                injected::add,
                activity,
                clock,
                scheduler,
                Instant::now,
                policy
        );

        // First send arms timeout.
        timed.execute(GenisysIntents.sendRecall(3));

        // Second send before timeout should cancel and re-arm.
        clock.advanceMillis(200);
        timed.execute(GenisysIntents.sendRecall(3));

        // Advance past original deadline but not past new deadline.
        clock.advanceMillis(400);
        scheduler.runDueTasks();

        // Original timeout should be stale.
        assertEquals(0, injected.size());

        // Advance past new deadline.
        clock.advanceMillis(200);
        scheduler.runDueTasks();

        // New timeout should fire.
        assertEquals(1, injected.size());
    }

    @Test
    void suspendAllCancelsArmedTimeout() {
        ManualMonotonicClock clock = new ManualMonotonicClock();
        DeterministicScheduler scheduler = new DeterministicScheduler(clock);
        GenisysIntentExecutor delegate = intents -> { };
        List<GenisysEvent> injected = new ArrayList<>();

        GenisysTimingPolicy policy = GenisysTimingPolicy.withResponseTimeout(Duration.ofMillis(500));
        GenisysMonotonicActivityTracker activity = new GenisysMonotonicActivityTracker(clock);

        TimedGenisysIntentExecutor timed = new TimedGenisysIntentExecutor(
                delegate,
                injected::add,
                activity,
                clock,
                scheduler,
                Instant::now,
                policy
        );

        // Arm timeout.
        timed.execute(GenisysIntents.sendRecall(3));

        // SUSPEND_ALL should cancel.
        clock.advanceMillis(100);
        timed.execute(GenisysIntents.suspendAll());

        // Advance past deadline.
        clock.advanceMillis(500);
        scheduler.runDueTasks();

        // Timeout should be cancelled.
        assertEquals(0, injected.size());
    }

    @Test
    void beginInitializationCancelsArmedTimeout() {
        ManualMonotonicClock clock = new ManualMonotonicClock();
        DeterministicScheduler scheduler = new DeterministicScheduler(clock);
        GenisysIntentExecutor delegate = intents -> { };
        List<GenisysEvent> injected = new ArrayList<>();

        GenisysTimingPolicy policy = GenisysTimingPolicy.withResponseTimeout(Duration.ofMillis(500));
        GenisysMonotonicActivityTracker activity = new GenisysMonotonicActivityTracker(clock);

        TimedGenisysIntentExecutor timed = new TimedGenisysIntentExecutor(
                delegate,
                injected::add,
                activity,
                clock,
                scheduler,
                Instant::now,
                policy
        );

        // Arm timeout.
        timed.execute(GenisysIntents.sendRecall(3));

        // BEGIN_INITIALIZATION should cancel.
        clock.advanceMillis(100);
        timed.execute(GenisysIntents.beginInitialization());

        // Advance past deadline.
        clock.advanceMillis(500);
        scheduler.runDueTasks();

        // Timeout should be cancelled.
        assertEquals(0, injected.size());
    }

    @Test
    void pollNextArmsTimeoutForStationReportedBySendTracker() {
        ManualMonotonicClock clock = new ManualMonotonicClock();
        DeterministicScheduler scheduler = new DeterministicScheduler(clock);
        List<GenisysEvent> injected = new ArrayList<>();

        GenisysTimingPolicy policy = GenisysTimingPolicy.withResponseTimeout(Duration.ofMillis(500));
        GenisysMonotonicActivityTracker activity = new GenisysMonotonicActivityTracker(clock);

        // Create timed executor first to get its SendTracker.
        TimedGenisysIntentExecutor timed = new TimedGenisysIntentExecutor(
                intents -> { }, // placeholder
                injected::add,
                activity,
                clock,
                scheduler,
                Instant::now,
                policy
        );

        SendTracker tracker = timed.sendTracker();

        // Replace with a delegate that records which station it "sent" to via the tracker.
        GenisysIntentExecutor delegate = intents -> {
            if (intents.contains(GenisysIntents.Kind.POLL_NEXT)) {
                // Simulate selecting station 7.
                tracker.recordSend(7);
            }
        };

        // Recreate with the working delegate (since delegate field is final).
        TimedGenisysIntentExecutor timedWithDelegate = new TimedGenisysIntentExecutor(
                delegate,
                injected::add,
                activity,
                clock,
                scheduler,
                Instant::now,
                policy
        );
        SendTracker realTracker = timedWithDelegate.sendTracker();
        // Wire delegate to use the real tracker.
        GenisysIntentExecutor realDelegate = intents -> {
            if (intents.contains(GenisysIntents.Kind.POLL_NEXT)) {
                realTracker.recordSend(7);
            }
        };

        // Need to construct properly: delegate uses the sendTracker from the executor.
        // This is a bit awkward for testing; let's use a different approach.
        List<GenisysEvent> events = new ArrayList<>();
        ManualMonotonicClock clock2 = new ManualMonotonicClock();
        DeterministicScheduler scheduler2 = new DeterministicScheduler(clock2);
        GenisysMonotonicActivityTracker activity2 = new GenisysMonotonicActivityTracker(clock2);

        // Create a holder for the tracker.
        final SendTracker[] trackerHolder = new SendTracker[1];

        GenisysIntentExecutor simulatingDelegate = intents -> {
            if (intents.contains(GenisysIntents.Kind.POLL_NEXT)) {
                trackerHolder[0].recordSend(7);
            }
        };

        TimedGenisysIntentExecutor executor = new TimedGenisysIntentExecutor(
                simulatingDelegate,
                events::add,
                activity2,
                clock2,
                scheduler2,
                Instant::now,
                policy
        );
        trackerHolder[0] = executor.sendTracker();

        // POLL_NEXT without explicit station; delegate records station 7.
        executor.execute(GenisysIntents.builder().add(GenisysIntents.Kind.POLL_NEXT).build());

        // Advance past timeout.
        clock2.advanceMillis(500);
        scheduler2.runDueTasks();

        // Timeout should fire for station 7.
        assertEquals(1, events.size());
        GenisysTimeoutEvent.ResponseTimeout timeout =
                assertInstanceOf(GenisysTimeoutEvent.ResponseTimeout.class, events.get(0));
        assertEquals(7, timeout.stationAddress());
    }

    @Test
    void sendControlsArmsTimeout() {
        ManualMonotonicClock clock = new ManualMonotonicClock();
        DeterministicScheduler scheduler = new DeterministicScheduler(clock);
        GenisysIntentExecutor delegate = intents -> { };
        List<GenisysEvent> injected = new ArrayList<>();

        GenisysTimingPolicy policy = GenisysTimingPolicy.withResponseTimeout(Duration.ofMillis(500));
        GenisysMonotonicActivityTracker activity = new GenisysMonotonicActivityTracker(clock);

        TimedGenisysIntentExecutor timed = new TimedGenisysIntentExecutor(
                delegate,
                injected::add,
                activity,
                clock,
                scheduler,
                Instant::now,
                policy
        );

        // SEND_CONTROLS should arm timeout.
        timed.execute(GenisysIntents.sendControls(5));

        clock.advanceMillis(500);
        scheduler.runDueTasks();

        assertEquals(1, injected.size());
        GenisysTimeoutEvent.ResponseTimeout timeout =
                assertInstanceOf(GenisysTimeoutEvent.ResponseTimeout.class, injected.get(0));
        assertEquals(5, timeout.stationAddress());
    }

    @Test
    void retryCurrentArmsTimeout() {
        ManualMonotonicClock clock = new ManualMonotonicClock();
        DeterministicScheduler scheduler = new DeterministicScheduler(clock);
        GenisysIntentExecutor delegate = intents -> { };
        List<GenisysEvent> injected = new ArrayList<>();

        GenisysTimingPolicy policy = GenisysTimingPolicy.withResponseTimeout(Duration.ofMillis(500));
        GenisysMonotonicActivityTracker activity = new GenisysMonotonicActivityTracker(clock);

        TimedGenisysIntentExecutor timed = new TimedGenisysIntentExecutor(
                delegate,
                injected::add,
                activity,
                clock,
                scheduler,
                Instant::now,
                policy
        );

        // RETRY_CURRENT should arm timeout.
        timed.execute(GenisysIntents.builder()
                .add(GenisysIntents.Kind.RETRY_CURRENT)
                .targetStation(2)
                .build());

        clock.advanceMillis(500);
        scheduler.runDueTasks();

        assertEquals(1, injected.size());
        GenisysTimeoutEvent.ResponseTimeout timeout =
                assertInstanceOf(GenisysTimeoutEvent.ResponseTimeout.class, injected.get(0));
        assertEquals(2, timeout.stationAddress());
    }

    @Test
    void lastSendTimeNanosTracksPerStation() {
        ManualMonotonicClock clock = new ManualMonotonicClock();
        DeterministicScheduler scheduler = new DeterministicScheduler(clock);
        GenisysIntentExecutor delegate = intents -> { };
        List<GenisysEvent> injected = new ArrayList<>();

        GenisysTimingPolicy policy = GenisysTimingPolicy.withResponseTimeout(Duration.ofMillis(500));
        GenisysMonotonicActivityTracker activity = new GenisysMonotonicActivityTracker(clock);

        TimedGenisysIntentExecutor timed = new TimedGenisysIntentExecutor(
                delegate,
                injected::add,
                activity,
                clock,
                scheduler,
                Instant::now,
                policy
        );

        // Initially no sends.
        assertEquals(0L, timed.lastSendTimeNanos(1));
        assertEquals(0L, timed.lastSendTimeNanos(2));

        // Send to station 1 at time 0.
        timed.execute(GenisysIntents.sendRecall(1));
        long t1 = timed.lastSendTimeNanos(1);
        assertTrue(t1 >= 0);

        // Advance and send to station 2.
        clock.advanceMillis(100);
        timed.execute(GenisysIntents.sendRecall(2));
        long t2 = timed.lastSendTimeNanos(2);

        // Station 2's send time should be later than station 1's.
        assertTrue(t2 > t1);

        // Station 1's send time should be unchanged.
        assertEquals(t1, timed.lastSendTimeNanos(1));
    }

    @Test
    void noTargetStationDoesNotArmTimeout() {
        ManualMonotonicClock clock = new ManualMonotonicClock();
        DeterministicScheduler scheduler = new DeterministicScheduler(clock);

        // Delegate that does NOT record any send (simulates nothing being sent).
        GenisysIntentExecutor delegate = intents -> { };
        List<GenisysEvent> injected = new ArrayList<>();

        GenisysTimingPolicy policy = GenisysTimingPolicy.withResponseTimeout(Duration.ofMillis(500));
        GenisysMonotonicActivityTracker activity = new GenisysMonotonicActivityTracker(clock);

        TimedGenisysIntentExecutor timed = new TimedGenisysIntentExecutor(
                delegate,
                injected::add,
                activity,
                clock,
                scheduler,
                Instant::now,
                policy
        );

        // POLL_NEXT with no explicit station and no send recorded -> no timeout.
        timed.execute(GenisysIntents.builder().add(GenisysIntents.Kind.POLL_NEXT).build());

        clock.advanceMillis(600);
        scheduler.runDueTasks();

        // No timeout should fire.
        assertEquals(0, injected.size());
    }
}