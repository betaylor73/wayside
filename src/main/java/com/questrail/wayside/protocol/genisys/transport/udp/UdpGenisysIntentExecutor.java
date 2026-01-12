package com.questrail.wayside.protocol.genisys.transport.udp;

import com.questrail.wayside.api.ControlSet;
import com.questrail.wayside.protocol.genisys.internal.exec.GenisysIntentExecutor;
import com.questrail.wayside.protocol.genisys.internal.exec.SendTracker;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysControllerState;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysIntents;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysSlaveState;
import com.questrail.wayside.protocol.genisys.model.AcknowledgeAndPoll;
import com.questrail.wayside.protocol.genisys.model.ControlData;
import com.questrail.wayside.protocol.genisys.model.GenisysMessage;
import com.questrail.wayside.protocol.genisys.model.GenisysStationAddress;
import com.questrail.wayside.protocol.genisys.model.Poll;
import com.questrail.wayside.protocol.genisys.model.Recall;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * UdpGenisysIntentExecutor
 * =============================================================================
 * Phase 4 UDP intent executor.
 *
 * <h2>Authoritative anchoring</h2>
 * This class is anchored to the concrete outbound execution surface present in
 * the codebase:
 *
 * <ul>
 *   <li>{@link GenisysIntentExecutor} (execution boundary)</li>
 *   <li>{@link GenisysIntents} (reducer-emitted intent set)</li>
 * </ul>
 *
 * <h2>Outbound wiring flow (captured here intentionally)</h2>
 *
 * <pre>
 *   GenisysEvent
 *      ↓
 *   GenisysStateReducer
 *      ↓ emits
 *   GenisysIntents
 *      ↓ consumed by
 *   UdpGenisysIntentExecutor   (this class)
 *      ↓ delegates to
 *   UdpTransportAdapter.send(remote, GenisysMessage)
 *      ↓ (fixed encoding pipeline inside adapter/codec)
 *   DatagramEndpoint.send(remote, byte[])
 * </pre>
 *
 * Each arrow crosses exactly one architectural boundary.
 *
 * <h2>Phase 4 constraints</h2>
 * Per the Phase 4 re-anchor acknowledgement and integration sketch:
 * <ul>
 *   <li>This class may perform outbound transmission strictly in response to intents.</li>
 *   <li>This class must not introduce transport knowledge into the controller/reducer.</li>
 *   <li>This class does not implement timers or scheduling (Phase 5+).</li>
 * </ul>
 */
public final class UdpGenisysIntentExecutor implements GenisysIntentExecutor
{
    private final UdpTransportAdapter transport;

    /**
     * Provides the current immutable controller state snapshot.
     *
     * <p>
     * The reducer decides <b>what should happen next</b>; for certain intents
     * (notably {@link GenisysIntents.Kind#POLL_NEXT}) the executor must consult
     * current state to select the correct outbound semantic message type
     * (e.g., {@link Poll} vs {@link AcknowledgeAndPoll}).
     * </p>
     */
    private final Supplier<GenisysControllerState> stateSupplier;

    /**
     * Resolves a GENISYS station address (int) to a UDP remote SocketAddress.
     * This is an integration/configuration concern.
     */
    private final IntFunction<SocketAddress> remoteResolver;

    /**
     * Supplies the current materialized {@link ControlSet} to transmit when
     * {@link GenisysIntents.Kind#SEND_CONTROLS} is requested.
     *
     * <p>
     * Intents do not carry payloads; the executor is responsible for sourcing
     * the control image from the hosting environment.
     * </p>
     */
    private final IntFunction<ControlSet> controlSetProvider;

    /**
     * Whether polling should be performed in secure mode.
     * This is a configuration choice; it does not alter protocol semantics.
     */
    private final boolean securePolls;

    /**
     * Minimal execution bookkeeping used to realize {@link GenisysIntents.Kind#RETRY_CURRENT}
     * without consulting or mutating reducer state.
     *
     * <p>
     * Phase 4 does not implement timers, but the reducer can still emit
     * {@code RETRY_CURRENT}. We realize it as a re-send of the most recent
     * outbound semantic message for that station.
     * </p>
     */
    private final Map<Integer, GenisysMessage> lastSentByStation = new HashMap<>();

    /**
     * Phase 5: optional tracker for communicating which station was sent to.
     * Used by {@link com.questrail.wayside.protocol.genisys.internal.exec.TimedGenisysIntentExecutor}
     * to arm timeouts for POLL_NEXT where station selection is executor-owned.
     */
    private final SendTracker sendTracker;

    /**
     * Phase 4 constructor (no send tracking).
     */
    public UdpGenisysIntentExecutor(UdpTransportAdapter transport,
                                   Supplier<GenisysControllerState> stateSupplier,
                                   IntFunction<SocketAddress> remoteResolver,
                                   IntFunction<ControlSet> controlSetProvider,
                                   boolean securePolls)
    {
        this(transport, stateSupplier, remoteResolver, controlSetProvider, securePolls, null);
    }

