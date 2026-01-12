# Phase 6: Production Integration - Implementation Plan

## Executive Summary

**Phase 6 Goal:** Transform the validated Phase 1-5 GENISYS protocol implementation into a production-ready runtime system.

**Key Deliverables:**
1. **Observability Infrastructure** - Structured event emission at semantic boundaries with SLF4J integration
2. **Configuration Model** - Programmatic station/timing/runtime configuration
3. **Production Runtime Factory** - Unified composition root with builder pattern
4. **User API Bridge** - Integration with AbstractWaysideController for public API
5. **Lifecycle Orchestration** - Coordinated start/stop/shutdown across all subsystems
6. **Comprehensive Testing** - Integration tests for production composition
7. **Documentation** - Production usage guide, observability guide, troubleshooting

**Architectural Principles Preserved:**
- Reducer purity (no logging in reducers)
- Decode-before-event boundary (transport → semantic events only)
- Monotonic time for correctness, wall-clock for observability only
- Transport neutrality
- Netty containment
- Backward compatibility with Phase 1-5

---

## Current State Analysis

### What Exists (Phase 1-5)
- ✅ GenisysStateReducer - Pure state machine for protocol logic
- ✅ GenisysWaysideController - Phase 3 controller with event queue
- ✅ GenisysOperationalDriver - Phase 5 threaded event loop coordinator
- ✅ TimedGenisysIntentExecutor - Phase 5 timeout arming and activity tracking
- ✅ GenisysUdpRuntime - Phase 4 UDP transport integration
- ✅ Production clocks: SystemMonotonicClock, SystemWallClock
- ✅ Production scheduler: ScheduledExecutorScheduler
- ✅ Test infrastructure: ManualMonotonicClock, DeterministicScheduler, RecordingIntentExecutor

### What's Missing (Phase 6 Scope)
- ❌ Production factory to compose all components
- ❌ Configuration externalization (station addresses, timing policy)
- ❌ Observability hooks (logging, metrics, tracing)
- ❌ Integration with AbstractWaysideController (user-facing API)
- ❌ Unified lifecycle management (coordinated start/stop)
- ❌ Production usage documentation

### Key Files
**Composition Roots:**
- `/src/main/java/com/questrail/wayside/protocol/genisys/runtime/GenisysUdpRuntime.java`
- `/src/main/java/com/questrail/wayside/protocol/genisys/internal/exec/GenisysOperationalDriver.java`

**User-Facing API:**
- `/src/main/java/com/questrail/wayside/core/AbstractWaysideController.java`
- `/src/main/java/com/questrail/wayside/api/ControllerStatus.java`

**Configuration:**
- `/src/main/java/com/questrail/wayside/protocol/genisys/internal/exec/GenisysTimingPolicy.java`

**Production Components:**
- `/src/main/java/com/questrail/wayside/protocol/genisys/time/SystemMonotonicClock.java`
- `/src/main/java/com/questrail/wayside/protocol/genisys/internal/time/SystemWallClock.java`
- `/src/main/java/com/questrail/wayside/protocol/genisys/time/ScheduledExecutorScheduler.java`

---

## Architecture Overview

### Production Runtime Stack

```
┌──────────────────────────────────────────────────────────┐
│  GenisysProductionController                             │
│  extends AbstractWaysideController                       │
│  ────────────────────────────────────────────────────    │
│  - User-facing API (setControls, getIndications)         │
│  - ControllerStatus mapping (state → status)             │
│  - Control/Indication bridge                             │
└────────────────────┬─────────────────────────────────────┘
                     │ owns
┌────────────────────▼─────────────────────────────────────┐
│  GenisysProductionRuntime                                │
│  (Composition Root + Lifecycle Owner)                    │
│  ────────────────────────────────────────────────────    │
│  - Unified start/stop/shutdown                           │
│  - Component wiring and ownership                        │
│  - Observability sink integration                        │
└────────────────────┬─────────────────────────────────────┘
                     │ coordinates
          ┌──────────┴──────────┐
          │                     │
┌─────────▼──────────┐  ┌──────▼───────────────────┐
│ Operational Driver │  │ UDP Runtime              │
│ (Event Loop)       │  │ (Transport Integration)  │
│                    │  │                          │
│ - Event queue      │  │ - Netty endpoint         │
│ - Reducer coord    │  │ - Codec pipeline         │
│ - Intent execution │  │ - Activity tracking      │
│ - Scheduler        │  │ - Frame encode/decode    │
└────────────────────┘  └──────────────────────────┘
```

### Data Flow

**Control Update Flow:**
```
User calls setControls()
  ↓
AbstractWaysideController.setControls() (merges, materializes)
  ↓
GenisysProductionController.onControlsUpdated() (hook)
  ↓
Submit GenisysControlIntentEvent to GenisysOperationalDriver
  ↓
Reducer processes event → emits SEND_CONTROLS intent
  ↓
TimedGenisysIntentExecutor.execute()
  ↓
UdpGenisysIntentExecutor → encode → send UDP
```

**Indication Update Flow:**
```
UDP datagram arrives
  ↓
UdpTransportAdapter.onDatagram()
  ↓
GenisysFrameDecoder → GenisysMessageDecoder
  ↓
GenisysMessageEvent.MessageReceived (semantic)
  ↓
GenisysWaysideController.submit() → drain()
  ↓
Reducer processes → state transition
  ↓
GenisysProductionController.onIndicationReceived() (callback)
  ↓
AbstractWaysideController.applyIndicationUpdate() (merges indications)
```

**Observability Flow:**
```
GenisysOperationalDriver.processEvent()
  ↓
Reducer.apply() → Result(newState, intents)
  ↓
observabilitySink.onStateTransition(event) ← hook point
  ↓
Slf4jGenisysObservabilitySink.onStateTransition()
  ↓
SLF4J logger.info("Station {} phase: {} → {}", ...)
```

---

## Design Decisions

### 1. Composition Root Pattern

**Decision:** Create `GenisysProductionRuntime` as a unified composition root with builder pattern.

**Rationale:**
- Centralizes component lifecycle ownership
- Builder pattern handles complex configuration naturally
- Similar to existing `GenisysTestHarnessFactory` pattern
- Avoids scattered factory methods across packages
- Enables validation at build() time

