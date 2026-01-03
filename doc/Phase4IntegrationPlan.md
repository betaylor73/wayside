# Phase 4 Integration Plan — UDP Transport Adapter (Authoritative)

This document defines the **structural integration plan** for Phase 4 of the GENISYS WaysideController roadmap. It is intentionally **non-implementational**: it specifies *where* new components attach, *what* responsibilities they own, and *which boundaries must not be crossed*.

Phase 4 introduces the **first real transport** (UDP) while preserving the GENISYS protocol as a **closed, validated semantic core**.

---

## 1. Phase 4 Scope and Non-Scope

### In scope

- UDP socket lifecycle management
- Byte-level encoding and decoding
- Translation between real transport artifacts and **semantic events**
- Translation between **executor intents** and outbound datagrams

### Explicitly out of scope

- Reducer logic changes
- Executor semantic changes
- Timing infrastructure (Phase 5)
- Observability sinks (Phase 6)
- Configuration binding (Phase 7)
- Concurrency or threading models beyond a single event loop

---

## 2. Architectural Invariants (Re-stated)

Phase 4 must preserve the following invariants, already locked by earlier phases:

- **Protocol semantics are closed**
- **Decode-before-event is mandatory**
- **Reducers never see bytes, frames, sockets, or timing artifacts**
- **Executors are the sole egress for outbound protocol traffic**
- **Transport failures are integration defects, not protocol defects**

---

## 3. New Phase 4 Package Structure

Phase 4 introduces the following new top-level packages:

```
com.questrail.wayside.protocol.genisys.transport
com.questrail.wayside.protocol.genisys.transport.udp
com.questrail.wayside.protocol.genisys.codec
```

No changes are permitted to:

- `internal.state`
- `internal.exec`
- `internal.decode`

---

## 4. Inbound Data Path (UDP → Semantic Event)

### 4.1 UDP Datagram Endpoint

**Component**: `UdpDatagramEndpoint`

**Responsibilities**:
- Bind and manage a UDP socket
- Receive datagrams (`byte[]`)
- Detect transport availability
- Emit:
  - `TransportUp`
  - `TransportDown`

**Explicit non-responsibilities**:
- No decoding
- No protocol interpretation
- No retries or sequencing

---

### 4.2 Byte-Level Decoder (Codec Layer)

**Component**: `GenisysFrameDecoder`

**Package**: `genisys.codec`

**Responsibilities**:
- Convert raw datagrams (`byte[]`) into `GenisysFrame`
- Detect framing errors
- Reject malformed input

**Outputs**:
- `GenisysFrame` (on success)
- A local decode failure signal (never forwarded to the reducer)

This component is **purely structural** and protocol-agnostic.

---

### 4.3 Frame → Message Decode (Existing)

**Component**: `internal.decode.GenisysMessageDecoder`

**Responsibilities**:
- Interpret a valid `GenisysFrame`
- Produce a `GenisysMessage`

This component remains unchanged and authoritative.

---

### 4.4 Message → Semantic Event (Existing)

**Component**: `GenisysMessageEvent.MessageReceived`

This is the **only inbound event** emitted into the controller for protocol messages.

---

### 4.5 Submission to Controller

All semantic events are submitted exclusively via:

```
GenisysWaysideController.submit(GenisysEvent)
```

---

### 4.6 Inbound Pipeline Summary

```
UDP Datagram (byte[])
    ↓
GenisysFrameDecoder        (codec layer)
    ↓
GenisysMessageDecoder     (protocol decode)
    ↓
MessageReceived event
    ↓
GenisysWaysideController
```

---

## 5. Outbound Data Path (Intent → UDP)

### 5.1 UDP Intent Executor

**Component**: `UdpIntentExecutor`

**Implements**: `GenisysIntentExecutor`

**Responsibilities**:
- Receive protocol intents from the controller
- Translate intents into `GenisysMessage`
- Delegate encoding to the codec layer

---

### 5.2 Message-Level Encoder (Codec Layer)

**Component**: `GenisysFrameEncoder`

