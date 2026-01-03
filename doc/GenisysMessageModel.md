# GENISYS Message Model

## 1. Purpose and Scope

This document defines the **semantic message model** for the GENISYS protocol.

It describes the canonical representation of GENISYS messages *after* decoding
and *before* protocol behavior is applied.

This document is normative for all reducers, tests, and state machines.

---

## 2. Design Principles

The GENISYS message model is designed to:

1. Separate semantic meaning from wire encoding
2. Enforce protocol directionality at the type level
3. Make illegal message flows unrepresentable
4. Provide clear, testable protocol inputs

---

## 3. Message Directionality

GENISYS messages are strictly directional:

- **Master → Slave**: Requests
- **Slave → Master**: Responses

This distinction is fundamental and is enforced structurally.

---

## 4. Message Taxonomy

All semantic messages implement `GenisysMessage`.

### 4.1 Master Requests

Master requests initiate protocol actions.

| Message | Semantics |
|-------|-----------|
| Poll | Request changed indication data |
| AcknowledgeAndPoll | Acknowledge then poll |
| Recall | Request full indication database |
| ControlData | Deliver control changes |
| ExecuteControls | Execute previously delivered controls |

---

### 4.2 Slave Responses

Slave responses are generated only in reply to valid requests.

| Message | Semantics |
|-------|-----------|
| Acknowledge | No data to return |
| IndicationData | Return indication data |
| ControlCheckback | Echo control data for verification |

---

## 5. Obligations and Invariants

Certain messages create protocol obligations:

### 5.1 IndicationData

- MUST be acknowledged by the master
- Failure to acknowledge causes retransmission

### 5.2 ControlCheckback

- MUST be followed by ExecuteControls to apply controls
- Otherwise control data is discarded

### 5.3 Poll vs Acknowledge-and-Poll

- Poll is illegal when an acknowledgment is pending
- Acknowledge-and-Poll MUST be used instead

---

## 6. Relationship to Wire Encoding

The semantic model intentionally excludes:

- Header byte values
- CRC computation
- Escaping rules
- Framing

Those concerns are handled below the semantic layer.

---

## 7. Decoder Boundary

A dedicated decode layer is responsible for:

- Mapping frames to semantic messages
- Validating message legality
- Rejecting invalid combinations

Reducers must never interpret raw frames directly.

Decoded semantic messages are the unit of protocol behavior.
Reducers must never receive raw frames or interpret header bytes directly.

Semantic messages are the unit of protocol behavior. 
Events carry meaning, not representation.

### Message vs Wire Representation

`GenisysMessage` represents **pure protocol semantics**.

It intentionally contains **no information** about:

- header byte values
- CRC bytes or CRC algorithms
- escaping rules
- frame terminators

Wire-level representation is introduced only at explicit architectural boundaries:

* Message → Frame: `internal.encode.GenisysMessageEncoder`
* Frame → Bytes: `codec.GenisysFrameEncoder`

Reducers, schedulers, and state machines never observe or reason about wire-level
mechanics.


---

## 8. Testing Implications

- Unit tests operate on semantic messages
- Tests describe protocol behavior, not transport artifacts
- Message semantics are stable across transports

---

## 9. Closing Note

The GENISYS message model is the semantic backbone of the protocol implementation.

Maintaining its clarity and correctness is essential to long-term reliability.