**API:**
```java
GenisysProductionRuntime runtime = GenisysProductionRuntime.builder()
    .withStations(stationConfig)
    .withTimingPolicy(GenisysTimingPolicy.defaults())
    .withControlSetProvider(controlSetProvider)
    .withObservabilitySink(new Slf4jGenisysObservabilitySink())
    .build();
```

### 2. Observability Sink Abstraction

**Decision:** Interface-based sink (`GenisysObservabilitySink`) with pluggable implementations.

**Rationale:**
- Preserves reducer purity (no logging in reducers)
- Testable (NullObservabilitySink, RecordingObservabilitySink)
- Production-ready (Slf4jGenisysObservabilitySink)
- Extensible for metrics/tracing (future: MicrometerGenisysObservabilitySink)
- Hooks at semantic boundaries only (not frame-level)

**Interface:**
```java
public interface GenisysObservabilitySink {
    void onStateTransition(GenisysStateTransitionEvent event);
    void onProtocolEvent(GenisysProtocolObservabilityEvent event);
    void onTransportEvent(GenisysTransportObservabilityEvent event);
    void onError(GenisysErrorEvent event);
}
```

### 3. Lifecycle Orchestration

**Decision:** Two-phase initialization (construct → start) with coordinated shutdown.

**Rationale:**
- Separates component wiring from activation
- Handles circular dependencies (controller ↔ executor)
- Start sequence: Driver.start() → Runtime.start() → Transport.start()
- Stop sequence: Transport.stop() → Runtime.stop() → Driver.stop() → Scheduler cleanup
- Graceful shutdown with timeout (5s for event loop drain)

**Lifecycle:**
```java
// Construction (all wiring, resolve circular dependencies)
GenisysProductionRuntime runtime = GenisysProductionRuntime.builder()...build();

// Activation (starts event loop, binds transport)
runtime.start();

// Graceful shutdown (reverse order)
runtime.stop();
```

### 4. Integration with AbstractWaysideController

**Decision:** `GenisysProductionController extends AbstractWaysideController`

**Rationale:**
- AbstractWaysideController provides control materialization and indication merging
- GenisysProductionController bridges to GENISYS protocol implementation
- Status mapping: GenisysControllerState.GlobalState → ControllerStatus
- Control updates trigger GENISYS protocol events
- Indication updates sourced from GENISYS message events
- Preserves separation: user API vs protocol engine

**Status Mapping:**
```
TRANSPORT_DOWN → DISCONNECTED
INITIALIZING → DISCONNECTED
RUNNING (all slaves healthy) → CONNECTED
RUNNING (some FAILED) → DEGRADED
RUNNING (all FAILED) → DISCONNECTED
```

### 5. Configuration Model

**Decision:** Programmatic configuration with immutable records (no file parsing).

**Rationale:**
- Aligns with existing `GenisysTimingPolicy` pattern
- Constructor validation ensures correctness
- Builder pattern for complex configuration
- No external dependencies (YAML parsers, etc.)
- Testable and deterministic

**Configuration Classes:**
- `GenisysStationConfig` - Maps station ID → SocketAddress
- `GenisysRuntimeConfig` - Aggregates timing, stations, runtime parameters

---

## Implementation Plan

### Phase 6.1: Observability Infrastructure

**Objective:** Define observability contracts and implement SLF4J integration.

**Tasks:**

1. **Create observability event interfaces** (package: `com.questrail.wayside.protocol.genisys.observability`)
   - `GenisysObservabilitySink` - Main sink interface
   - `GenisysStateTransitionEvent` - State transition metadata (record)
   - `GenisysProtocolObservabilityEvent` - Protocol-level events (marker interface)
   - `GenisysTransportObservabilityEvent` - Transport lifecycle events (marker interface)
   - `GenisysErrorEvent` - Errors and anomalies (record)

2. **Implement sink implementations**
   - `NullObservabilitySink` - No-op singleton for tests/minimal overhead
   - `Slf4jGenisysObservabilitySink` - Production SLF4J integration
   - `RecordingObservabilitySink` - Test sink that captures events for assertions

3. **Add SLF4J dependency to build.gradle.kts**
   ```kotlin
   dependencies {
       implementation("org.slf4j:slf4j-api:2.0.9")
       testImplementation("ch.qos.logback:logback-classic:1.4.11")
   }
   ```

**Files to Create:**
- `src/main/java/com/questrail/wayside/protocol/genisys/observability/GenisysObservabilitySink.java`
- `src/main/java/com/questrail/wayside/protocol/genisys/observability/GenisysStateTransitionEvent.java`
- `src/main/java/com/questrail/wayside/protocol/genisys/observability/GenisysProtocolObservabilityEvent.java`
- `src/main/java/com/questrail/wayside/protocol/genisys/observability/GenisysTransportObservabilityEvent.java`
- `src/main/java/com/questrail/wayside/protocol/genisys/observability/GenisysErrorEvent.java`
- `src/main/java/com/questrail/wayside/protocol/genisys/observability/NullObservabilitySink.java`
- `src/main/java/com/questrail/wayside/protocol/genisys/observability/Slf4jGenisysObservabilitySink.java`
- `src/test/java/com/questrail/wayside/protocol/genisys/observability/RecordingObservabilitySink.java`

**Key Implementation Details:**

`GenisysStateTransitionEvent`:
```java
public record GenisysStateTransitionEvent(
    Instant timestamp,
    GenisysControllerState oldState,
    GenisysControllerState newState,
    GenisysEvent triggeringEvent,
    GenisysIntents resultingIntents
) {
    public boolean isGlobalStateChange() {
        return oldState.globalState() != newState.globalState();
    }

    public Set<Integer> affectedStations() {
        // Return stations with changed state
    }
}
```

`Slf4jGenisysObservabilitySink`:
```java
public final class Slf4jGenisysObservabilitySink implements GenisysObservabilitySink {
    private static final Logger log = LoggerFactory.getLogger(Slf4jGenisysObservabilitySink.class);

    @Override
    public void onStateTransition(GenisysStateTransitionEvent event) {
        if (event.isGlobalStateChange()) {
            log.info("Global state: {} → {}",
                event.oldState().globalState(),
                event.newState().globalState());
        }
        // Per-station phase changes
        for (Integer station : event.affectedStations()) {
            logStationChange(station, event);
        }
    }

    @Override
    public void onError(GenisysErrorEvent event) {
        log.error("GENISYS error: {}", event.message(), event.cause());
    }
}
```

