package com.questrail.wayside.protocol.genisys.runtime;

import com.questrail.wayside.api.ControlId;
import com.questrail.wayside.api.ControlSet;
import com.questrail.wayside.api.IndicationId;
import com.questrail.wayside.api.IndicationSet;
import com.questrail.wayside.protocol.genisys.codec.GenisysFrameDecoder;
import com.questrail.wayside.protocol.genisys.codec.GenisysFrameEncoder;
import com.questrail.wayside.protocol.genisys.codec.impl.DefaultGenisysFrameDecoder;
import com.questrail.wayside.protocol.genisys.codec.impl.DefaultGenisysFrameEncoder;
import com.questrail.wayside.mapping.SignalIndex;
import com.questrail.wayside.protocol.genisys.GenisysWaysideController;
import com.questrail.wayside.protocol.genisys.config.GenisysRuntimeConfig;
import com.questrail.wayside.protocol.genisys.config.GenisysStationConfig;
import com.questrail.wayside.protocol.genisys.internal.codec.DefaultControlCodec;
import com.questrail.wayside.protocol.genisys.internal.codec.DefaultIndicationCodec;
import com.questrail.wayside.protocol.genisys.internal.decode.GenisysMessageDecoder;
import com.questrail.wayside.protocol.genisys.internal.encode.GenisysMessageEncoder;
import com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent;
import com.questrail.wayside.protocol.genisys.internal.exec.GenisysMonotonicActivityTracker;
import com.questrail.wayside.protocol.genisys.internal.exec.GenisysOperationalDriver;
import com.questrail.wayside.protocol.genisys.internal.exec.GenisysTimingPolicy;
import com.questrail.wayside.protocol.genisys.internal.exec.SendTracker;
import com.questrail.wayside.protocol.genisys.internal.exec.TimedGenisysIntentExecutor;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysControllerState;
import com.questrail.wayside.protocol.genisys.internal.state.GenisysStateReducer;
import com.questrail.wayside.protocol.genisys.internal.time.MonotonicClock;
import com.questrail.wayside.protocol.genisys.internal.time.MonotonicScheduler;
import com.questrail.wayside.protocol.genisys.internal.time.ScheduledExecutorScheduler;
import com.questrail.wayside.protocol.genisys.internal.time.SystemMonotonicClock;
import com.questrail.wayside.protocol.genisys.internal.time.SystemWallClock;
import com.questrail.wayside.protocol.genisys.observability.GenisysErrorEvent;
import com.questrail.wayside.protocol.genisys.observability.GenisysObservabilitySink;
import com.questrail.wayside.protocol.genisys.observability.GenisysProtocolObservabilityEvent;
import com.questrail.wayside.protocol.genisys.observability.GenisysStateTransitionEvent;
import com.questrail.wayside.protocol.genisys.observability.GenisysTransportObservabilityEvent;
import com.questrail.wayside.protocol.genisys.observability.NullObservabilitySink;
import com.questrail.wayside.protocol.genisys.transport.DatagramEndpoint;
import com.questrail.wayside.protocol.genisys.transport.udp.UdpGenisysIntentExecutor;
import com.questrail.wayside.protocol.genisys.transport.udp.UdpTransportAdapter;
import com.questrail.wayside.protocol.genisys.transport.udp.netty.NettyUdpDatagramEndpoint;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * GenisysProductionRuntime
 * =============================================================================
 * Unified composition root and lifecycle owner for the production GENISYS stack.
 */
public final class GenisysProductionRuntime {
    private final GenisysOperationalDriver driver;
    private final GenisysUdpRuntime udpRuntime;
    private final ScheduledExecutorService schedulerExecutor;
    private final GenisysObservabilitySink observabilitySink;

    private GenisysProductionRuntime(
            GenisysOperationalDriver driver,
            GenisysUdpRuntime udpRuntime,
            ScheduledExecutorService schedulerExecutor,
            GenisysObservabilitySink observabilitySink) {
        this.driver = driver;
        this.udpRuntime = udpRuntime;
        this.schedulerExecutor = schedulerExecutor;
        this.observabilitySink = observabilitySink;
    }

    public void start() {
        driver.start();
        udpRuntime.start();
    }

