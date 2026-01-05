package com.questrail.wayside.protocol.genisys.test.exec;

import com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysTimeoutEvent;
import com.questrail.wayside.protocol.genisys.internal.exec.GenisysIntentExecutor;
import com.questrail.wayside.protocol.genisys.internal.exec.GenisysMonotonicActivityTracker;
import com.questrail.wayside.protocol.genisys.internal.exec.GenisysTimingPolicy;
import com.questrail.wayside.protocol.genisys.internal.exec.TimedGenisysIntentExecutor;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysIntents;
import com.questrail.wayside.protocol.genisys.time.DeterministicScheduler;
import com.questrail.wayside.protocol.genisys.time.ManualMonotonicClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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

        GenisysTimingPolicy policy = new GenisysTimingPolicy(Duration.ofMillis(500));
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
}