**Architectural Constraints:**
- NO logging calls in GenisysStateReducer (preserve reducer purity)
- NO logging calls in GenisysIntentExecutor implementations
- Sink called only from GenisysOperationalDriver and GenisysProductionRuntime
- Events contain `Instant` timestamps (wall-clock for human readability)
- NO frame-level or byte-level logging (semantic boundaries only)

---

### Phase 6.2: Configuration Model

**Objective:** Define production configuration structures.

**Tasks:**

1. **Create `GenisysStationConfig`** (package: `com.questrail.wayside.protocol.genisys.config`)
   - Maps station ID (0-255) to SocketAddress
   - Builder pattern for programmatic construction
   - Validation: station range, non-empty, unique addresses
   - Methods: `resolve(int station)`, `allStations()`

2. **Create `GenisysRuntimeConfig`** (package: `com.questrail.wayside.protocol.genisys.config`)
   - Aggregates: GenisysTimingPolicy, GenisysStationConfig, securePolls flag
   - Builder pattern
   - Immutable record
   - Factory method: `GenisysRuntimeConfig.defaults()`

3. **Review `GenisysTimingPolicy`** (already exists)
   - Confirm current defaults are production-appropriate:
     - Response timeout: 500ms
     - Poll min gap: 10ms
     - Recall retry delay: 250ms
     - Control coalesce window: 50ms

**Files to Create:**
- `src/main/java/com/questrail/wayside/protocol/genisys/config/GenisysStationConfig.java`
- `src/main/java/com/questrail/wayside/protocol/genisys/config/GenisysRuntimeConfig.java`

**Key Implementation Details:**

`GenisysStationConfig`:
```java
public final class GenisysStationConfig {
    private final Map<Integer, SocketAddress> stations;

    public SocketAddress resolve(int station) {
        SocketAddress addr = stations.get(station);
        if (addr == null) {
            throw new IllegalArgumentException("Unknown station: " + station);
        }
        return addr;
    }

    public Set<Integer> allStations() {
        return stations.keySet();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        public Builder addStation(int station, SocketAddress address) {
            if (station < 0 || station > 255) {
                throw new IllegalArgumentException("Station must be 0-255");
            }
            // ...
        }

        public GenisysStationConfig build() {
            if (stations.isEmpty()) {
                throw new IllegalStateException("At least one station required");
            }
            return new GenisysStationConfig(stations);
        }
    }
}
```

**Configuration Example:**
```java
GenisysStationConfig stations = GenisysStationConfig.builder()
    .addStation(1, new InetSocketAddress("10.0.1.10", 5000))
    .addStation(2, new InetSocketAddress("10.0.1.11", 5000))
    .addStation(3, new InetSocketAddress("10.0.1.12", 5000))
    .build();

GenisysRuntimeConfig config = GenisysRuntimeConfig.builder()
    .withStations(stations)
    .withTimingPolicy(GenisysTimingPolicy.defaults())
    .withSecurePolls(true)
    .build();
```

---

### Phase 6.3: Production Runtime Factory

**Objective:** Create unified composition root for all production components.

**Tasks:**

1. **Create `GenisysProductionRuntime`** (package: `com.questrail.wayside.protocol.genisys.runtime`)
   - Composition root that owns all component lifecycle
   - Builder API for configuration
   - Two-phase initialization (construct → start)
   - Owns: GenisysOperationalDriver, GenisysUdpRuntime, ScheduledExecutorService
   - Integrates observability sink

2. **Resolve circular dependencies**
   - Controller needs executor for intent execution
   - Executor needs controller for state queries (`controller::state`)
   - Executor needs runtime for `send()`
   - Solution: Use Supplier for late binding

3. **Implement `GenisysProductionRuntime.Builder`**
   - Required parameters: stations, controlSetProvider
   - Optional parameters: timingPolicy (defaults), observabilitySink (null), securePolls (true)
   - Validation at build() time
   - Returns fully wired GenisysProductionRuntime

**Files to Create:**
- `src/main/java/com/questrail/wayside/protocol/genisys/runtime/GenisysProductionRuntime.java`

**Key Implementation Details:**

`GenisysProductionRuntime`:
```java
public final class GenisysProductionRuntime {
    private final GenisysOperationalDriver driver;
    private final GenisysUdpRuntime udpRuntime;
    private final ScheduledExecutorService schedulerExecutor;
    private final GenisysObservabilitySink observabilitySink;

    // Package-private constructor (called by builder)
    GenisysProductionRuntime(...) { ... }

    public void start() {
        observabilitySink.onTransportEvent(new TransportStarting(...));
        driver.start();
        udpRuntime.start();
    }

    public void stop() {
        observabilitySink.onTransportEvent(new TransportStopping(...));
        udpRuntime.stop();
        driver.stop();
        schedulerExecutor.shutdown();
        try {
            schedulerExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
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
        private InetSocketAddress bindAddress = new InetSocketAddress(0); // ephemeral

        public Builder withStations(GenisysStationConfig stations) { ... }
        public Builder withTimingPolicy(GenisysTimingPolicy policy) { ... }
        public Builder withControlSetProvider(Function<Integer, ControlSet> provider) { ... }
        public Builder withObservabilitySink(GenisysObservabilitySink sink) { ... }
        public Builder withSecurePolls(boolean secure) { ... }
        public Builder withBindAddress(InetSocketAddress address) { ... }

        public GenisysProductionRuntime build() {
            // Validation
            Objects.requireNonNull(stations, "stations");
            Objects.requireNonNull(controlSetProvider, "controlSetProvider");

            // Create components in dependency order
            // (detailed construction sequence below)

            return new GenisysProductionRuntime(driver, udpRuntime, schedulerExec, sink);
        }
    }
}
```

**Component Construction Sequence (in Builder.build()):**