    /**
     * Phase 5 constructor with send tracking.
     *
     * @param sendTracker optional tracker for recording which station was sent to (may be null)
     */
    public UdpGenisysIntentExecutor(UdpTransportAdapter transport,
                                   Supplier<GenisysControllerState> stateSupplier,
                                   IntFunction<SocketAddress> remoteResolver,
                                   IntFunction<ControlSet> controlSetProvider,
                                   boolean securePolls,
                                   SendTracker sendTracker)
    {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.stateSupplier = Objects.requireNonNull(stateSupplier, "stateSupplier");
        this.remoteResolver = Objects.requireNonNull(remoteResolver, "remoteResolver");
        this.controlSetProvider = Objects.requireNonNull(controlSetProvider, "controlSetProvider");
        this.securePolls = securePolls;
        this.sendTracker = sendTracker; // may be null
    }

    @Override
    public void execute(GenisysIntents intents)
    {
        Objects.requireNonNull(intents, "intents");

        if (intents.isEmpty()) {
            return;
        }

        // -----------------------------------------------------------------
        // Dominant intents (see GenisysIntentExecutorBehavior.md)
        // -----------------------------------------------------------------

        if (intents.contains(GenisysIntents.Kind.SUSPEND_ALL)) {
            // Phase 4: suppress outbound activity. Timer cancellation is Phase 5+.
            return;
        }

        if (intents.contains(GenisysIntents.Kind.BEGIN_INITIALIZATION)) {
            // Phase 4: no timer/scheduler initialization here. Reducer will emit SEND_RECALL.
            return;
        }

        // -----------------------------------------------------------------
        // Protocol intents
        // -----------------------------------------------------------------

        Integer station = intents.targetStation().orElse(null);

        if (intents.contains(GenisysIntents.Kind.SEND_RECALL)) {
            requireStation(station, "SEND_RECALL");
            sendToStation(station, new Recall(GenisysStationAddress.of(station)));
        }

        if (intents.contains(GenisysIntents.Kind.SEND_CONTROLS)) {
            requireStation(station, "SEND_CONTROLS");
            ControlSet controls = Objects.requireNonNull(
                    controlSetProvider.apply(station),
                    "controlSetProvider returned null for station " + station);

            sendToStation(station, new ControlData(GenisysStationAddress.of(station), controls));
        }

        if (intents.contains(GenisysIntents.Kind.RETRY_CURRENT)) {
            requireStation(station, "RETRY_CURRENT");
            retryLast(station);
        }

        if (intents.contains(GenisysIntents.Kind.POLL_NEXT)) {
            // POLL_NEXT selection is executor-owned: consult controller state.
            pollNextAfter(station);
        }

        // SCHEDULE_CONTROL_DELIVERY is explicitly a scheduler/timer concern.
        // Phase 4 intentionally does not implement timing or scheduling.
    }

    private static void requireStation(Integer station, String kind)
    {
        if (station == null) {
            throw new IllegalStateException(kind + " intent requires a target station.");
        }
    }

    private void retryLast(int station)
    {
        GenisysMessage last = lastSentByStation.get(station);
        if (last == null) {
            // Phase 4 integration policy: if no prior message exists, do nothing.
            // This avoids inventing a phase-based resend policy here.
            return;
        }

        sendToStation(station, last);
    }

    private void pollNextAfter(Integer currentStation)
    {
        GenisysControllerState state = Objects.requireNonNull(stateSupplier.get(), "stateSupplier returned null");

        Integer next = selectNextStation(state, currentStation);
        if (next == null) {
            return;
        }

        GenisysSlaveState slave = state.slaves().get(next);
        if (slave == null) {
            return;
        }

        GenisysStationAddress stationAddr = GenisysStationAddress.of(next);

        // Per reducer comments: ackPending influences WHAT the next outbound message is.
        GenisysMessage msg = slave.acknowledgmentPending()
                ? new AcknowledgeAndPoll(stationAddr)
                : new Poll(stationAddr, securePolls);

        sendToStation(next, msg);
    }

    /**
     * Selects the next station after {@code currentStation} in ascending station order.
     *
     * <p>
     * The ordering rule is an execution concern, not a reducer concern.
     * The reducer provides {@code POLL_NEXT(currentStation)} to let the executor
     * advance in a stable deterministic way.
     * </p>
     */
    private static Integer selectNextStation(GenisysControllerState state, Integer currentStation)
    {
        if (state.slaves().isEmpty()) {
            return null;
        }

        List<Integer> stations = new ArrayList<>(state.slaves().keySet());
        Collections.sort(stations);

        if (currentStation == null) {
            // If reducer did not specify a reference, start from the first station.
            return stations.get(0);
        }

        int idx = stations.indexOf(currentStation);
        if (idx < 0) {
            // Unknown current station; fall back to first station.
            return stations.get(0);
        }

        return stations.get((idx + 1) % stations.size());
    }

    private void sendToStation(int stationValue, GenisysMessage message)
    {
        Objects.requireNonNull(message, "message");

        SocketAddress remote = Objects.requireNonNull(
                remoteResolver.apply(stationValue),
                "remoteResolver returned null for station " + stationValue);

        transport.send(remote, message);

        // Bookkeeping for RETRY_CURRENT.
        lastSentByStation.put(stationValue, message);

        // Phase 5: notify tracker so timed executor can arm timeout.
        if (sendTracker != null) {
            sendTracker.recordSend(stationValue);
        }
    }
}
