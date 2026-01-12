# GENISYS Observability Guide

Phase 6 introduces a structured observability framework for the GENISYS protocol stack. This framework allows for monitoring state transitions, protocol events, and errors without compromising the purity of the core protocol logic.

## Architectural Principles

1.  **Reducer Purity:** No logging calls are allowed within `GenisysStateReducer`.
2.  **Semantic Boundaries:** Observability events are emitted at semantic boundaries (e.g., state transitions, protocol timeouts) rather than at the byte or frame level.
3.  **Wall-Clock for Diagnostics:** While the protocol uses monotonic time for correctness, observability events include `Instant` timestamps for human-readable diagnostics.

## Key Components

### GenisysObservabilitySink

The primary interface for receiving events. Implementations can forward events to SLF4J, Micrometer, or other monitoring systems.

### Event Types

*   **GenisysStateTransitionEvent:** Emitted after every reducer application. Contains the old state, new state, triggering event, and resulting intents.
*   **GenisysProtocolObservabilityEvent:** Marker interface for protocol-level events like timeouts or retries.
*   **GenisysTransportObservabilityEvent:** Marker interface for transport lifecycle events (e.g., socket binding).
*   **GenisysErrorEvent:** Emitted when anomalies occur in the event loop or executors.

## Production Implementation: SLF4J

`Slf4jGenisysObservabilitySink` provides a standard implementation that logs to SLF4J.

*   **Global State Changes:** Logged at `INFO` level.
*   **Station Phase Changes:** Logged at `INFO` level.
*   **Protocol Events:** Logged at `DEBUG` level.
*   **Errors:** Logged at `ERROR` level with stack traces.

## Custom Sinks

You can implement `GenisysObservabilitySink` to export metrics to Prometheus/InfluxDB or traces to Jaeger/Zipkin.

```java
public class MyMetricsSink implements GenisysObservabilitySink {
    @Override
    public void onStateTransition(GenisysStateTransitionEvent event) {
        if (event.isGlobalStateChange()) {
            counter("genisys.state.changes", "to", event.newState().globalState().name()).increment();
        }
    }
    // ...
}
```