```java
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

// 3. Create controller with placeholder executor
GenisysWaysideController controller = new GenisysWaysideController(
    initialState,
    reducer,
    intents -> {} // Placeholder, will be replaced by driver
);

// 4. Create UDP components
DatagramEndpoint endpoint = new NettyUdpDatagramEndpoint(bindAddress);
GenisysFrameDecoder frameDecoder = new DefaultGenisysFrameDecoder();
GenisysFrameEncoder frameEncoder = new DefaultGenisysFrameEncoder();
GenisysMessageDecoder messageDecoder = new GenisysMessageDecoder(
    payload -> null, // IndicationPayloadDecoder (TODO: implement)
    payload -> null  // ControlPayloadDecoder (TODO: implement)
);
GenisysMessageEncoder messageEncoder = new GenisysMessageEncoder(
    indications -> null, // IndicationPayloadEncoder (TODO: implement)
    controls -> null     // ControlPayloadEncoder (TODO: implement)
);

UdpTransportAdapter adapter = new UdpTransportAdapter(
    controller,
    endpoint,
    frameDecoder,
    frameEncoder,
    messageDecoder,
    messageEncoder,
    activityTracker
);

// 5. Create SendTracker for POLL_NEXT support
SendTracker sendTracker = new SendTracker();

// 6. Create real executor with controller state supplier
UdpGenisysIntentExecutor udpExecutor = new UdpGenisysIntentExecutor(
    adapter,
    controller::state,  // State supplier (late binding)
    stations::resolve,  // Station address resolver
    controlSetProvider,
    securePolls,
    sendTracker
);

// 7. Wrap with timed executor
TimedGenisysIntentExecutor timedExecutor = new TimedGenisysIntentExecutor(
    udpExecutor,
    controller::submit,  // Event sink for timeouts
    activityTracker,
    clock,
    scheduler,
    SystemWallClock.INSTANCE::now,
    timingPolicy,
    sendTracker
);

// 8. Create operational driver (owns event loop, uses timed executor)
GenisysOperationalDriver driver = new GenisysOperationalDriver(
    reducer,
    timedExecutor,
    clock,
    scheduler,
    timingPolicy,
    () -> initialState,
    observabilitySink  // Phase 6 addition
);

// 9. Create UDP runtime
GenisysUdpRuntime udpRuntime = new GenisysUdpRuntime(
    controller,
    endpoint,
    frameDecoder,
    frameEncoder,
    messageDecoder,
    messageEncoder,
    activityTracker
);

return new GenisysProductionRuntime(driver, udpRuntime, schedulerExec, observabilitySink);
```

---

### Phase 6.4: User-Facing API Bridge

**Objective:** Integrate with AbstractWaysideController for user-facing API.

**Tasks:**

1. **Create `GenisysProductionController`** (package: `com.questrail.wayside.protocol.genisys`)
   - Extends `AbstractWaysideController` (from `com.questrail.wayside.core`)
   - Owns `GenisysProductionRuntime`
   - Implements status mapping: GenisysControllerState → ControllerStatus
   - Bridges control updates to GENISYS events
   - Bridges GENISYS indications to AbstractWaysideController

2. **Implement `onControlsUpdated()` hook**
   - Called by AbstractWaysideController when controls change
   - For each station, submit GenisysControlIntentEvent to driver
   - Use current materialized controls

3. **Implement indication callback**
   - Runtime builder accepts indication callback
   - On GenisysMessageEvent.MessageReceived (IndicationData):
     - Extract indications from message
     - Call AbstractWaysideController.applyIndicationUpdate()

4. **Implement status callback**
   - Runtime builder accepts status callback
   - On state transition:
     - Map GenisysControllerState.GlobalState → ControllerStatus
     - Call AbstractWaysideController.setStatus()

**Files to Create:**
- `src/main/java/com/questrail/wayside/protocol/genisys/GenisysProductionController.java`

**Key Implementation Details:**

`GenisysProductionController`:
```java
public final class GenisysProductionController extends AbstractWaysideController {

    private final GenisysProductionRuntime runtime;
    private final GenisysStationConfig stationConfig;

    public GenisysProductionController(
            SignalIndex<ControlId> controlIndex,
            SignalIndex<IndicationId> indicationIndex,
            GenisysStationConfig stationConfig,
            GenisysTimingPolicy timingPolicy,
            GenisysObservabilitySink observabilitySink) {
        super(controlIndex, indicationIndex);

        this.stationConfig = Objects.requireNonNull(stationConfig);

        // Build runtime with callbacks
        this.runtime = GenisysProductionRuntime.builder()
            .withStations(stationConfig)
            .withTimingPolicy(timingPolicy)
            .withControlSetProvider(station -> this.getControls())
            .withObservabilitySink(observabilitySink)
            .withIndicationCallback(this::onIndicationReceived)
            .withStatusCallback(this::onStatusChanged)
            .build();
    }

    public void start() {
        runtime.start();
    }

    public void stop() {
        runtime.stop();
    }

    @Override
    protected void onControlsUpdated(ControlSet appliedDelta, ControlSet currentMaterialized) {
        // For each station, submit control update event
        // (Implementation detail: may need to define GenisysControlIntentEvent)
        for (int station : stationConfig.allStations()) {
            // Submit event to driver to trigger SEND_CONTROLS
            // runtime.submitControlUpdate(station, currentMaterialized);
        }
    }

    private void onIndicationReceived(int station, IndicationSet indications) {
        applyIndicationUpdate(indications);
    }

    private void onStatusChanged(GenisysControllerState state) {
        ControllerStatus status = mapToControllerStatus(state);
        setStatus(status);
    }

    private ControllerStatus mapToControllerStatus(GenisysControllerState state) {
        if (state.globalState() == GlobalState.TRANSPORT_DOWN) {
            return ControllerStatus.DISCONNECTED;
        }
        if (state.globalState() == GlobalState.INITIALIZING) {
            return ControllerStatus.DISCONNECTED;
        }

        // RUNNING: check slave health
        long failedCount = state.slaves().values().stream()
            .filter(s -> s.phase() == Phase.FAILED)
            .count();

        if (failedCount == 0) {
            return ControllerStatus.CONNECTED;
        } else if (failedCount < state.slaves().size()) {
            return ControllerStatus.DEGRADED;
        } else {
            return ControllerStatus.DISCONNECTED; // All failed
        }
    }
}
```