**Package**: `genisys.codec`

**Responsibilities**:
- Convert `GenisysMessage` into wire-ready `byte[]`
- Perform framing and checksums

This component is the **mirror image** of `GenisysFrameDecoder`.

---

### 5.3 Datagram Transmission

`UdpDatagramEndpoint.send(byte[])`

Outbound datagrams may only originate from executor-driven intent execution.

---

### 5.4 Outbound Pipeline Summary

```
GenisysIntent
    ↓
UdpIntentExecutor
    ↓
GenisysFrameEncoder       (codec layer)
    ↓
UDP Datagram (byte[])
```

---

## 6. Phase 4 Composition Root

Phase 4 introduces a single wiring component:

**Component**: `GenisysUdpRuntime`

**Responsibilities**:
- Construct and wire:
  - `GenisysWaysideController`
  - `UdpDatagramEndpoint`
  - `UdpIntentExecutor`
  - `GenisysFrameDecoder`
  - `GenisysFrameEncoder`
  - `GenisysMessageDecoder`
- Drive the controller execution loop

**Explicit exclusions**:
- No protocol logic
- No retry logic
- No timers
- No observability

---

## 7. Phase 4 Exit Criteria (Preview)

Phase 4 is complete when:

- GENISYS protocol flows operate correctly over UDP
- No reducer or executor changes were required
- Transport failures are correctly classified as integration defects
- Semantic tests remain valid and unchanged

---

## 8. Phase Boundary Reminder

Phase 4 adds **realism**, not **meaning**.

Any pressure to modify reducer logic during Phase 4 indicates a boundary violation and must be explicitly rejected or escalated.

---

## Appendix A — Netty Integration Strategy (Non-Binding Implementation Plan)

This appendix records an implementation strategy for eventually hosting the Phase 4 UDP transport on **Netty** while avoiding architectural lock-in.

### A.1 Goal

Use Netty’s strengths (event loop, UDP datagram support, backpressure-friendly I/O) while ensuring that:

- GENISYS protocol semantics remain **framework-agnostic**
- Netty types never leak into reducers, the controller, message model, or codec boundaries
- A non-Netty transport implementation remains feasible without refactoring protocol code

### A.2 The Port-and-Adapter boundary

Netty must appear only as a concrete adapter behind a **transport port interface**.

- The transport port speaks only in **standard Java types** and GENISYS-adjacent primitives:
  - `byte[]` datagram payloads
  - `java.net.SocketAddress` (remote endpoint)
  - transport lifecycle signals (up/down)

- The rest of the system remains Netty-free:
  - reducer: semantic events only
  - controller: event queue + reducer + executor hosting only
  - codec: bytes ↔ frames
  - protocol decode: frames → messages

### A.3 Netty placement and package constraints

Netty code must live exclusively in a dedicated implementation package, for example:

```
com.questrail.wayside.protocol.genisys.transport.udp.netty
```

No Netty imports are permitted outside this package.

### A.4 ByteBuf containment rule

All Netty `ByteBuf` instances must be consumed and translated to `byte[]` **inside** the Netty adapter.

- No `ByteBuf` may cross the transport port boundary.
- The codec boundary continues to accept `byte[]` only.

This rule prevents Netty from becoming part of the protocol type system.

### A.5 Event loop / determinism

Netty’s `EventLoop` may be used to host the transport adapter. However:

- The controller remains single-threaded and deterministic.
- Any calls into `GenisysWaysideController` must be serialized.

If/when a generic “runner” abstraction is introduced, it must be defined as a framework-agnostic port and implemented using Netty’s `EventLoop` (or alternatives) without affecting protocol semantics.

### A.6 Implication for future phases

- Phase 5 timers may be implemented on Netty’s scheduler, but only behind a framework-agnostic timer port.
- Observability (Phase 6) must remain semantic and must not depend on Netty instrumentation.

This appendix is intentionally **non-binding**: it documents the preferred Netty strategy while preserving the ability to substitute non-Netty transports in the future.