    public void stop() {
        udpRuntime.stop();
        driver.stop();
        schedulerExecutor.shutdown();
        try {
            if (!schedulerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                schedulerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            schedulerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public GenisysControllerState currentState() {
        return driver.currentState();
    }

    public void submitEvent(GenisysEvent event) {
        driver.submitEvent(event);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private GenisysStationConfig stations;
        private GenisysTimingPolicy timingPolicy = GenisysTimingPolicy.defaults();
        private Function<Integer, ControlSet> controlSetProvider;
        private GenisysObservabilitySink observabilitySink = NullObservabilitySink.INSTANCE;
        private boolean securePolls = true;
        private boolean controlCheckbackEnabled = true;
        private InetSocketAddress bindAddress = new InetSocketAddress(0);
        private SignalIndex<ControlId> controlIndex;
        private SignalIndex<IndicationId> indicationIndex;
        private BiConsumer<Integer, IndicationSet> indicationCallback;
        private Consumer<GenisysControllerState> statusCallback;

        public Builder withStations(GenisysStationConfig stations) {
            this.stations = stations;
            return this;
        }

        public Builder withTimingPolicy(GenisysTimingPolicy policy) {
            this.timingPolicy = policy;
            return this;
        }

        public Builder withControlSetProvider(Function<Integer, ControlSet> provider) {
            this.controlSetProvider = provider;
            return this;
        }

        public Builder withObservabilitySink(GenisysObservabilitySink sink) {
            this.observabilitySink = sink;
            return this;
        }

        public Builder withSecurePolls(boolean secure) {
            this.securePolls = secure;
            return this;
        }

        public Builder withControlCheckbackEnabled(boolean enabled) {
            this.controlCheckbackEnabled = enabled;
            return this;
        }

        public Builder withBindAddress(InetSocketAddress address) {
            this.bindAddress = address;
            return this;
        }

        public Builder withControlIndex(SignalIndex<ControlId> index) {
            this.controlIndex = index;
            return this;
        }

        public Builder withIndicationIndex(SignalIndex<IndicationId> index) {
            this.indicationIndex = index;
            return this;
        }

        public Builder withIndicationCallback(BiConsumer<Integer, IndicationSet> callback) {
            this.indicationCallback = callback;
            return this;
        }

        public Builder withStatusCallback(Consumer<GenisysControllerState> callback) {
            this.statusCallback = callback;
            return this;
        }

        public GenisysProductionRuntime build() {
            Objects.requireNonNull(stations, "stations");
            Objects.requireNonNull(controlSetProvider, "controlSetProvider");
            Objects.requireNonNull(controlIndex, "controlIndex");
            Objects.requireNonNull(indicationIndex, "indicationIndex");

            // 1. Create core dependencies
            MonotonicClock clock = SystemMonotonicClock.INSTANCE;
            ScheduledExecutorService schedulerExec = Executors.newScheduledThreadPool(1);
            MonotonicScheduler scheduler = new ScheduledExecutorScheduler(schedulerExec, clock);
            GenisysStateReducer reducer = new GenisysStateReducer();
            GenisysMonotonicActivityTracker activityTracker = new GenisysMonotonicActivityTracker(clock);

            // 2. Create initial state
            List<Integer> stationList = new ArrayList<>(stations.allStations());
            GenisysControllerState initialState = GenisysControllerState.initializing(
                stationList,
                SystemWallClock.INSTANCE.now()
            );

            // 3. Create controller with placeholder executor (circular dependency resolved by driver later)
            GenisysWaysideController controller = new GenisysWaysideController(
                initialState,
                reducer,
                intents -> {} 
            );

            // 4. Create UDP components
            DatagramEndpoint endpoint = new NettyUdpDatagramEndpoint(bindAddress);
            GenisysFrameDecoder frameDecoder = new DefaultGenisysFrameDecoder();
            GenisysFrameEncoder frameEncoder = new DefaultGenisysFrameEncoder();
            
            DefaultIndicationCodec indicationCodec = new DefaultIndicationCodec(indicationIndex);
            DefaultControlCodec controlCodec = new DefaultControlCodec(controlIndex);

            GenisysMessageDecoder messageDecoder = new GenisysMessageDecoder(indicationCodec, controlCodec);
            GenisysMessageEncoder messageEncoder = new GenisysMessageEncoder(indicationCodec, controlCodec);

            // 5. Create UDP runtime (composition root for transport)
            GenisysUdpRuntime udpRuntime = new GenisysUdpRuntime(
                controller,
                endpoint,
                frameDecoder,
                frameEncoder,
                messageDecoder,
                messageEncoder,
                activityTracker
            );

            // Expose the adapter from udpRuntime (we added the transport() method in GenisysUdpRuntime)
            UdpTransportAdapter adapter = udpRuntime.transport();

            // 6. Create SendTracker
            SendTracker sendTracker = new SendTracker();

            // 7. Create real executor with controller state supplier
            UdpGenisysIntentExecutor udpExecutor = new UdpGenisysIntentExecutor(
                adapter,
                controller::state,
                stations::resolve,
                controlSetProvider::apply,
                securePolls,
                sendTracker
            );

            // 8. Wrap with timed executor
            TimedGenisysIntentExecutor timedExecutor = new TimedGenisysIntentExecutor(
                udpExecutor,
                controller::submit,
                activityTracker,
                clock,
                scheduler,
                SystemWallClock.INSTANCE::now,
                timingPolicy
            );

            // Composite sink for status callback + original sink
            GenisysObservabilitySink effectiveSink = observabilitySink;
            if (statusCallback != null) {
                effectiveSink = new GenisysObservabilitySink() {
                    @Override
                    public void onStateTransition(GenisysStateTransitionEvent event) {
                        observabilitySink.onStateTransition(event);
                        statusCallback.accept(event.newState());
                    }

                    @Override public void onProtocolEvent(GenisysProtocolObservabilityEvent event) { observabilitySink.onProtocolEvent(event); }
                    @Override public void onTransportEvent(GenisysTransportObservabilityEvent event) { observabilitySink.onTransportEvent(event); }
                    @Override public void onError(GenisysErrorEvent event) { observabilitySink.onError(event); }
                };
            }

            // 9. Create operational driver (owns event loop)
            GenisysOperationalDriver driver = new GenisysOperationalDriver(
                reducer,
                timedExecutor,
                clock,
                scheduler,
                timingPolicy,
                () -> initialState,
                effectiveSink
            );

            // Circularity resolved: driver owns the state that the controller needs to query
            // and the controller submits events that the driver processes.
            // But we already wired controller::state and controller::submit.
            // One final thing: the controller needs to be able to tell the driver to drain
            // if we were using a non-threaded driver, but here the threaded driver
            // will pick up events from the queue.

            return new GenisysProductionRuntime(driver, udpRuntime, schedulerExec, effectiveSink);
        }
    }
}