**Status Mapping Logic:**
```
GenisysControllerState.GlobalState → ControllerStatus

TRANSPORT_DOWN → DISCONNECTED
INITIALIZING → DISCONNECTED
RUNNING:
  - All slaves in POLL phase → CONNECTED
  - Some slaves in FAILED phase → DEGRADED
  - All slaves in FAILED phase → DISCONNECTED
```

---

### Phase 6.5: Observability Instrumentation

**Objective:** Wire observability sink into GenisysOperationalDriver.

**Tasks:**

1. **Modify `GenisysOperationalDriver`** to accept optional `GenisysObservabilitySink`
   - Add constructor parameter (nullable, defaults to NullObservabilitySink)
   - Add private final field
   - Call sink.onStateTransition() after each reducer.apply()
   - Replace System.err.println (line ~179) with sink.onError()

2. **Emit state transition events**
   - After reducer.apply() in processEvent()
   - Include: timestamp, oldState, newState, triggeringEvent, resultingIntents

3. **Emit error events**
   - In event loop exception handler
   - Include: timestamp, message, cause

**Files to Modify:**
- `src/main/java/com/questrail/wayside/protocol/genisys/internal/exec/GenisysOperationalDriver.java`

**Key Code Changes:**

```java
public final class GenisysOperationalDriver {

    // Add field
    private final GenisysObservabilitySink observabilitySink;

    // Modify constructor
    public GenisysOperationalDriver(
            GenisysStateReducer reducer,
            GenisysIntentExecutor executor,
            MonotonicClock clock,
            MonotonicScheduler scheduler,
            GenisysTimingPolicy timingPolicy,
            Supplier<GenisysControllerState> initialStateSupplier,
            GenisysObservabilitySink observabilitySink) {  // NEW PARAMETER

        // ... existing initialization ...

        this.observabilitySink = Objects.requireNonNullElse(
            observabilitySink,
            NullObservabilitySink.INSTANCE
        );
    }

    // Backward compatibility: overload without observability
    public GenisysOperationalDriver(
            GenisysStateReducer reducer,
            GenisysIntentExecutor executor,
            MonotonicClock clock,
            MonotonicScheduler scheduler,
            GenisysTimingPolicy timingPolicy,
            Supplier<GenisysControllerState> initialStateSupplier) {
        this(reducer, executor, clock, scheduler, timingPolicy, initialStateSupplier,
             NullObservabilitySink.INSTANCE);
    }

    // Modify processEvent
    private void processEvent(GenisysEvent event) {
        GenisysControllerState oldState;
        GenisysStateReducer.Result result;

        synchronized (stateLock) {
            oldState = currentState;
            result = reducer.apply(currentState, event);
            currentState = result.newState();
        }

        // Observability hook (AFTER state update, OUTSIDE lock)
        observabilitySink.onStateTransition(new GenisysStateTransitionEvent(
            SystemWallClock.INSTANCE.now(),  // Wall-clock for observability
            oldState,
            result.newState(),
            event,
            result.intents()
        ));

        // Execute intents if non-empty
        if (!result.intents().isEmpty()) {
            executeIntents(result.intents());
        }
    }

    // Modify runEventLoop exception handler
    private void runEventLoop() {
        while (running.get()) {
            try {
                GenisysEvent event = eventQueue.take();
                if (running.get()) {
                    processEvent(event);
                }
            } catch (InterruptedException e) {
                if (running.get()) {
                    Thread.currentThread().interrupt();
                }
            } catch (Exception e) {
                // REPLACE System.err.println with observability
                observabilitySink.onError(new GenisysErrorEvent(
                    SystemWallClock.INSTANCE.now(),
                    "Event processing error",
                    e
                ));
            }
        }
    }
}
```

**Backward Compatibility:**
- Existing tests that don't pass observabilitySink will use NullObservabilitySink
- All Phase 1-5 tests continue to pass unchanged

---

### Phase 6.6: Integration Testing

**Objective:** Verify production composition and backward compatibility.

**Tasks:**

1. **Create `Phase6IntegrationTest`**
   - Test production runtime construction via builder
   - Test start/stop lifecycle
   - Test observability event emission
   - Test configuration validation

2. **Create `GenisysProductionControllerTest`**
   - Test control update propagation (setControls → onControlsUpdated → runtime)
   - Test indication update propagation (runtime → onIndicationReceived → applyIndicationUpdate)
   - Test status mapping (all state transitions → DISCONNECTED/CONNECTED/DEGRADED)

3. **Create `GenisysProductionRuntimeSmokeTest`**
   - Full stack with real Netty endpoint (localhost loopback)
   - Multi-station configuration
   - Verify transport lifecycle
   - Verify graceful shutdown

4. **Backward compatibility verification**
   - Run full test suite: `./gradlew test`
   - Verify all Phase 1-5 tests still pass (162 tests)
   - Verify no regressions

**Files to Create:**
- `src/test/java/com/questrail/wayside/protocol/genisys/test/Phase6IntegrationTest.java`
- `src/test/java/com/questrail/wayside/protocol/genisys/test/GenisysProductionControllerTest.java`
- `src/test/java/com/questrail/wayside/protocol/genisys/test/GenisysProductionRuntimeSmokeTest.java`

**Key Test Cases:**

`Phase6IntegrationTest`:
```java
@Test
void productionRuntimeBuildsAndStarts() {
    GenisysStationConfig stations = GenisysStationConfig.builder()
        .addStation(1, new InetSocketAddress("localhost", 5001))
        .build();

    RecordingObservabilitySink sink = new RecordingObservabilitySink();

    GenisysProductionRuntime runtime = GenisysProductionRuntime.builder()
        .withStations(stations)
        .withControlSetProvider(station -> ControlBitSetSignalSet.empty())
        .withObservabilitySink(sink)
        .build();

    runtime.start();
    Thread.sleep(100); // Allow initialization
    runtime.stop();

    // Verify observability events
    assertTrue(sink.hasEventOfType(GenisysTransportObservabilityEvent.class));
}

@Test
void observabilityEventsEmittedOnStateTransition() {
    RecordingObservabilitySink sink = new RecordingObservabilitySink();
    // ... build runtime with sink ...

    runtime.start();
    runtime.submitEvent(new GenisysTransportEvent.TransportUp(Instant.now()));
    Thread.sleep(50);

    List<GenisysStateTransitionEvent> transitions = sink.getStateTransitions();
    assertFalse(transitions.isEmpty());
    assertTrue(transitions.stream().anyMatch(e ->
        e.newState().globalState() == GlobalState.INITIALIZING));
}
```

