# Phase 6: Production Usage Guide

This guide describes how to use the GENISYS production runtime and controller introduced in Phase 6.

## Overview

Phase 6 provides a unified composition root (`GenisysProductionRuntime`) and a user-facing API bridge (`GenisysProductionController`). These components handle the complexity of wiring Netty transport, the protocol event loop, timing policies, and observability.

## Configuration

### Station Configuration

Use `GenisysStationConfig` to map station IDs (0-255) to their network addresses.

```java
GenisysStationConfig stations = GenisysStationConfig.builder()
    .addStation(1, new InetSocketAddress("10.0.1.10", 5000))
    .addStation(2, new InetSocketAddress("10.0.1.11", 5000))
    .build();
```

### Runtime Configuration

`GenisysRuntimeConfig` aggregates timing and protocol behavior flags.

```java
GenisysRuntimeConfig config = GenisysRuntimeConfig.builder()
    .withStations(stations)
    .withTimingPolicy(GenisysTimingPolicy.defaults())
    .withSecurePolls(true)
    .withControlCheckbackEnabled(true)
    .build();
```

## Creating the Controller

The `GenisysProductionController` is the primary entry point for applications. It extends `AbstractWaysideController`.

```java
GenisysProductionController controller = new GenisysProductionController(
    controlIndex,
    indicationIndex,
    stations,
    GenisysTimingPolicy.defaults(),
    new Slf4jGenisysObservabilitySink()
);

// Start the runtime (activates event loop and transport)
controller.start();

// Use the controller
controller.setControls(myControls);
Optional<IndicationSet> indications = controller.getIndications();
ControllerStatus status = controller.getStatus();

// Stop on shutdown
controller.stop();
```

## Observability

To enable logging, provide an instance of `Slf4jGenisysObservabilitySink`. Ensure you have an SLF4J binding (like Logback) configured in your application.

```java
GenisysObservabilitySink sink = new Slf4jGenisysObservabilitySink();
```

## Lifecycle Management

*   **Construction:** Components are wired but inactive.
*   **Start:** Spawns the event loop thread and binds the UDP socket.
*   **Stop:** Gracefully shuts down the transport, stops the event loop, and shuts down the scheduler. Allow up to 5 seconds for a clean shutdown.