`GenisysProductionControllerTest`:
```java
@Test
void controlUpdatePropagates() {
    // Create controller with test configuration
    SignalIndex<ControlId> controlIndex = createTestControlIndex();
    SignalIndex<IndicationId> indicationIndex = createTestIndicationIndex();

    GenisysProductionController controller = new GenisysProductionController(
        controlIndex,
        indicationIndex,
        createTestStationConfig(),
        GenisysTimingPolicy.defaults(),
        new RecordingObservabilitySink()
    );

    controller.start();

    // Update controls
    ControlBitSetSignalSet controls = new ControlBitSetSignalSet(controlIndex);
    controls.set(ControlId.of("signal_1"), SignalState.TRUE);
    controller.setControls(controls);

    // Verify control intent submitted
    // (Would need observability event or test hook)

    controller.stop();
}

@Test
void statusMappingCorrect() {
    // Test all status transitions
    GenisysControllerState transportDown = GenisysControllerState.of(
        GlobalState.TRANSPORT_DOWN, Map.of(), Instant.now());
    assertEquals(ControllerStatus.DISCONNECTED, mapToControllerStatus(transportDown));

    GenisysControllerState initializing = GenisysControllerState.initializing(
        List.of(1), Instant.now());
    assertEquals(ControllerStatus.DISCONNECTED, mapToControllerStatus(initializing));

    // ... test RUNNING → CONNECTED, RUNNING (partial failed) → DEGRADED, etc.
}
```

---

### Phase 6.7: Documentation

**Objective:** Document production usage patterns.

**Tasks:**

1. **Create production usage guide** (`docs/Phase6-ProductionUsage.md`)
   - GenisysProductionController construction example
   - Station configuration patterns (localhost, remote, multi-subnet)
   - Timing policy tuning guidelines
   - Observability integration (SLF4J setup)
   - Graceful shutdown (shutdown hooks)
   - Error handling

2. **Create observability guide** (`docs/GenisysObservability.md`)
   - What's observable (semantic boundaries)
   - What's NOT observable (frame-level, reducer internals)
   - SLF4J configuration examples (Logback)
   - Structured logging patterns (MDC context)
   - Custom observability sinks (metrics, tracing)

3. **Create troubleshooting guide** (`docs/GenisysTroubleshooting.md`)
   - Common issues: timeout tuning, station unreachable, status DEGRADED
   - Observability event interpretation
   - Performance profiling
   - Configuration validation errors

4. **Update roadmap** (`docs/GenisysWaysideControllerRoadmap.md`)
   - Mark Phase 6 as ✅ Complete
   - Add Appendix F: Phase 6 Completion Notes

**Files to Create:**
- `docs/Phase6-ProductionUsage.md`
- `docs/GenisysObservability.md`
- `docs/GenisysTroubleshooting.md`

**Files to Modify:**
- `docs/GenisysWaysideControllerRoadmap.md`

**Production Usage Example (for guide):**
```java
// 1. Define signal indexes
SignalIndex<ControlId> controlIndex = SignalIndex.builder(ControlId.class)
    .add(ControlId.of("signal_1"))
    .add(ControlId.of("signal_2"))
    .build();

SignalIndex<IndicationId> indicationIndex = SignalIndex.builder(IndicationId.class)
    .add(IndicationId.of("track_occupancy_1"))
    .add(IndicationId.of("track_occupancy_2"))
    .build();

// 2. Configure stations
GenisysStationConfig stations = GenisysStationConfig.builder()
    .addStation(1, new InetSocketAddress("10.0.1.10", 5000))
    .addStation(2, new InetSocketAddress("10.0.1.11", 5000))
    .build();

// 3. Create controller
GenisysProductionController controller = new GenisysProductionController(
    controlIndex,
    indicationIndex,
    stations,
    GenisysTimingPolicy.defaults(),
    new Slf4jGenisysObservabilitySink()
);

// 4. Start
controller.start();

// 5. Register shutdown hook
Runtime.getRuntime().addShutdownHook(new Thread(controller::stop));

// 6. Use controller
ControlBitSetSignalSet controls = new ControlBitSetSignalSet(controlIndex);
controls.set(ControlId.of("signal_1"), SignalState.TRUE);
controller.setControls(controls);

// 7. Query status
ControllerStatus status = controller.getStatus();
System.out.println("Status: " + status);

// 8. Query indications
Optional<IndicationSet> indications = controller.getIndications();
indications.ifPresent(ind -> {
    SignalState occupancy = ind.get(IndicationId.of("track_occupancy_1"));
    System.out.println("Track occupancy: " + occupancy);
});
```

---

## Critical Implementation Files

### New Files to Create (21 files)

**Observability (8 files):**
1. `GenisysObservabilitySink.java` - Main sink interface
2. `GenisysStateTransitionEvent.java` - State transition event record
3. `GenisysProtocolObservabilityEvent.java` - Protocol event marker interface
4. `GenisysTransportObservabilityEvent.java` - Transport event marker interface
5. `GenisysErrorEvent.java` - Error event record
6. `NullObservabilitySink.java` - No-op implementation
7. `Slf4jGenisysObservabilitySink.java` - SLF4J implementation
8. `RecordingObservabilitySink.java` (test) - Test implementation

**Configuration (2 files):**
9. `GenisysStationConfig.java` - Station address mapping
10. `GenisysRuntimeConfig.java` - Runtime configuration aggregation

**Runtime (2 files):**
11. `GenisysProductionRuntime.java` - Composition root and lifecycle owner
12. `GenisysProductionController.java` - User-facing API bridge

**Tests (3 files):**
13. `Phase6IntegrationTest.java` - Production runtime tests
14. `GenisysProductionControllerTest.java` - Controller bridge tests
15. `GenisysProductionRuntimeSmokeTest.java` - Full-stack smoke tests

**Documentation (6 files):**
16. `Phase6-ProductionUsage.md` - Production usage guide
17. `GenisysObservability.md` - Observability guide
18. `GenisysTroubleshooting.md` - Troubleshooting guide
19. (Update) `GenisysWaysideControllerRoadmap.md` - Roadmap completion
20. (Update) `build.gradle.kts` - Add SLF4J dependency
21. (Update) `GenisysOperationalDriver.java` - Add observability hooks

### Files to Modify (2 files)

1. **`GenisysOperationalDriver.java`**
   - Add observability sink parameter (optional, backward compatible)
   - Call sink.onStateTransition() after reducer.apply()
   - Call sink.onError() in exception handler
   - Add backward-compatible constructor overload

2. **`build.gradle.kts`**
   - Add SLF4J API dependency
   - Add Logback test dependency

---

## Testing Strategy

### Unit Tests (Component-Level)

1. **Observability Sink Implementations**
   - Slf4jGenisysObservabilitySink logs correctly formatted messages
   - NullObservabilitySink is truly no-op (no exceptions, no overhead)
   - RecordingObservabilitySink captures events for assertions

2. **Configuration Classes**
   - GenisysStationConfig: builder validation (station range 0-255, non-empty)
   - GenisysStationConfig: resolve() returns correct address, throws on unknown
   - GenisysRuntimeConfig: builder validation, defaults applied

3. **Status Mapping**
   - GenisysProductionController maps all GlobalState values correctly
   - Edge cases: all failed, partial failed, initializing, transport down

### Integration Tests (Full Stack)

1. **GenisysProductionRuntime Lifecycle**
   - Build → start → stop sequence succeeds
   - Multiple starts are idempotent
   - Stop without start is safe (no-op)
   - Observability events emitted at start/stop

2. **Control Propagation**
   - setControls() triggers onControlsUpdated()
   - onControlsUpdated() submits event to driver
   - Event processed by reducer → SEND_CONTROLS intent
   - Multi-station control updates handled correctly

3. **Indication Propagation**
   - IndicationData message received
   - Callback invoked with indications
   - applyIndicationUpdate() merges indications
   - Cumulative indication state correct

4. **Full Protocol Flow**
   - Transport up → initialization → recall → poll cycle
   - Control update during poll
   - Timeout handling
   - Status transitions: DISCONNECTED → CONNECTED → DEGRADED

### Backward Compatibility Tests

1. **Phase 1-5 Test Suite**
   - All 162 existing tests must pass
   - No regressions in reducer behavior
   - No regressions in controller behavior
   - No regressions in timeout behavior

2. **Optional Observability**
   - GenisysOperationalDriver works without sink (null-safe)
   - Tests can opt-in to RecordingObservabilitySink
   - Default to NullObservabilitySink when not specified

### Smoke Tests (End-to-End)

1. **Localhost Loopback Test**
   - Real Netty endpoint on localhost
   - Multi-station configuration
   - Full protocol cycle (recall → poll)
   - Graceful shutdown

2. **Observability Verification**
   - Slf4jGenisysObservabilitySink with real logger
   - State transitions logged correctly
   - Error events logged correctly
   - No excessive logging (performance)

---

## Migration Path

### From Phase 5 to Phase 6

**For Tests (Backward Compatible):**
- No changes required for existing tests
- GenisysOperationalDriver constructor overload preserves compatibility
- Optional: Add RecordingObservabilitySink for enhanced test assertions

**For Production (New Usage):**
1. Add SLF4J dependency to build.gradle.kts
2. Replace ad-hoc GenisysOperationalDriver creation with GenisysProductionRuntime.builder()
3. Create GenisysStationConfig for station mapping
4. Construct GenisysProductionController instead of direct runtime usage
5. Wire to application via AbstractWaysideController API
6. Configure SLF4J/Logback for logging

**Before (Phase 5 ad-hoc test pattern):**
```java
ManualMonotonicClock clock = new ManualMonotonicClock();
DeterministicScheduler scheduler = new DeterministicScheduler(clock);
GenisysStateReducer reducer = new GenisysStateReducer();
GenisysOperationalDriver driver = new GenisysOperationalDriver(
    reducer, executor, clock, scheduler, timingPolicy, initialStateSupplier);
driver.start();
```

**After (Phase 6 production pattern):**
```java
GenisysStationConfig stations = GenisysStationConfig.builder()
    .addStation(1, new InetSocketAddress("10.0.1.10", 5000))
    .build();

GenisysProductionController controller = new GenisysProductionController(
    controlIndex,
    indicationIndex,
    stations,
    GenisysTimingPolicy.defaults(),
    new Slf4jGenisysObservabilitySink()
);

controller.start();
// ... use controller.setControls(), controller.getIndications(), etc.
controller.stop();
```

---

## Risk Mitigation

### Risk 1: Circular Dependencies in Composition

**Risk:** Controller needs executor, executor needs controller → construction deadlock.

**Mitigation:**
- Use Supplier for late binding (controller::state)
- Two-phase construction: create components → wire references
- Document construction order in GenisysProductionRuntime.Builder
- Test circular dependency resolution in Phase6IntegrationTest

### Risk 2: Observability Performance Overhead

**Risk:** Excessive logging degrades protocol performance.

**Mitigation:**
- NullObservabilitySink for zero-overhead when observability not needed
- Async logging in SLF4J (off-heap buffering, minimal latency)
- Guard expensive string formatting with log level checks
- NO observability in hot paths (reducer, executor)
- Benchmarking test to measure overhead

### Risk 3: Thread Safety in Status Mapping

**Risk:** Status callback invoked concurrently with state queries.

**Mitigation:**
- GenisysControllerState is immutable (thread-safe by design)
- GenisysOperationalDriver.currentState() is synchronized
- Status callback invoked outside of stateLock (avoid deadlock)
- AbstractWaysideController.setStatus() uses volatile (thread-safe)

### Risk 4: Backward Compatibility Break

**Risk:** Phase 6 changes break Phase 1-5 tests.

**Mitigation:**
- GenisysOperationalDriver constructor overload (optional observability)
- Default to NullObservabilitySink when not provided
- Run full test suite before each commit
- CI/CD pipeline enforces 100% test pass rate

### Risk 5: Configuration Validation Gaps

**Risk:** Invalid configuration passed to production runtime → runtime failures.

**Mitigation:**
- GenisysStationConfig validates station range (0-255) at build time
- GenisysRuntimeConfig validates non-null timing policy
- Builder throws IllegalStateException at build() time (fail-fast)
- Production runtime checks required configuration before start()

### Risk 6: SLF4J Not Configured in Production

**Risk:** SLF4J API present but no binding → warnings or dropped logs.

**Mitigation:**
- Documentation explicitly covers SLF4J configuration
- Logback example configuration in docs
- Production usage guide shows complete setup
- NullObservabilitySink available for deployments without logging

---

## Performance Considerations

### Observability Overhead

**Event Volume Estimates:**
- State transitions: ~10-50/sec (low overhead)
- Protocol events: ~100-500/sec (moderate overhead)
- Error events: ~0-10/sec (negligible overhead)

**Mitigation:**
- SLF4J async appenders (off-heap buffering)
- Structured events: short-lived objects (young gen GC)
- NullObservabilitySink for performance-critical deployments

### Lifecycle Start/Stop Latency

**Expected Latencies:**
- Start sequence: Thread spawn + socket bind = ~10-50ms
- Stop sequence: Event loop drain + socket close = ~50-200ms
- Graceful shutdown: EventQueue.take() interrupt + join = ~5s timeout

**Recommendation:**
- Call stop() in JVM shutdown hook for graceful termination
- Budget 5-10 seconds for clean shutdown in deployment scripts

### Memory Footprint

**Additional Memory (vs Phase 5):**
- GenisysProductionRuntime: ~1KB (object overhead)
- Observability events: ~100-500 bytes each (short-lived)
- SLF4J async buffers: ~1-10MB (configurable)

**Total Overhead:** Negligible (<1% for typical deployments)

---

## Architectural Constraints Verification

### Constraint Checklist

1. ✅ **Decode-before-event boundary**
   - GenisysUdpRuntime unchanged (Phase 4 design preserved)
   - UdpTransportAdapter decode path unchanged
   - Observability receives semantic events only (not frames/bytes)

2. ✅ **Reducers see semantic events only**
   - GenisysStateReducer unchanged (no bytes/frames/sockets)
   - NO logging added to reducer
   - Observability hook AFTER reducer.apply(), not inside

3. ✅ **Executors sole source of outbound traffic**
   - UdpGenisysIntentExecutor unchanged
   - TimedGenisysIntentExecutor unchanged
   - Observability hooks after execution, not before

4. ✅ **Netty containment**
   - NettyUdpDatagramEndpoint unchanged
   - GenisysProductionRuntime uses DatagramEndpoint interface (no Netty types)

5. ✅ **Transport-neutral protocol**
   - Reducer/controller have no UDP knowledge
   - GenisysProductionRuntime can be adapted for serial/HDLC

6. ✅ **Monotonic time for correctness, wall-clock for observability only**
   - TimedGenisysIntentExecutor uses SystemMonotonicClock for timeouts
   - Observability events use Instant (SystemWallClock) for timestamps
   - NO wall-clock used in timeout logic

7. ✅ **Reducer purity**
   - NO logging calls added to GenisysStateReducer
   - NO I/O in reducers
   - Observability hooks in GenisysOperationalDriver only

8. ✅ **Observability at semantic boundaries only**
   - State transitions (semantic)
   - Protocol events (recall, poll, timeout - semantic)
   - Transport lifecycle (semantic)
   - NO frame-level or byte-level logging

---

## Verification

### How to Test the Implementation

**Step 1: Unit Tests**
```bash
./gradlew test --tests "*observability*"
./gradlew test --tests "*config*"
./gradlew test --tests "GenisysProductionRuntimeTest"
```

**Step 2: Integration Tests**
```bash
./gradlew test --tests "Phase6IntegrationTest"
./gradlew test --tests "GenisysProductionControllerTest"
```

**Step 3: Backward Compatibility**
```bash
./gradlew test  # All 162+ tests must pass
```

**Step 4: Smoke Test (Full Stack)**
```bash
./gradlew test --tests "GenisysProductionRuntimeSmokeTest"
```

**Step 5: Build Verification**
```bash
./gradlew clean build
# Verify no warnings, 100% test pass rate
```

### Success Criteria

- ✅ All new Phase 6 tests pass (20+ new tests)
- ✅ All Phase 1-5 tests continue to pass (162 existing tests)
- ✅ Build completes without warnings
- ✅ SLF4J integration verified (logs appear in test output)
- ✅ Production runtime builds and starts without errors
- ✅ Graceful shutdown completes within 5 seconds
- ✅ Status mapping correct for all state transitions
- ✅ Control/indication propagation verified

---

## Summary

Phase 6 completes the GENISYS protocol implementation by transforming validated Phase 1-5 components into a production-ready runtime system with:

**Core Deliverables:**
1. ✅ Observability infrastructure (SLF4J integration, structured events)
2. ✅ Configuration model (GenisysStationConfig, GenisysRuntimeConfig)
3. ✅ Production runtime factory (GenisysProductionRuntime with builder)
4. ✅ User API bridge (GenisysProductionController extends AbstractWaysideController)
5. ✅ Lifecycle orchestration (coordinated start/stop/shutdown)
6. ✅ Comprehensive testing (20+ new tests, backward compatibility verified)
7. ✅ Documentation (production usage, observability, troubleshooting)

**Architectural Principles Preserved:**
- Reducer purity (no logging in reducers)
- Decode-before-event boundary
- Monotonic time for correctness, wall-clock for observability
- Transport neutrality
- Netty containment
- Backward compatibility with Phase 1-5

**What's Next (Beyond Phase 6):**
- Metrics integration (Micrometer/Prometheus)
- Health checks (Spring Boot Actuator, Kubernetes probes)
- Dynamic reconfiguration (hot-reload stations/timing)
- Distributed tracing (OpenTelemetry)
- Advanced diagnostics (state history, event replay)

This plan provides a complete roadmap for Phase 6 implementation with concrete class names, detailed code examples, and comprehensive testing strategy.
